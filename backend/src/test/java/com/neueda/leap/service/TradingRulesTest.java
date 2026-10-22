package com.neueda.leap.service;

import com.neueda.leap.enums.AssetClass;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class TradingRulesTest {
    @Test void defaultsAndInstrumentOverridesFailClosed() {
        Properties p = new Properties(); TradingRules r = new TradingRules(p);
        assertThat(r.supports(null)).isFalse(); assertThat(r.supports(AssetClass.EQUITY)).isFalse();
        assertThat(r.isRestricted(instrument())).isFalse();
        p.setProperty("trading.asset.EQUITY.supported", "true"); p.setProperty("trading.asset.EQUITY.quantity-scale", "0");
        assertThat(r.supports(AssetClass.EQUITY)).isTrue(); assertThat(r.allowedQuantityScale(instrument())).isZero();
        p.setProperty("trading.instrument.2.quantity-scale", "3"); p.setProperty("trading.instrument.2.restricted", "true");
        assertThat(r.allowedQuantityScale(instrument())).isEqualTo(3); assertThat(r.isRestricted(instrument())).isTrue();
        p.setProperty("trading.instrument.2.quantity-scale", "-1");
        assertThatIllegalStateException().isThrownBy(() -> r.allowedQuantityScale(instrument()));
        p.setProperty("trading.instrument.2.quantity-scale", "bad");
        assertThatIllegalArgumentException().isThrownBy(() -> r.allowedQuantityScale(instrument()));
        assertThatIllegalStateException().isThrownBy(() -> new TradingRules(new Properties()).allowedQuantityScale(instrument()));
    }
    @Test void marketBoundariesAndDaylightSavingUseExchangeTime() {
        var r = new TradingRules(); var w = r.marketWindow(" us ").orElseThrow();
        assertThat(w.isOpen(Instant.parse("2026-10-14T13:29:59Z"))).isFalse();
        assertThat(w.isOpen(Instant.parse("2026-10-14T13:30:00Z"))).isTrue();
        assertThat(w.isOpen(Instant.parse("2026-10-14T19:59:59Z"))).isTrue();
        assertThat(w.isOpen(Instant.parse("2026-10-14T20:00:00Z"))).isFalse();
        assertThat(w.isOpen(Instant.parse("2026-10-17T15:00:00Z"))).isFalse();
        assertThat(w.isOpen(Instant.parse("2026-01-14T14:29:59Z"))).isFalse();
        assertThat(w.isOpen(Instant.parse("2026-01-14T14:30:00Z"))).isTrue();
        assertThat(r.marketWindow(null)).isEmpty(); assertThat(r.marketWindow("UNKNOWN")).isEmpty();
        var crypto = r.marketWindow("CRYPTO").orElseThrow();
        assertThat(crypto.isOpen(Instant.parse("2026-10-18T00:00:00Z"))).isTrue();
        assertThat(crypto.isOpen(Instant.parse("2026-10-18T23:59:59Z"))).isTrue();
    }
    @Test void invalidWindowsAndMissingConfigurationFailClearly() {
        assertThatIllegalStateException().isThrownBy(() -> new TradingRules.MarketWindow(ZoneOffset.UTC, Set.of(), true, null, null));
        for (LocalTime close : new LocalTime[]{null, LocalTime.of(9, 0), LocalTime.of(8, 0)})
            assertThatIllegalStateException().isThrownBy(() -> new TradingRules.MarketWindow(ZoneOffset.UTC, Set.of(DayOfWeek.MONDAY), false, LocalTime.of(9, 0), close));
        assertThatIllegalStateException().isThrownBy(() -> new TradingRules.MarketWindow(ZoneOffset.UTC, Set.of(DayOfWeek.MONDAY), false, null, LocalTime.NOON));
        Properties p = new Properties(); p.setProperty("trading.market.X.zone", "UTC");
        assertThatIllegalStateException().isThrownBy(() -> new TradingRules(p).marketWindow("X"));
        p.setProperty("trading.market.X.days", "MONDAY"); p.setProperty("trading.market.X.open", " ");
        assertThatIllegalStateException().isThrownBy(() -> new TradingRules(p).marketWindow("X"));
        var weekdaysOnly = new TradingRules.MarketWindow(ZoneOffset.UTC, Set.of(DayOfWeek.MONDAY), true, null, null);
        assertThat(weekdaysOnly.isOpen(NOW)).isFalse();
    }
}
