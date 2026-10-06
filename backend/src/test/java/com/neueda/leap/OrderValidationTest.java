package com.neueda.leap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class OrderValidationTest {
    private static final Instant NOW = Instant.parse("2026-10-05T14:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private final OrderValidationService validation = new OrderValidationService(new TradingRules(), CLOCK);
    private final StubSubmittedOrderSource stub = new StubSubmittedOrderSource(CLOCK);

    @Test
    void validatesSubmittedOrderWithoutRepositoryAccessOrStateChanges() {
        Order order = stub.getSubmittedOrder(StubSubmittedOrderSource.STUB_ORDER_ID, "client@example.com");
        assertThatCode(() -> validation.validate(order, quote(NOW), "client@example.com",
                stub.availableCash(order), stub.availableUnits(order))).doesNotThrowAnyException();
        assertThat(order.getCurrentOrderStatus().name()).isEqualTo("SUBMITTED");
    }

    @Test
    void controllerReceivesStubOrderAndReturnsNoContent() {
        OrderValidationController controller = new OrderValidationController(validation, stub);
        assertThat(controller.validate(StubSubmittedOrderSource.STUB_ORDER_ID,
                quote(NOW), () -> "client@example.com").getStatusCode().value()).isEqualTo(204);
        assertThatThrownBy(() -> controller.validate(2, quote(NOW), () -> "client@example.com"))
                .isInstanceOf(NoSuchElementException.class);
        var failure = controller.handleValidationFailure(
                new OrderValidationException("INSUFFICIENT_CASH", "Available cash is too low."));
        assertThat(failure.getStatus()).isEqualTo(422);
        assertThat(failure.getProperties()).containsEntry("code", "INSUFFICIENT_CASH");
    }

    @Test
    void throwsCustomErrorWhenPendingCashLeavesTooLittleAvailable() {
        Order order = stub.getSubmittedOrder(1, "client@example.com");
        assertFailure("INSUFFICIENT_CASH", order, quote(NOW),
                new Money(new BigDecimal("10"), Currency.USD), BigDecimal.TEN);
    }

    @Test
    void throwsCustomErrorWhenPendingSalesLeaveTooFewUnits() {
        Order order = order(OrderSide.SELL, BigDecimal.valueOf(2), true);
        assertFailure("INSUFFICIENT_UNITS", order, quote(NOW),
                new Money(BigDecimal.ZERO, Currency.USD), BigDecimal.ONE);
    }

    @Test
    void rejectsStaleFrontendQuoteAndWrongCurrency() {
        Order order = stub.getSubmittedOrder(1, "client@example.com");
        assertFailure("QUOTE_STALE", order, quote(NOW.minusSeconds(120)),
                stub.availableCash(order), stub.availableUnits(order));
        assertFailure("QUOTE_CURRENCY_MISMATCH", order,
                new QuoteSnapshot(new Money(BigDecimal.TEN, Currency.EUR), timestamp(NOW)),
                stub.availableCash(order), stub.availableUnits(order));
    }

    @Test
    void enforcesOwnerAvailabilityAndQuantityPrecision() {
        Order order = stub.getSubmittedOrder(1, "client@example.com");
        assertThatThrownBy(() -> validation.validate(order, quote(NOW), "other@example.com",
                stub.availableCash(order), stub.availableUnits(order)))
                .isInstanceOfSatisfying(OrderValidationException.class,
                        error -> assertThat(error.getCode()).isEqualTo("ORDER_NOT_OWNED"));

        Order disabled = order(OrderSide.BUY, BigDecimal.ONE, false);
        assertFailure("INSTRUMENT_UNAVAILABLE", disabled, quote(NOW),
                stub.availableCash(disabled), stub.availableUnits(disabled));

        Order overprecise = order(OrderSide.BUY, new BigDecimal("0.123456789"), true);
        assertFailure("QUANTITY_PRECISION", overprecise, quote(NOW),
                stub.availableCash(overprecise), stub.availableUnits(overprecise));
    }

    @Test
    void configuredMarketWindowExcludesWeekends() {
        var window = new TradingRules().marketWindow("NYSE").orElseThrow();
        assertThat(window.isOpen(Instant.parse("2026-10-03T14:00:00Z"))).isFalse();
        assertThat(window.isOpen(NOW)).isTrue();
    }

    private void assertFailure(String code, Order order, QuoteSnapshot quote,
                               Money availableCash, BigDecimal availableUnits) {
        assertThatThrownBy(() -> validation.validate(order, quote, "client@example.com",
                availableCash, availableUnits))
                .isInstanceOfSatisfying(OrderValidationException.class,
                        error -> assertThat(error.getCode()).isEqualTo(code));
    }

    private static Order order(OrderSide side, BigDecimal quantity, boolean tradable) {
        Client client = new Client("Client", "client@example.com", "STUB_ONLY");
        Account account = new Account(client, "Test account");
        Instrument instrument = new Instrument("CRYPTO", "BTC", "Bitcoin",
                AssetClass.CRYPTO, Currency.USD);
        if (tradable) instrument.enableTrading();
        return new Order(account, instrument, side, quantity, timestamp(NOW));
    }

    private static QuoteSnapshot quote(Instant at) {
        return new QuoteSnapshot(new Money(BigDecimal.TEN, Currency.USD), timestamp(at));
    }

    private static OffsetDateTime timestamp(Instant at) {
        return OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}
