package com.neueda.leap;

import com.neueda.leap.entities.Instrument;
import com.neueda.leap.entities.Order;
import com.neueda.leap.enums.AssetClass;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.models.MarketErrorDto;
import com.neueda.leap.models.MarketQuoteDto;
import com.neueda.leap.models.MarketQuoteResultDto;
import com.neueda.leap.models.SubmitOrderRequest;
import com.neueda.leap.service.OrderExecutionPolicy;
import com.neueda.leap.service.TradingRules;
import com.neueda.leap.service.order_validation.OrderValidationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DelayedQuotePolicyTest {
    private final Instant now = Instant.parse("2026-10-14T18:00:00Z");
    private final OffsetDateTime oldQuote = OffsetDateTime.parse("2026-10-14T17:30:00Z");
    private final Instrument instrument = new Instrument("US", "AAPL", "Apple", AssetClass.EQUITY, Currency.USD);
    private final OrderExecutionPolicy policy = new OrderExecutionPolicy(new TradingRules());

    private Order order(OrderSide side) {
        Order order = mock(Order.class);
        when(order.getInstrument()).thenReturn(instrument);
        when(order.getSide()).thenReturn(side);
        return order;
    }

    private MarketQuoteResultDto quote(String timestamp) {
        return new MarketQuoteResultDto("AAPL", new MarketQuoteDto("AAPL",
                new BigDecimal("100"), new BigDecimal("99"), new BigDecimal("101"),
                BigDecimal.ONE, "USD", null, null, null, timestamp, "unknown"),
                "cache", true, null);
    }

    @Test
    void delayedStaleQuotesCanExecuteAtTheCorrectSidePrice() {
        var result = quote(oldQuote.toString());
        var buy = policy.executionQuote(order(OrderSide.BUY), result, now).orElseThrow();
        var sell = policy.executionQuote(order(OrderSide.SELL), result, now).orElseThrow();
        assertThat(buy.price().amount()).isEqualByComparingTo("101");
        assertThat(sell.price().amount()).isEqualByComparingTo("99");
        assertThat(buy.quoteAt()).isEqualTo(oldQuote);
    }

    @Test
    void invalidQuotesAndProviderErrorsStillCannotExecute() {
        Order order = order(OrderSide.BUY);
        assertThat(policy.executionQuote(order, null, now)).isEmpty();
        assertThat(policy.executionQuote(order, quote("invalid"), now)).isEmpty();
        assertThat(policy.executionQuote(order, quote("2026-10-14T18:01:00Z"), now)).isEmpty();
        var backup = new MarketQuoteResultDto("AAPL", quote(oldQuote.toString()).quote(),
                "local-cache", true, new MarketErrorDto("CACHED_FALLBACK", "Unavailable"));
        assertThat(policy.executionQuote(order, backup, now)).isEmpty();
    }

    @Test
    void delayedSubmissionAcceptedButFutureTimestampRejected() {
        var validation = new OrderValidationService(new TradingRules(), Clock.fixed(now, ZoneOffset.UTC));
        var request = new SubmitOrderRequest(1, 1, OrderSide.BUY, BigDecimal.ONE,
                new BigDecimal("101"), oldQuote, "USD");
        validation.validateSubmissionRequest(request);
        assertThat(validation.quoteFromSubmittedRequest(request, instrument).quoteAt()).isEqualTo(oldQuote);
        var future = new SubmitOrderRequest(1, 1, OrderSide.BUY, BigDecimal.ONE,
                new BigDecimal("101"), OffsetDateTime.ofInstant(now.plusSeconds(1), ZoneOffset.UTC), "USD");
        assertThatThrownBy(() -> validation.quoteFromSubmittedRequest(future, instrument))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("future");
    }

    @Test
    void pendingDeadlineStillExpiresAtTwoMinutes() {
        assertThat(policy.expirationReason(order(OrderSide.BUY),
                OffsetDateTime.ofInstant(now.minusSeconds(120), ZoneOffset.UTC), now))
                .hasValue("The order exceeded its two-minute execution deadline.");
    }
}
