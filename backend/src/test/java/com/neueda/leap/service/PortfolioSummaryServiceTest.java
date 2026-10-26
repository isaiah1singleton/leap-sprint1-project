package com.neueda.leap.service;

import com.neueda.leap.enums.Currency;
import com.neueda.leap.market.QuoteProviderException;
import com.neueda.leap.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortfolioSummaryServiceTest {
    private final PortfolioSnapshotReader reader = mock(PortfolioSnapshotReader.class);
    private final MarketDataService market = mock(MarketDataService.class);
    private final Instant now = Instant.parse("2026-10-15T18:00:00Z");
    private final PortfolioSummaryService service = new PortfolioSummaryService(reader, market,
            Clock.fixed(now, ZoneOffset.UTC));
    private PortfolioSnapshotReader.Snapshot snapshot;

    @BeforeEach void setup() {
        snapshot = new PortfolioSnapshotReader.Snapshot(1, new BigDecimal("800"),
                List.of(position(1, "AAPL", "2")), false, new BigDecimal("1000"), null, null, now);
        when(reader.read(1, 7)).thenAnswer(invocation -> snapshot);
    }

    @Test void valuesHeldUnitsAndCalculatesProfitAfterDeposits() {
        when(market.quotes("AAPL")).thenReturn(batch(quote("AAPL", "150", "USD", now.minusSeconds(600), true)));
        var result = service.summary(1, 7);
        assertThat(result.investedAssetsValue()).isEqualByComparingTo("300");
        assertThat(result.estimatedPortfolioValue()).isEqualByComparingTo("1100");
        assertThat(result.buyingPower()).isEqualByComparingTo("800");
        assertThat(result.lifetimeProfitLoss()).isEqualByComparingTo("100");
        assertThat(result.oldestQuoteAt()).isEqualTo(now.minusSeconds(600));
        assertThat(result.cashBalance()).isEqualByComparingTo("800");
        assertThat(result.holdings()).hasSize(1);
        assertThat(result.holdings().get(0).quotePrice()).isEqualByComparingTo("150");
        assertThat(result.holdings().get(0).estimatedValue()).isEqualByComparingTo("300");
        assertThat(result.valuationWarnings()).noneMatch(warning -> warning.contains("unavailable"));
    }

    @Test void reportsLossUsingNetFundingAfterWithdrawals() {
        snapshot = new PortfolioSnapshotReader.Snapshot(1, new BigDecimal("500"), snapshot.positions(),
                false, new BigDecimal("900"), null, null, now);
        when(market.quotes("AAPL")).thenReturn(batch(quote("AAPL", "150", "USD", now, false)));
        assertThat(service.summary(1, 7).lifetimeProfitLoss()).isEqualByComparingTo("-100");
    }

    @Test void cashOnlyAccountRequiresNoQuote() {
        snapshot = new PortfolioSnapshotReader.Snapshot(1, new BigDecimal("1000"), List.of(),
                false, new BigDecimal("1000"), null, null, now);
        var result = service.summary(1, 7);
        assertThat(result.estimatedPortfolioValue()).isEqualByComparingTo("1000");
        assertThat(result.investedAssetsValue()).isZero();
        assertThat(result.lifetimeProfitLoss()).isZero();
        verifyNoInteractions(market);
    }

    @Test void pendingOrderLocksBuyingPowerWithoutChangingValuation() {
        snapshot = new PortfolioSnapshotReader.Snapshot(1, snapshot.usdCash(), snapshot.positions(),
                false, snapshot.netFunding(), null, "Pending order", now);
        when(market.quotes("AAPL")).thenReturn(batch(quote("AAPL", "150", "USD", now, false)));
        var result = service.summary(1, 7);
        assertThat(result.buyingPower()).isNull();
        assertThat(result.buyingPowerUnavailableReason()).isEqualTo("Pending order");
        assertThat(result.estimatedPortfolioValue()).isEqualByComparingTo("1100");
    }

    @Test void unreconciledHistorySuppressesOnlyProfitLoss() {
        snapshot = new PortfolioSnapshotReader.Snapshot(1, snapshot.usdCash(), snapshot.positions(),
                false, snapshot.netFunding(), "History incomplete", null, now);
        when(market.quotes("AAPL")).thenReturn(batch(quote("AAPL", "150", "USD", now, false)));
        var result = service.summary(1, 7);
        assertThat(result.lifetimeProfitLoss()).isNull();
        assertThat(result.profitLossUnavailableReason()).isEqualTo("History incomplete");
        assertThat(result.estimatedPortfolioValue()).isEqualByComparingTo("1100");
    }

    @Test void badPricesLeavePortfolioTotalsUnavailableRatherThanZero() {
        var invalid = Arrays.asList(
                quote("AAPL", "0", "USD", now, false),
                quote("AAPL", "150", "GBP", now, false),
                quote("AAPL", "150", "USD", now.plusSeconds(1), false),
                quote("OTHER", "150", "USD", now, false),
                new MarketQuoteResultDto("AAPL", null, null, false, new MarketErrorDto("NO_DATA", "Unavailable")));
        for (var quote : invalid) {
            when(market.quotes("AAPL")).thenReturn(batch(quote));
            var result = service.summary(1, 7);
            assertThat(result.estimatedPortfolioValue()).isNull();
            assertThat(result.investedAssetsValue()).isNull();
            assertThat(result.lifetimeProfitLoss()).isNull();
            assertThat(result.buyingPower()).isEqualByComparingTo("800");
            assertThat(result.holdings().get(0).estimatedValue()).isNull();
        }
    }

    @Test void providerFailureKeepsBuyingPowerAndExplainsUnavailableTotals() {
        when(market.quotes("AAPL")).thenThrow(new QuoteProviderException(QuoteProviderException.Kind.RATE_LIMITED, "Quota"));
        var result = service.summary(1, 7);
        assertThat(result.estimatedPortfolioValue()).isNull();
        assertThat(result.valuationWarnings()).isNotEmpty();
        assertThat(result.buyingPower()).isEqualByComparingTo("800");
    }

    @Test void foreignCashOrPositionsAreNotTreatedAsUsd() {
        snapshot = new PortfolioSnapshotReader.Snapshot(1, snapshot.usdCash(), snapshot.positions(),
                true, snapshot.netFunding(), null, null, now);
        assertThat(service.summary(1, 7).estimatedPortfolioValue()).isNull();
        verifyNoInteractions(market);
    }

    @Test void checksOwnershipBeforeConsumingProviderQuota() {
        when(reader.read(1, 8)).thenThrow(new NoSuchElementException("Account not found."));
        assertThatThrownBy(() -> service.summary(1, 8)).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(market);
    }

    @Test void batchesHoldingsAndRoundsOnlyAfterAddingThem() {
        var positions = IntStream.range(0, 26).mapToObj(i -> position(i, "S" + i, "0.5")).toList();
        snapshot = new PortfolioSnapshotReader.Snapshot(1, BigDecimal.ZERO, positions,
                false, BigDecimal.ZERO, null, null, now);
        when(market.quotes(anyString())).thenAnswer(call -> {
            String[] symbols = ((String) call.getArgument(0)).split(",");
            assertThat(symbols.length).isLessThanOrEqualTo(25);
            return new MarketQuotesDto(Arrays.stream(symbols)
                    .map(symbol -> quote(symbol, "0.01", "USD", now, false)).toList(), "");
        });
        assertThat(service.summary(1, 7).investedAssetsValue()).isEqualByComparingTo("0.13");
        verify(market, times(2)).quotes(anyString());
    }

    private PortfolioSnapshotReader.Position position(int id, String symbol, String quantity) {
        BigDecimal units = new BigDecimal(quantity);
        return new PortfolioSnapshotReader.Position(id, symbol, symbol + " Inc.", Currency.USD,
                units, BigDecimal.ZERO, units);
    }
    private MarketQuotesDto batch(MarketQuoteResultDto result) { return new MarketQuotesDto(List.of(result), ""); }
    private MarketQuoteResultDto quote(String symbol, String price, String currency, Instant time, boolean stale) {
        var data = new MarketQuoteDto(symbol, new BigDecimal(price), null, null, null,
                currency, null, null, null, time.toString(), "REGULAR");
        return new MarketQuoteResultDto(symbol, data, "cache", stale, null);
    }
}
