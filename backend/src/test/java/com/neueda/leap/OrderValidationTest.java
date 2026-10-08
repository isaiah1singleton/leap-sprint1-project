package com.neueda.leap;

import com.neueda.leap.controller.OrderValidationController;
import com.neueda.leap.entities.Account;
import com.neueda.leap.entities.Client;
import com.neueda.leap.entities.Instrument;
import com.neueda.leap.entities.Money;
import com.neueda.leap.entities.Order;
import com.neueda.leap.entities.QuoteSnapshot;
import com.neueda.leap.enums.AssetClass;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.service.StubSubmittedOrderSource;
import com.neueda.leap.service.TradingRules;
import com.neueda.leap.service.order_validation.OrderValidationException;
import com.neueda.leap.service.order_validation.OrderValidationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class OrderValidationTest {

    private static final Instant NOW =
            Instant.parse("2026-10-05T14:00:00Z");

    private static final Clock CLOCK =
            Clock.fixed(NOW, ZoneOffset.UTC);

    private final OrderValidationService validation =
            new OrderValidationService(new TradingRules(), CLOCK);

    private final StubSubmittedOrderSource stub =
            new StubSubmittedOrderSource(CLOCK);

    @Test
    void validatesAnOrderWithSufficientResources() {
        Order order = stub.getSubmittedOrder(
                StubSubmittedOrderSource.STUB_ORDER_ID,
                "client@example.com"
        );

        assertDoesNotThrow(
                () -> validation.validate(
                        order,
                        quote(NOW),
                        "client@example.com",
                        stub.availableCash(order),
                        stub.availableUnits(order)
                )
        );
    }

    @Test
    void controllerReceivesStubOrderAndReturnsNoContent() {
        OrderValidationController controller =
                new OrderValidationController(validation, stub);

        var response = controller.validate(
                StubSubmittedOrderSource.STUB_ORDER_ID,
                quote(NOW),
                () -> "client@example.com"
        );

        assertEquals(204, response.getStatusCode().value());

        assertThrows(
                NoSuchElementException.class,
                () -> controller.validate(
                        2,
                        quote(NOW),
                        () -> "client@example.com"
                )
        );

        var failure = controller.handleValidationFailure(
                new OrderValidationException(
                        "INSUFFICIENT_CASH",
                        "Available cash is too low."
                )
        );

        assertEquals(422, failure.getStatus());
        assertNotNull(failure.getProperties());
        assertEquals(
                "INSUFFICIENT_CASH",
                failure.getProperties().get("code")
        );
    }

    @Test
    void rejectsInsufficientAvailableCash() {
        Order order = stub.getSubmittedOrder(
                1,
                "client@example.com"
        );

        assertFailure(
                "INSUFFICIENT_CASH",
                order,
                quote(NOW),
                new Money(new BigDecimal("5"), Currency.USD),
                BigDecimal.TEN
        );
    }

    @Test
    void rejectsInsufficientAvailableUnits() {
        Order order = order(
                OrderSide.SELL,
                new BigDecimal("2"),
                true,
                AssetClass.CRYPTO,
                "CRYPTO"
        );

        assertFailure(
                "INSUFFICIENT_UNITS",
                order,
                quote(NOW),
                new Money(BigDecimal.ZERO, Currency.USD),
                BigDecimal.ONE
        );
    }

    @Test
    void rejectsStaleQuotesAndWrongCurrencies() {
        Order order = stub.getSubmittedOrder(
                1,
                "client@example.com"
        );

        assertFailure(
                "QUOTE_STALE",
                order,
                quote(NOW.minusSeconds(120)),
                stub.availableCash(order),
                stub.availableUnits(order)
        );

        assertFailure(
                "QUOTE_CURRENCY_MISMATCH",
                order,
                new QuoteSnapshot(
                        new Money(BigDecimal.TEN, Currency.EUR),
                        timestamp(NOW)
                ),
                stub.availableCash(order),
                stub.availableUnits(order)
        );
    }

    @Test
    void rejectsOrdersOwnedByAnotherClient() {
        Order order = stub.getSubmittedOrder(
                1,
                "client@example.com"
        );

        OrderValidationException error = assertThrows(
                OrderValidationException.class,
                () -> validation.validate(
                        order,
                        quote(NOW),
                        "other@example.com",
                        stub.availableCash(order),
                        stub.availableUnits(order)
                )
        );

        assertEquals("ORDER_NOT_OWNED", error.getCode());
    }

    @Test
    void rejectsAnInstrumentThatIsNotTradable() {
        Order order = order(
                OrderSide.BUY,
                BigDecimal.ONE,
                false,
                AssetClass.CRYPTO,
                "CRYPTO"
        );

        assertFailure(
                "INSTRUMENT_UNAVAILABLE",
                order,
                quote(NOW),
                stub.availableCash(order),
                stub.availableUnits(order)
        );
    }

    @Test
    void rejectsFractionalEquityQuantityUnderCurrentTradingRules() {
        Order order = order(
                OrderSide.BUY,
                new BigDecimal("0.5"),
                true,
                AssetClass.EQUITY,
                "US"
        );

        assertFailure(
                "QUANTITY_PRECISION",
                order,
                quote(NOW),
                stub.availableCash(order),
                stub.availableUnits(order)
        );
    }

    @Test
    void configuredMarketWindowExcludesWeekends() {
        var window = new TradingRules()
                .marketWindow("US")
                .orElseThrow();

        assertFalse(
                window.isOpen(
                        Instant.parse("2026-10-03T14:00:00Z")
                )
        );

        assertTrue(window.isOpen(NOW));
    }

    private void assertFailure(
            String expectedCode,
            Order order,
            QuoteSnapshot quote,
            Money availableCash,
            BigDecimal availableUnits
    ) {
        OrderValidationException error = assertThrows(
                OrderValidationException.class,
                () -> validation.validate(
                        order,
                        quote,
                        "client@example.com",
                        availableCash,
                        availableUnits
                )
        );

        assertEquals(expectedCode, error.getCode());
    }

    private static Order order(
            OrderSide side,
            BigDecimal quantity,
            boolean tradable,
            AssetClass assetClass,
            String market
    ) {
        Client client = new Client(
                "Client",
                "client@example.com",
                "STUB_ONLY"
        );

        Account account = new Account(client, "Test account");

        Instrument instrument = new Instrument(
                market,
                "TEST",
                "Test instrument",
                assetClass,
                Currency.USD
        );

        if (tradable) {
            instrument.enableTrading();
        }

        return new Order(
                account,
                instrument,
                side,
                quantity,
                BigDecimal.TEN,
                timestamp(NOW),
                timestamp(NOW)
        );
    }

    private static QuoteSnapshot quote(Instant at) {
        return new QuoteSnapshot(
                new Money(BigDecimal.TEN, Currency.USD),
                timestamp(at)
        );
    }

    private static OffsetDateTime timestamp(Instant at) {
        return OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}
