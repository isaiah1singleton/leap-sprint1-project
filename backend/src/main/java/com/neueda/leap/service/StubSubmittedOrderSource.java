package com.neueda.leap.service;

import com.neueda.leap.entities.Account;
import com.neueda.leap.entities.Client;
import com.neueda.leap.entities.Instrument;
import com.neueda.leap.entities.Money;
import com.neueda.leap.entities.Order;
import com.neueda.leap.enums.AssetClass;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.enums.OrderSide;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Component;

/**
 * TEMPORARY STUB: supplies one in-memory submitted order and resource snapshot.
 * Replace this with the real submitted-order and portfolio source later.
 * No values here are read from, or written to, the database.
 */
@Component
public class StubSubmittedOrderSource {
    public static final int STUB_ORDER_ID = 1;

    private final Clock clock;

    public StubSubmittedOrderSource(Clock clock) {
        this.clock = clock;
    }

    public Order getSubmittedOrder(Integer orderId, String authenticatedEmail) {
        if (orderId == null || orderId != STUB_ORDER_ID) {
            throw new NoSuchElementException("Stub order not found.");
        }
        Client client = new Client("Stub client", authenticatedEmail, "STUB_ONLY");
        Account account = new Account(client, "Stub trading account");
        Instrument instrument = new Instrument(
                "CRYPTO", "BTC", "Stub Bitcoin", AssetClass.CRYPTO, Currency.USD);
        instrument.enableTrading();
        return new Order(account, instrument, OrderSide.BUY, BigDecimal.ONE,
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    // These are already-available amounts: any pending reservations are assumed
    // to have been deducted by the future source that replaces this stub.
    public Money availableCash(Order order) {
        return new Money(new BigDecimal("1000"), order.getInstrument().getQuoteCurrency());
    }

    public BigDecimal availableUnits(Order order) {
        return new BigDecimal("10");
    }
}
