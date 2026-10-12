package com.neueda.leap.service;

import com.neueda.leap.entities.Account;
import com.neueda.leap.entities.Client;
import com.neueda.leap.entities.Instrument;
import com.neueda.leap.entities.Money;
import com.neueda.leap.entities.Order;
import com.neueda.leap.enums.AssetClass;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.enums.OrderSide;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;

@Component
public class StubSubmittedOrderSource {

    public static final int STUB_ORDER_ID = 1;

    private final Clock clock;

    public StubSubmittedOrderSource(Clock clock) {
        this.clock = clock;
    }

    public Order getSubmittedOrder(
            Integer orderId,
            String authenticatedEmail
    ) {
        if (orderId == null || orderId != STUB_ORDER_ID) {
            throw new NoSuchElementException("Stub order not found.");
        }

        Client client = new Client(
                "Stub client",
                authenticatedEmail,
                "STUB_ONLY"
        );

        Account account = new Account(
                client,
                "Stub trading account"
        );

        Instrument instrument = new Instrument(
                "CRYPTO",
                "BTC",
                "Stub Bitcoin",
                AssetClass.CRYPTO,
                Currency.USD
        );

        instrument.enableTrading();

        OffsetDateTime submittedAt = OffsetDateTime.now(clock);

        return new Order(
                account,
                instrument,
                OrderSide.BUY,
                BigDecimal.ONE,
                BigDecimal.TEN,
                submittedAt,
                submittedAt
        );
    }

    public Money availableCash(Order order) {
        return new Money(
                new BigDecimal("1000"),
                order.getInstrument().getQuoteCurrency()
        );
    }

    public BigDecimal availableUnits(Order order) {
        return BigDecimal.TEN;
    }
}
