package com.neueda.leap.service;

import com.neueda.leap.entities.Account;
import com.neueda.leap.entities.CashBalance;
import com.neueda.leap.entities.CashBalanceId;
import com.neueda.leap.entities.Holding;
import com.neueda.leap.entities.HoldingId;
import com.neueda.leap.entities.Instrument;
import com.neueda.leap.entities.Money;
import com.neueda.leap.entities.Order;
import com.neueda.leap.entities.OrderEvent;
import com.neueda.leap.entities.QuoteSnapshot;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.models.OrderResponse;
import com.neueda.leap.models.SubmitOrderRequest;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.InstrumentRepository;
import com.neueda.leap.repository.OrderRepository;
import com.neueda.leap.service.order_validation.OrderValidationException;
import com.neueda.leap.service.order_validation.OrderValidationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderSubmissionService {
    private final AccountRepository accounts;
    private final InstrumentRepository instruments;
    private final OrderRepository orders;
    private final OrderEventService events;
    private final OrderValidationService validation;
    private final EntityManager entityManager;
    private final Clock clock;

    public OrderSubmissionService(AccountRepository accounts, InstrumentRepository instruments,
            OrderRepository orders, OrderEventService events, OrderValidationService validation,
             EntityManager entityManager, Clock clock) {
        this.accounts = accounts;
        this.instruments = instruments;
        this.orders = orders;
        this.events = events;
        this.validation = validation;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional
    public OrderResponse submit(SubmitOrderRequest request, Integer clientId, String email) {
        validation.validateSubmissionRequest(request);

        Account account = accounts.findByAccountIdAndClient_ClientId(request.accountId(), clientId)
                .orElseThrow(() -> new NoSuchElementException("Account not found."));
        Instrument instrument = instruments.findById(request.instrumentId())
                .orElseThrow(() -> new NoSuchElementException("Instrument not found."));
        QuoteSnapshot quote = validation.quoteFromSubmittedRequest(request, instrument);

        // Transfers take this same lock. It serializes submissions for one account.
        entityManager.lock(account, LockModeType.PESSIMISTIC_WRITE);
        if (orders.existsPendingAcceptedOrder(account.getAccountId())) {
            throw new IllegalStateException("Resolve the accepted order before submitting another order.");
        }

        Order order = orders.saveAndFlush(new Order(account, instrument, request.side(),
                request.quantity(), quote.price().amount(), quote.quoteAt(), OffsetDateTime.now(clock)));
        events.appendEvent(order.getOrderId(), OrderStatus.SUBMITTED, null, null, null);

        CashBalance cash = request.side() == OrderSide.BUY
                ? entityManager.find(CashBalance.class,
                        new CashBalanceId(account.getAccountId(), instrument.getQuoteCurrency()),
                        LockModeType.PESSIMISTIC_WRITE)
                : null;
        Holding holding = request.side() == OrderSide.SELL
                ? entityManager.find(Holding.class,
                        new HoldingId(account.getAccountId(), instrument.getInstrumentId()),
                        LockModeType.PESSIMISTIC_WRITE)
                : null;
        Money totalCash = cash == null ? null : cash.getTotalBalance();
        BigDecimal availableUnits = holding == null ? BigDecimal.ZERO : holding.availableQuantity();

        try {
            validation.validate(order, quote, email, totalCash, availableUnits);
            if (request.side() == OrderSide.SELL) {
                holding.reserveQuantity(request.quantity());
            }
            OrderEvent accepted = events.appendEvent(order.getOrderId(), OrderStatus.ACCEPTED,
                    null, quote.price().amount(), quote.quoteAt());
            return OrderResponse.from(order, accepted);
        } catch (OrderValidationException rejection) {
            OrderEvent rejected = events.appendEvent(order.getOrderId(), OrderStatus.REJECTED,
                    rejection.getCode() + ": " + rejection.getMessage(), null, null);
            return OrderResponse.from(order, rejected);
        }
    }

}
