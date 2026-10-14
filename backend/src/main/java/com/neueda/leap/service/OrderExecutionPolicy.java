package com.neueda.leap.service;

import com.neueda.leap.entities.Instrument;
import com.neueda.leap.entities.Money;
import com.neueda.leap.entities.Order;
import com.neueda.leap.entities.QuoteSnapshot;
import com.neueda.leap.models.MarketQuoteDto;
import com.neueda.leap.models.MarketQuoteResultDto;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataUnit;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

@Component
public class OrderExecutionPolicy {
    private static final Duration PENDING_TIMEOUT = Duration.ofMinutes(2);

    private final TradingRules tradingRules;

    public OrderExecutionPolicy(TradingRules tradingRules) {
        this.tradingRules = Objects.requireNonNull(tradingRules, "Trading rules are required.");
    }

    public Optional<String> expirationReason(Order order, OffsetDateTime acceptedAt, Instant now) {
        Objects.requireNonNull(order, "Order is required.");
        Objects.requireNonNull(
                acceptedAt,
                "Acceptance time is required."
        );
        Objects.requireNonNull(now, "Current time is required.");

        Instant acceptanceTime = acceptedAt.toInstant();
        if (acceptanceTime.isAfter(now)) {
            throw new IllegalStateException("Acceptance time cannot be in the future.");
        }

        Instant deadline = acceptanceTime.plus(PENDING_TIMEOUT);
        if (!now.isBefore(deadline)) {
            return Optional.of("The order exceeded its two-minute execution deadline.");
        }

        TradingRules.MarketWindow marketWindow = tradingRules
                .marketWindow(order.getInstrument().getMarket())
                .orElseThrow(() -> new IllegalStateException(("No market-hours policy defined for this order.")));

        if(!marketWindow.isOpen(now)) {
            return Optional.of("The market closed before the order could be executed.");
        }

        return Optional.empty();

    }

    public Optional<QuoteSnapshot> executionQuote(Order order, MarketQuoteResultDto result, Instant now) {
        Objects.requireNonNull(order, "Order is required.");
        Objects.requireNonNull(result, "Market quote result is required.");
        Objects.requireNonNull(now, "Current time is required.");

       if (result == null || result.error() != null || result.stale() || result.quote() ==null) {
           return Optional.empty();
       }

       Instrument instrument = order.getInstrument();
       MarketQuoteDto quote = result.quote();

       if (!instrument.getSymbol().equals(result.symbol()) || !instrument.getSymbol().equals(quote.symbol())) {
           return Optional.empty();
       }

       if (quote.currency() == null || !instrument.getQuoteCurrency().name().equalsIgnoreCase(quote.currency())) {
           return Optional.empty();
       }

       BigDecimal executionPrice = switch (order.getSide()) {
           case BUY -> quote.ask();
              case SELL -> quote.bid();

       };

       if (executionPrice == null || executionPrice.signum() <= 0 || quote.asOf() == null || quote.asOf().isBlank()) {
           return Optional.empty();
       }

       OffsetDateTime quoteAt;

       try {
           quoteAt = OffsetDateTime.parse(quote.asOf());
       } catch (DateTimeParseException e) {
           return Optional.empty();
       }

       QuoteSnapshot snapshot = new QuoteSnapshot(
               new Money(
                       executionPrice,
                       instrument.getQuoteCurrency()
               ),
               quoteAt
       );

       return isQuoteFresh(snapshot, now) ? Optional.of(snapshot) : Optional.empty();
    }

    public boolean isQuoteFresh(QuoteSnapshot snapshot, Instant now) {
        Objects.requireNonNull(now, "Current time is required.");

        if (snapshot == null) {
            return false;
        }

        Instant quoteTime = snapshot.quoteAt().toInstant();
        Instant oldestAllowed = now.minusSeconds(tradingRules.maxQuoteAgeSeconds());

        return !quoteTime.isAfter(now) && !quoteTime.isBefore(oldestAllowed);
    }

}
