package com.neueda.leap.service;

import com.neueda.leap.enums.Currency;
import com.neueda.leap.market.QuoteProviderException;
import com.neueda.leap.models.MarketQuoteResultDto;
import com.neueda.leap.models.PortfolioHoldingValue;
import com.neueda.leap.models.PortfolioSummaryResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class PortfolioSummaryService {
    private final PortfolioSnapshotReader snapshots;
    private final MarketDataService market;
    private final Clock clock;

    public PortfolioSummaryService(PortfolioSnapshotReader snapshots, MarketDataService market, Clock clock) {
        this.snapshots = snapshots;
        this.market = market;
        this.clock = clock;
    }

    public PortfolioSummaryResponse summary(Integer accountId, Integer clientId) {
        var account = snapshots.read(accountId, clientId); // Ownership is checked before using provider quota.
        Set<String> warnings = new LinkedHashSet<>();
        boolean usdOnly = !account.foreignBalance() && account.positions().stream()
                .allMatch(position -> position.currency() == Currency.USD);
        if (!usdOnly) warnings.add("Valuation is unavailable for accounts holding non-USD assets or cash.");

        Map<String, MarketQuoteResultDto> quotes = new HashMap<>();
        if (usdOnly) {
            var symbols = account.positions().stream().map(PortfolioSnapshotReader.Position::symbol).distinct().toList();
            // The market service reuses recent batch results, keeping within the provider's quota.
            for (int offset = 0; offset < symbols.size(); offset += 25) {
                var batch = symbols.subList(offset, Math.min(offset + 25, symbols.size()));
                try {
                    var response = market.quotes(String.join(",", batch));
                    if (response != null && response.quotes() != null) response.quotes().stream()
                            .filter(Objects::nonNull).forEach(result -> quotes.put(result.symbol(), result));
                } catch (QuoteProviderException | NoSuchElementException failure) {
                    warnings.add("Market prices are temporarily unavailable. Refresh to try again.");
                }
            }
        }

        BigDecimal assets = BigDecimal.ZERO;
        Instant oldestQuote = null;
        boolean complete = usdOnly;
        List<PortfolioHoldingValue> details = new ArrayList<>();
        for (var position : account.positions()) {
            var result = quotes.get(position.symbol());
            Instant quoteTime = usdOnly ? quoteTime(position.symbol(), result) : null;
            BigDecimal price = quoteTime == null ? null : result.quote().price();
            BigDecimal value = price == null ? null : position.quantity().multiply(price);
            details.add(new PortfolioHoldingValue(position.instrumentId(), position.symbol(), position.name(),
                    position.quantity(), position.reservedQuantity(), position.availableQuantity(),
                    price, dollars(value), quoteTime));
            if (quoteTime == null) {
                if (usdOnly) {
                    complete = false;
                    warnings.add("Price unavailable for " + position.symbol() + ". Totals cannot be calculated.");
                }
                continue;
            }
            assets = assets.add(value);
            if (oldestQuote == null || quoteTime.isBefore(oldestQuote)) oldestQuote = quoteTime;
            if (result.stale()) warnings.add("Valuation includes delayed provider quotes.");
            if ("synthetic".equals(result.source())) warnings.add("Valuation includes synthetic provider prices.");
        }

        BigDecimal portfolioValue = complete ? account.usdCash().add(assets) : null;
        String profitProblem = account.historyProblem() != null ? account.historyProblem()
                : !complete ? "A complete portfolio valuation is needed to calculate lifetime profit/loss." : null;
        return new PortfolioSummaryResponse(accountId, "USD", dollars(account.usdCash()), dollars(portfolioValue),
                account.buyingPowerProblem() == null ? dollars(account.usdCash()) : null,
                complete ? dollars(assets) : null,
                profitProblem == null ? dollars(portfolioValue.subtract(account.netFunding())) : null,
                account.buyingPowerProblem(), profitProblem, account.asOf(), oldestQuote,
                List.copyOf(warnings), List.copyOf(details));
    }

    private Instant quoteTime(String symbol, MarketQuoteResultDto result) {
        if (result == null || result.error() != null || result.quote() == null) return null;
        var quote = result.quote();
        if (!symbol.equals(result.symbol()) || !symbol.equals(quote.symbol()) ||
                !"USD".equalsIgnoreCase(quote.currency()) || quote.price() == null ||
                quote.price().signum() <= 0 || quote.asOf() == null) return null;
        try {
            Instant time = OffsetDateTime.parse(quote.asOf()).toInstant();
            return time.isAfter(clock.instant()) ? null : time;
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }

    private static BigDecimal dollars(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_EVEN);
    }
}
