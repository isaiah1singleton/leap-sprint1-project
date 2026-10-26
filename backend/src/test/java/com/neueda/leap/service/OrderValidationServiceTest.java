package com.neueda.leap.service;

import com.neueda.leap.entities.*;
import com.neueda.leap.enums.*;
import com.neueda.leap.models.SubmitOrderRequest;
import com.neueda.leap.service.order_validation.OrderValidationService;
import com.neueda.leap.service.order_validation.OrderValidationException;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderValidationServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T14:00:00Z"), ZoneOffset.UTC);
    private final OrderValidationService validation = new OrderValidationService(mock(TradingRules.class), clock);
    private final OffsetDateTime now = OffsetDateTime.now(clock);
    private final Instrument instrument = new Instrument("CRYPTO", "BTC", "Bitcoin", AssetClass.CRYPTO, Currency.USD);

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.000000001", "10000000000000000"})
    void rejectsInvalidQuantityOrPriceBeforePersistence(String value) {
        assertThatThrownBy(() -> validation.validateSubmissionRequest(request(new BigDecimal(value), BigDecimal.TEN, now, "USD")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validation.validateSubmissionRequest(request(BigDecimal.ONE, new BigDecimal(value), now, "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsEightDecimalCryptoAmountsWithoutRoundingThem() {
        var request = request(new BigDecimal("0.12345678"), new BigDecimal("123.12345678"), now, "USD");
        validation.validateSubmissionRequest(request);
        var quote = validation.quoteFromSubmittedRequest(request, instrument);
        assertThat(quote.price().amount()).isEqualByComparingTo("123.12345678");
    }

    @Test
    void rejectsFutureOrCurrencyMismatchedQuotes() {
        assertThatThrownBy(() -> validation.quoteFromSubmittedRequest(request(BigDecimal.ONE, BigDecimal.TEN, now.plusNanos(1), "USD"), instrument))
                .hasMessageContaining("future");
        assertThatThrownBy(() -> validation.quoteFromSubmittedRequest(request(BigDecimal.ONE, BigDecimal.TEN, now, "EUR"), instrument))
                .hasMessageContaining("currency");
    }

    @Test
    void checksOwnershipBeforeAnyTradingPolicyOrResourceDecisions() {
        var account = new Account(new Client("Owner", "owner@example.test", "hash"), "Trading");
        var order = new Order(account, instrument, OrderSide.BUY, BigDecimal.ONE, BigDecimal.TEN, now, now);
        assertThatThrownBy(() -> validation.validate(order, new QuoteSnapshot(new Money(BigDecimal.TEN, Currency.USD), now),
                "other@example.test", new Money(new BigDecimal("100"), Currency.USD), BigDecimal.TEN))
                .isInstanceOf(OrderValidationException.class).hasMessageContaining("authenticated client");
    }

    private SubmitOrderRequest request(BigDecimal quantity, BigDecimal price, OffsetDateTime time, String currency) {
        return new SubmitOrderRequest(1, 1, OrderSide.BUY, quantity, price, time, currency);
    }
}
