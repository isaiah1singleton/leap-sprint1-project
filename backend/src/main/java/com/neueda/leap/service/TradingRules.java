package com.neueda.leap.service;

import com.neueda.leap.entities.Instrument;
import com.neueda.leap.enums.AssetClass;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TradingRules {
    private final Properties values;

    public TradingRules() {
        this(loadRules());
    }

    public TradingRules(Properties values) {
        this.values = values;
    }

    public long maxQuoteAgeSeconds() {
        long age = Long.parseLong(required("trading.max-quote-age-seconds"));
        if (age <= 0) throw new IllegalStateException("Quote age must be positive.");
        return age;
    }

    public boolean supports(AssetClass assetClass) {
        return assetClass != null
                && Boolean.parseBoolean(values.getProperty("trading.asset." + assetClass + ".supported", "false"));
    }

    public int allowedQuantityScale(Instrument instrument) {
        String override = values.getProperty("trading.instrument." + instrument.getInstrumentId() + ".quantity-scale");
        int scale = Integer.parseInt(override == null
                ? required("trading.asset." + instrument.getAssetClass() + ".quantity-scale") : override);
        if (scale < 0) throw new IllegalStateException("Quantity scale cannot be negative.");
        return scale;
    }

    public BigDecimal fundingMultiplier(AssetClass assetClass) {
        String prefix = "trading.asset." + assetClass + ".";
        BigDecimal fee = new BigDecimal(required(prefix + "fee-rate"));
        BigDecimal tax = new BigDecimal(required(prefix + "tax-rate"));
        if (fee.signum() < 0 || tax.signum() < 0) {
            throw new IllegalStateException("Fee and tax rates cannot be negative.");
        }
        return BigDecimal.ONE.add(fee).add(tax);
    }

    public boolean isRestricted(Instrument instrument) {
        return Boolean.parseBoolean(values.getProperty(
                "trading.instrument." + instrument.getInstrumentId() + ".restricted", "false"));
    }

    public Optional<MarketWindow> marketWindow(String market) {
        if (market == null) return Optional.empty();
        String prefix = "trading.market." + market.trim().toUpperCase(Locale.ROOT) + ".";
        String zone = values.getProperty(prefix + "zone");
        if (zone == null) return Optional.empty();
        Set<DayOfWeek> days = Arrays.stream(required(prefix + "days").split(","))
                .map(String::trim).map(DayOfWeek::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
        boolean allDay = Boolean.parseBoolean(values.getProperty(prefix + "all-day", "false"));
        return Optional.of(new MarketWindow(ZoneId.of(zone), days, allDay,
                allDay ? null : LocalTime.parse(required(prefix + "open")),
                allDay ? null : LocalTime.parse(required(prefix + "close"))));
    }

    private String required(String key) {
        String value = values.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing trading rule: " + key);
        }
        return value.trim();
    }

    private static Properties loadRules() {
        Properties properties = new Properties();
        try (InputStream stream = TradingRules.class.getResourceAsStream("/trading-rules.properties")) {
            if (stream == null) throw new IllegalStateException("trading-rules.properties is missing.");
            properties.load(stream);
            return properties;
        } catch (IOException error) {
            throw new IllegalStateException("Unable to load trading rules.", error);
        }
    }

    public record MarketWindow(ZoneId zone, Set<DayOfWeek> days, boolean allDay,
                               LocalTime open, LocalTime close) {
        public MarketWindow {
            if (days.isEmpty() || (!allDay && (open == null || close == null || !open.isBefore(close)))) {
                throw new IllegalStateException("Market window must have days and valid hours.");
            }
        }

        public boolean isOpen(Instant at) {
            ZonedDateTime local = at.atZone(zone);
            if (!days.contains(local.getDayOfWeek())) return false;
            if (allDay) return true;
            LocalTime time = local.toLocalTime();
            return !time.isBefore(open) && time.isBefore(close);
        }
    }
}
