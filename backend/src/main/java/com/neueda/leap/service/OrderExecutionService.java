package com.neueda.leap.service;

import com.neueda.leap.entities.Account;
import com.neueda.leap.entities.CashBalance;
import com.neueda.leap.entities.CashBalanceId;
import com.neueda.leap.entities.Fill;
import com.neueda.leap.entities.FifoAllocation;
import com.neueda.leap.entities.Holding;
import com.neueda.leap.entities.HoldingId;
import com.neueda.leap.entities.Money;
import com.neueda.leap.entities.Order;
import com.neueda.leap.entities.OrderEvent;
import com.neueda.leap.entities.QuoteSnapshot;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.models.MarketQuoteResultDto;
import com.neueda.leap.repository.FifoAllocationRepository;
import com.neueda.leap.repository.FillRepository;
import com.neueda.leap.repository.OrderEventRepository;
import com.neueda.leap.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class OrderExecutionService {

    public enum ExecutionResult {
        FILLED,
        REJECTED,
        EXPIRED,
        RETRY,
        SKIPPED
    }

    private final OrderRepository orders;
    private final OrderEventRepository orderEvents;
    private final OrderEventService events;
    private final FillService fills;
    private final FillRepository fillRepository;
    private final FifoAllocationRepository fifoAllocations;
    private final CashMovementService cashMovements;
    private final OrderExecutionPolicy policy;
    private final TradeAmountCalculator amountCalculator;
    private final EntityManager entityManager;
    private final Clock clock;

    public OrderExecutionService(
            OrderRepository orders,
            OrderEventRepository orderEvents,
            OrderEventService events,
            FillService fills,
            FillRepository fillRepository,
            FifoAllocationRepository fifoAllocations,
            CashMovementService cashMovements,
            OrderExecutionPolicy policy,
            TradeAmountCalculator amountCalculator,
            EntityManager entityManager,
            Clock clock
    ) {
        this.orders = orders;
        this.orderEvents = orderEvents;
        this.events = events;
        this.fills = fills;
        this.fillRepository = fillRepository;
        this.fifoAllocations = fifoAllocations;
        this.cashMovements = cashMovements;
        this.policy = policy;
        this.amountCalculator = amountCalculator;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /**
     * Attempts settlement using a quote fetched before this transaction.
     * A null quote result represents an unavailable quote.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExecutionResult execute(
            Integer orderId,
            MarketQuoteResultDto quoteResult
    ) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException(
                    "Order ID must be positive."
            );
        }

        // Read the immutable account relationship before taking locks.
        Order candidate = orders.findById(orderId).orElse(null);

        if (candidate == null) {
            return ExecutionResult.SKIPPED;
        }

        Integer accountId = candidate.getAccount().getAccountId();

        // All settlement attempts lock account before order.
        Account account = entityManager.find(
                Account.class,
                accountId,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (account == null) {
            throw new NoSuchElementException("Account not found.");
        }

        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() ->
                        new NoSuchElementException("Order not found.")
                );

        OrderEvent latestEvent = orderEvents
                .findFirstByOrder_OrderIdOrderByOrderEventIdDesc(orderId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Order has no lifecycle events."
                        )
                );

        if (latestEvent.getStatus() != OrderStatus.ACCEPTED) {
            return ExecutionResult.SKIPPED;
        }

        CashBalance cash = entityManager.find(
                CashBalance.class,
                new CashBalanceId(
                        accountId,
                        order.getInstrument().getQuoteCurrency()
                ),
                LockModeType.PESSIMISTIC_WRITE
        );

        Holding holding = entityManager.find(
                Holding.class,
                new HoldingId(
                        accountId,
                        order.getInstrument().getInstrumentId()
                ),
                LockModeType.PESSIMISTIC_WRITE
        );

        // Read time after acquiring locks, which may have required waiting.
        Instant now = clock.instant();

        Optional<String> expirationReason = policy.expirationReason(
                order,
                latestEvent.getOccurredAt(),
                now
        );

        if (expirationReason.isPresent()) {
            finishWithoutFill(
                    order,
                    holding,
                    OrderStatus.EXPIRED,
                    expirationReason.get()
            );
            return ExecutionResult.EXPIRED;
        }

        // Do not pass null into the current policy implementation.
        if (quoteResult == null) {
            return ExecutionResult.RETRY;
        }

        Optional<QuoteSnapshot> usableQuote = policy.executionQuote(
                order,
                quoteResult,
                now
        );

        if (usableQuote.isEmpty()) {
            return ExecutionResult.RETRY;
        }

        QuoteSnapshot quote = usableQuote.get();
        Money settlementAmount;

        try {
            settlementAmount = amountCalculator.calculate(
                    order.getRequestedQuantity(),
                    quote.price()
            );
        } catch (IllegalArgumentException rejection) {
            finishWithoutFill(
                    order,
                    holding,
                    OrderStatus.REJECTED,
                    rejection.getMessage()
            );
            return ExecutionResult.REJECTED;
        }

        boolean settled = switch (order.getSide()) {
            case BUY -> settleBuy(
                    order,
                    account,
                    cash,
                    holding,
                    settlementAmount
            );
            case SELL -> settleSell(
                    order,
                    account,
                    cash,
                    holding,
                    settlementAmount
            );
        };

        if (!settled) {
            return ExecutionResult.REJECTED;
        }

        Fill fill = fills.recordFill(
                order,
                quote.price().amount(),
                OffsetDateTime.ofInstant(now, ZoneOffset.UTC)
        );

        if (order.getSide() == OrderSide.SELL) {
            allocateSellFill(fill);
        }

        cashMovements.recordTrade(fill, settlementAmount);

        events.appendEvent(
                orderId,
                OrderStatus.FILLED,
                null,
                quote.price().amount(),
                quote.quoteAt()
        );

        return ExecutionResult.FILLED;
    }

    private void allocateSellFill(Fill sellFill) {
        Order order = sellFill.getOrder();
        // Account is already locked by execute(), serializing allocations with settlement.
        var buyLots = fillRepository.findAvailableBuyLots(
                order.getAccount().getAccountId(), order.getInstrument().getInstrumentId());

        BigDecimal remaining = sellFill.getFillQuantity();
        for (var lot : buyLots) {
            BigDecimal allocated = remaining.min(lot.getAvailableQuantity());
            fifoAllocations.save(new FifoAllocation(sellFill, lot.getBuyFill(), allocated));
            remaining = remaining.subtract(allocated);
            if (remaining.signum() == 0) {
                return;
            }
        }
        throw new IllegalStateException("Insufficient buy fills for FIFO allocation.");
    }

    private boolean settleBuy(
            Order order,
            Account account,
            CashBalance cash,
            Holding holding,
            Money settlementAmount
    ) {
        if (cash == null
                || cash.getTotalBalance().amount()
                .compareTo(settlementAmount.amount()) < 0) {
            finishWithoutFill(
                    order,
                    holding,
                    OrderStatus.REJECTED,
                    "Insufficient cash at the execution price."
            );
            return false;
        }

        boolean newHolding = holding == null;

        if (newHolding) {
            holding = new Holding(account, order.getInstrument());
        }

        // addQuantity validates overflow before changing the holding.
        // Catch only this operation, before any cash has been debited.
        try {
            holding.addQuantity(order.getRequestedQuantity());
        } catch (IllegalArgumentException rejection) {
            finishWithoutFill(
                    order,
                    holding,
                    OrderStatus.REJECTED,
                    "Resulting holding exceeds the supported quantity range."
            );
            return false;
        }

        cash.withdrawCash(settlementAmount);

        if (newHolding) {
            entityManager.persist(holding);
        }

        return true;
    }

    private boolean settleSell(
            Order order,
            Account account,
            CashBalance cash,
            Holding holding,
            Money settlementAmount
    ) {
        requireSellReservation(order, holding);

        boolean newCashBalance = cash == null;

        // if account doesn't have any balance in the quotes currency
        // ex: euros or USD
        if (newCashBalance) {
            cash = new CashBalance(
                    account,
                    order.getInstrument().getQuoteCurrency()
            );
        }

        // depositCash validates the resulting balance before changing it.
        // No holding quantities have been changed at this point.
        try {
            cash.depositCash(settlementAmount);
        } catch (IllegalStateException rejection) {
            finishWithoutFill(
                    order,
                    holding,
                    OrderStatus.REJECTED,
                    rejection.getMessage()
            );
            return false;
        }

        // reserves amount of shares client wants to sell
        // technically not necesssary because we already lock the client account at this point
        holding.settleReservedSale(order.getRequestedQuantity());

        if (newCashBalance) {
            entityManager.persist(cash);
        }

        return true;
    }

    private void finishWithoutFill(
            Order order,
            Holding holding,
            OrderStatus status,
            String reason
    ) {
        if (order.getSide() == OrderSide.SELL) {
            requireSellReservation(order, holding);
            holding.releaseReservedQuantity(
                    order.getRequestedQuantity()
            );
        }

        events.appendEvent(
                order.getOrderId(),
                status,
                reason,
                null,
                null
        );
    }

    private void requireSellReservation(
            Order order,
            Holding holding
    ) {
        BigDecimal quantity = order.getRequestedQuantity();

        if (holding == null
                || holding.getReservedQuantity().compareTo(quantity) < 0
                || holding.getTotalQuantity().compareTo(quantity) < 0) {
            throw new IllegalStateException(
                    "Accepted sell order is missing its required holding "
                            + "or reservation."
            );
        }
    }
}
