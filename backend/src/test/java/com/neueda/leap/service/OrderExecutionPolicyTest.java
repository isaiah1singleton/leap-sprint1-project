package com.neueda.leap.service;

import com.neueda.leap.entities.*;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.models.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class OrderExecutionPolicyTest {
    private final OrderExecutionPolicy policy = new OrderExecutionPolicy(new TradingRules());
    @Test void expiryBoundaryAndMarketClose() {
        Order o = order(OrderSide.BUY);
        assertThat(policy.expirationReason(o, AT.minusSeconds(119), NOW)).isEmpty();
        assertThat(policy.expirationReason(o, AT.minusSeconds(120), NOW)).hasValueSatisfying(s -> assertThat(s).contains("two-minute"));
        assertThat(policy.expirationReason(o, AT.minusSeconds(121), NOW)).isPresent();
        assertThatIllegalStateException().isThrownBy(() -> policy.expirationReason(o, AT.plusNanos(1), NOW));
        var close = java.time.Instant.parse("2026-10-14T20:00:00Z");
        assertThat(policy.expirationReason(o, close.atOffset(java.time.ZoneOffset.UTC).minusSeconds(1), close)).hasValueSatisfying(s -> assertThat(s).contains("market closed"));
        id(o.getInstrument(), "market", "UNKNOWN");
        assertThatIllegalStateException().isThrownBy(() -> policy.expirationReason(o, AT, NOW));
    }
    @Test void sidePricesPreserveDelayedQuoteTime() {
        assertThat(policy.executionQuote(order(OrderSide.BUY), quote(), NOW).orElseThrow().price().amount()).isEqualByComparingTo("101");
        assertThat(policy.executionQuote(order(OrderSide.SELL), quote(), NOW).orElseThrow().price().amount()).isEqualByComparingTo("99");
        assertThat(policy.executionQuote(order(OrderSide.BUY), quote(), NOW).orElseThrow().quoteAt()).isEqualTo(AT.minusMinutes(10));
    }
    @Test void errorsMissingQuotesSymbolsAndCurrencyAreRejected() {
        Order o = order(OrderSide.BUY); var q = quote();
        assertThat(policy.executionQuote(o, null, NOW)).isEmpty();
        assertThat(policy.executionQuote(o, new MarketQuoteResultDto("AAPL", null, null, false, null), NOW)).isEmpty();
        assertThat(policy.executionQuote(o, new MarketQuoteResultDto("AAPL", q.quote(), null, true, new MarketErrorDto("FAILED", "Unavailable")), NOW)).isEmpty();
        assertThat(policy.executionQuote(o, new MarketQuoteResultDto("OTHER", q.quote(), null, false, null), NOW)).isEmpty();
        assertThat(policy.executionQuote(o, quote("OTHER", "99", "101", AT.toString()), NOW)).isEmpty();
        for (String c : new String[]{null, "EUR"}) {
            var bad = new MarketQuoteDto("AAPL", bd("100"), bd("99"), bd("101"), null, c, null, null, null, AT.toString(), null);
            assertThat(policy.executionQuote(o, new MarketQuoteResultDto("AAPL", bad, null, false, null), NOW)).isEmpty();
        }
    }
    @ParameterizedTest @ValueSource(strings={"", "invalid", "2026-10-14T18:00:01Z"})
    void rejectsInvalidQuoteTimes(String time) {
        assertThat(policy.executionQuote(order(OrderSide.BUY), quote("AAPL", "99", "101", time), NOW)).isEmpty();
    }
    @Test void rejectsMissingAndNonpositiveSidePrices() {
        for (String p : new String[]{null, "0", "-1"}) {
            assertThat(policy.executionQuote(order(OrderSide.BUY), quote("AAPL", "99", p, AT.toString()), NOW)).isEmpty();
            assertThat(policy.executionQuote(order(OrderSide.SELL), quote("AAPL", p, "101", AT.toString()), NOW)).isEmpty();
        }
        assertThat(policy.executionQuote(order(OrderSide.BUY), quote("AAPL", "99", "101", null), NOW)).isEmpty();
    }
}
