package com.neueda.leap.service;

import com.neueda.leap.entities.*;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.models.OrderResponse;
import com.neueda.leap.models.SubmitOrderRequest;
import com.neueda.leap.repository.*;
import com.neueda.leap.service.order_validation.OrderValidationException;
import com.neueda.leap.service.order_validation.OrderValidationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderDecisionService {
    private final AccountRepository accounts;
    private final InstrumentRepository instruments;
    private final OrderRepository orders;
    private final OrderEventRepository events;
    private final OrderEventService eventService;
    private final OutboxEventRepository outbox;
    private final OrderValidationService validation;
    private final EntityManager entityManager;
    private final Clock clock;

    public OrderDecisionService(AccountRepository accounts, InstrumentRepository instruments,
            OrderRepository orders, OrderEventRepository events, OrderEventService eventService,
            OutboxEventRepository outbox, OrderValidationService validation,
            EntityManager entityManager, Clock clock) {
        this.accounts = accounts;
        this.instruments = instruments;
        this.orders = orders;
        this.events = events;
        this.eventService = eventService;
        this.outbox = outbox;
        this.validation = validation;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional
    public OrderResponse decide(SubmitOrderRequest request, Integer clientId, String email, QuoteSnapshot quote) {
        Account account = accounts.findByAccountIdAndClient_ClientId(request.accountId(), clientId)
                .orElseThrow(() -> new NoSuchElementException("Account not found."));
        Instrument instrument = instruments.findById(request.instrumentId())
                .orElseThrow(() -> new NoSuchElementException("Instrument not found."));
        OffsetDateTime now = OffsetDateTime.now(clock);
        Order order = orders.saveAndFlush(new Order(account, instrument, request.side(), request.quantity(),
                quote.price().amount(), quote.quoteAt(), now));
        eventService.appendEvent(order.getOrderId(), OrderStatus.SUBMITTED, null, null, null);

        // Lock the same row that concurrent withdrawals or sells modify.
        CashBalance cash = request.side() == OrderSide.BUY
                ? entityManager.find(CashBalance.class,
                        new CashBalanceId(account.getAccountId(), instrument.getQuoteCurrency()), LockModeType.PESSIMISTIC_WRITE)
                : null;
        Holding holding = request.side() == OrderSide.SELL
                ? entityManager.find(Holding.class,
                        new HoldingId(account.getAccountId(), instrument.getInstrumentId()), LockModeType.PESSIMISTIC_WRITE)
                : null;
        Money availableCash = cash == null ? null : cash.availableBalance();
        BigDecimal availableUnits = holding == null ? BigDecimal.ZERO : holding.availableQuantity();
        try {
            validation.validate(order, quote, email, availableCash, availableUnits);
            if (request.side() == OrderSide.SELL) holding.reserveQuantity(request.quantity());
            OrderEvent accepted = eventService.appendEvent(order.getOrderId(), OrderStatus.ACCEPTED,
                    null, quote.price().amount(), quote.quoteAt());
            outbox.save(OutboxEvent.orderAccepted(order.getOrderId()));
            return OrderResponse.from(order, accepted);
        } catch (OrderValidationException rejection) {
            OrderEvent rejected = eventService.appendEvent(order.getOrderId(), OrderStatus.REJECTED,
                    rejection.getCode() + ": " + rejection.getMessage(), null, null);
            return OrderResponse.from(order, rejected);
        }
    }

    @Transactional
    public OrderResponse cancel(Integer orderId, Integer clientId) {
        Order order = ownedOrder(orderId, clientId);
        entityManager.lock(order, LockModeType.PESSIMISTIC_WRITE);
        OrderEvent latest = latest(orderId);
        if (latest.getStatus() != OrderStatus.ACCEPTED) {
            throw new IllegalStateException("Only an accepted, unfilled order can be cancelled.");
        }
        if (order.getSide() == OrderSide.SELL) {
            Holding holding = entityManager.find(Holding.class,
                    new HoldingId(order.getAccount().getAccountId(), order.getInstrument().getInstrumentId()),
                    LockModeType.PESSIMISTIC_WRITE);
            if (holding == null) throw new IllegalStateException("Reserved holding is missing.");
            holding.releaseReservedQuantity(order.getRequestedQuantity());
        }
        OrderEvent cancelled = eventService.appendEvent(orderId, OrderStatus.CANCELLED,
                "Cancelled by client before execution.", null, null);
        return OrderResponse.from(order, cancelled);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(Integer accountId, Integer clientId) {
        accounts.findByAccountIdAndClient_ClientId(accountId, clientId)
                .orElseThrow(() -> new NoSuchElementException("Account not found."));
        return orders.findByAccount_AccountIdAndAccount_Client_ClientIdOrderBySubmittedAtDesc(accountId, clientId)
                .stream().map(order -> OrderResponse.from(order, latest(order.getOrderId()))).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Integer orderId, Integer clientId) {
        Order order = ownedOrder(orderId, clientId);
        return OrderResponse.from(order, latest(orderId));
    }

    private Order ownedOrder(Integer orderId, Integer clientId) {
        if (orderId == null || orderId <= 0) throw new IllegalArgumentException("Order ID must be positive.");
        return orders.findByOrderIdAndAccount_Client_ClientId(orderId, clientId)
                .orElseThrow(() -> new NoSuchElementException("Order not found."));
    }

    private OrderEvent latest(Integer orderId) {
        return events.findFirstByOrder_OrderIdOrderByOrderEventIdDesc(orderId)
                .orElseThrow(() -> new IllegalStateException("Order has no status event."));
    }
}
