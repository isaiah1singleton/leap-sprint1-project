package com.neueda.leap;

import com.neueda.leap.market.*;
import com.neueda.leap.service.*;
import com.neueda.leap.models.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class MarketDataServiceTest {
    private final MarketSymbolService symbols = mock(MarketSymbolService.class);
    private final QuoteProvider provider = mock(QuoteProvider.class);
    private final AtomicReference<Instant> time = new AtomicReference<>(NOW);
    private final Clock clock = new Clock() {
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        public Instant instant() { return time.get(); }
    };
    private final MarketDataService service = new MarketDataService(symbols, provider, clock);
    private void known(String... names) {
        for (String name : names) when(symbols.requireSymbol(name)).thenReturn(new MarketSymbolDto(2, name, name, "equity", "US", "USD"));
    }
    @Test void batchPreservesOrderDeduplicatesAndExpiresAtThirtySeconds() {
        known("AAPL", "MSFT"); var a = quote(); var m = quote("MSFT", "99", "101", AT.toString());
        when(provider.fetchQuotes(List.of("AAPL", "MSFT"))).thenReturn(List.of(m, a));
        assertThat(service.quotes("AAPL,MSFT,AAPL").quotes()).containsExactly(a, m);
        service.quotes("AAPL,MSFT"); verify(provider, times(1)).fetchQuotes(anyList());
        time.set(NOW.plusSeconds(29)); service.quotes("AAPL,MSFT"); verify(provider, times(1)).fetchQuotes(anyList());
        time.set(NOW.plusSeconds(30)); service.quotes("AAPL,MSFT"); verify(provider, times(2)).fetchQuotes(anyList());
    }
    @Test void singleAlwaysFetchesAndFallbackPreservesOriginalTimestamp() {
        known("AAPL"); var q = quote(); when(provider.fetchQuote("AAPL")).thenReturn(q);
        service.quote("AAPL"); service.quote("AAPL"); verify(provider, times(2)).fetchQuote("AAPL");
        when(provider.fetchQuote("AAPL")).thenThrow(new QuoteProviderException(QuoteProviderException.Kind.UNAVAILABLE, "down"));
        var backup = service.quote("AAPL"); assertThat(backup.source()).isEqualTo("local-cache"); assertThat(backup.stale()).isTrue();
        assertThat(backup.error().code()).isEqualTo("CACHED_FALLBACK"); assertThat(backup.quote().asOf()).isEqualTo(q.quote().asOf());
    }
    @Test void olderAndFailedResponsesNeverReplaceUsableBackup() {
        known("AAPL"); var newer = quote("AAPL", "99", "101", AT.toString());
        when(provider.fetchQuote("AAPL")).thenReturn(newer, quote()); service.quote("AAPL"); service.quote("AAPL");
        when(provider.fetchQuotes(anyList())).thenReturn(List.of(new MarketQuoteResultDto("AAPL", null, null, false, new MarketErrorDto("NO_DATA", "none"))));
        time.set(NOW.plusSeconds(30)); service.quotes("AAPL");
        when(provider.fetchQuote("AAPL")).thenThrow(new QuoteProviderException(QuoteProviderException.Kind.UNAVAILABLE, "down"));
        assertThat(service.quote("AAPL").quote()).isEqualTo(newer.quote());
    }
    @Test void missingResultsAndPartialErrorsRemainExplicitAndCacheable() {
        known("AAPL", "MSFT"); var error = new MarketQuoteResultDto("AAPL", null, null, false, new MarketErrorDto("NO_DATA", "none"));
        when(provider.fetchQuotes(anyList())).thenReturn(Arrays.asList(null, error, quote("OTHER", "1", "2", AT.toString())));
        var response = service.quotes("AAPL,MSFT"); assertThat(response.quotes().get(0)).isEqualTo(error);
        assertThat(response.quotes().get(1).error().code()).isEqualTo("QUOTE_UNAVAILABLE");
        assertThat(response.disclaimer()).isEqualTo(MarketDataService.DISCLAIMER);
        service.quotes("AAPL,MSFT"); verify(provider, times(1)).fetchQuotes(anyList());
    }
    @Test void invalidInputsNeverCallProvider() {
        known("AAPL");
        for (String input : new String[]{null, "", " ", ",", "AAPL,", String.join(",", Collections.nCopies(26, "AAPL"))})
            assertThatIllegalArgumentException().isThrownBy(() -> service.quotes(input));
        when(symbols.requireSymbol("UNKNOWN")).thenThrow(new NoSuchElementException("symbol"));
        assertThatThrownBy(() -> service.quotes("UNKNOWN")).isInstanceOf(NoSuchElementException.class); verifyNoInteractions(provider);
    }
    @Test void rateLimitBlocksProviderUntilReset() {
        known("AAPL"); var failure = new QuoteProviderException(QuoteProviderException.Kind.RATE_LIMITED, "quota", NOW.plusSeconds(60));
        when(provider.fetchQuote("AAPL")).thenThrow(failure).thenReturn(quote());
        assertThatThrownBy(() -> service.quote("AAPL")).isSameAs(failure);
        time.set(NOW.plusSeconds(59)); assertThatThrownBy(() -> service.quote("AAPL")).isInstanceOf(QuoteProviderException.class);
        verify(provider, times(1)).fetchQuote("AAPL"); time.set(NOW.plusSeconds(60)); assertThat(service.quote("AAPL").quote()).isNotNull();
    }
    @Test void unspecifiedQuotaResetUsesNextUtcMidnight() {
        known("AAPL"); when(provider.fetchQuote("AAPL")).thenThrow(new QuoteProviderException(QuoteProviderException.Kind.RATE_LIMITED, "quota"));
        assertThatThrownBy(() -> service.quote("AAPL")).isInstanceOf(QuoteProviderException.class);
        time.set(Instant.parse("2026-10-14T23:59:59Z"));
        assertThatThrownBy(() -> service.quote("AAPL")).satisfies(e -> assertThat(((QuoteProviderException)e).retryAt()).isEqualTo(Instant.parse("2026-10-15T00:00:00Z")));
        verify(provider, times(1)).fetchQuote("AAPL");
    }
    @Test void unexpectedFailureIsReleasedAndNextCallCanRetry() {
        known("AAPL"); when(provider.fetchQuote("AAPL")).thenThrow(new IllegalStateException("unexpected")).thenReturn(quote());
        assertThatIllegalStateException().isThrownBy(() -> service.quote("AAPL")); assertThat(service.quote("AAPL").quote()).isNotNull();
    }
    @Test void fatalProviderErrorIsPropagatedAndDoesNotLeaveInFlightEntryStuck() {
        known("AAPL"); AssertionError failure = new AssertionError("provider failure");
        when(provider.fetchQuote("AAPL")).thenThrow(failure).thenReturn(quote());
        assertThatThrownBy(() -> service.quote("AAPL")).isSameAs(failure);
        assertThat(service.quote("AAPL").quote()).isNotNull();
    }
    @Test void overlappingCallsShareInFlightResult() throws Exception {
        known("AAPL"); CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(provider.fetchQuote("AAPL")).thenAnswer(c -> { entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("provider not released"); return quote(); });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<MarketQuoteResultDto> first = pool.submit(() -> service.quote("AAPL")); assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            FutureTask<MarketQuoteResultDto> second = new FutureTask<>(() -> service.quote("AAPL"));
            Thread secondThread = new Thread(second, "second-quote-request"); secondThread.setDaemon(true); secondThread.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (secondThread.getState() != Thread.State.WAITING && secondThread.isAlive() && System.nanoTime() < deadline) Thread.onSpinWait();
            assertThat(secondThread.getState()).isEqualTo(Thread.State.WAITING);
            release.countDown(); assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(second.get(5, TimeUnit.SECONDS)); secondThread.join(5000);
            assertThat(secondThread.isAlive()).isFalse(); verify(provider, times(1)).fetchQuote("AAPL");
        } finally { release.countDown(); pool.shutdownNow(); }
    }
}
