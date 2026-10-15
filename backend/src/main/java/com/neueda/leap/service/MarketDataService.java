package com.neueda.leap.service;

import com.neueda.leap.models.MarketErrorDto;
import com.neueda.leap.models.MarketQuoteResultDto;
import com.neueda.leap.models.MarketQuotesDto;
import com.neueda.leap.market.QuoteProvider;
import com.neueda.leap.market.QuoteProviderException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import static com.neueda.leap.market.QuoteProviderException.Kind.RATE_LIMITED;

@Service
public class MarketDataService {

    public static final String DISCLAIMER =
            "Educational data. Not for investment use.";

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final MarketSymbolService symbols;
    private final QuoteProvider provider;
    private final Clock clock;

    // Recent responses, including per-symbol errors, limit repeated batch calls.
    private final ConcurrentMap<String, CachedResult> recentResults =
            new ConcurrentHashMap<>();

    // Failed responses never overwrite a usable backup quote.
    private final ConcurrentMap<String, MarketQuoteResultDto> lastQuotes =
            new ConcurrentHashMap<>();

    private final ConcurrentMap<
            String, CompletableFuture<MarketQuoteResultDto>> inFlight =
            new ConcurrentHashMap<>();

    private final AtomicReference<Instant> quotaRetryAt =
            new AtomicReference<>();

    public MarketDataService(
            MarketSymbolService symbols,
            QuoteProvider provider,
            Clock clock
    ) {
        this.symbols = symbols;
        this.provider = provider;
        this.clock = clock;
    }

    public MarketQuoteResultDto quote(String input) {
        String symbol = symbols.requireSymbol(input).symbol();

        try {
            // Completed cached responses are not used on this path.
            return load(List.of(symbol), false).get(0);
        } catch (QuoteProviderException failure) {
            MarketQuoteResultDto backup = lastQuotes.get(symbol);

            if (backup == null) {
                throw failure;
            }

            return new MarketQuoteResultDto(
                    symbol,
                    backup.quote(),
                    "local-cache",
                    true,
                    new MarketErrorDto(
                            "CACHED_FALLBACK",
                            failure.getMessage()
                                    + " Showing a saved quote; trading is disabled."
                    )
            );
        }
    }

    public MarketQuotesDto quotes(String input) {
        return new MarketQuotesDto(
                load(normalizeSymbols(input), true),
                DISCLAIMER
        );
    }

    private List<String> normalizeSymbols(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException(
                    "Provide at least one symbol."
            );
        }

        String[] requested = input.split(",", -1);

        if (requested.length > 25) {
            throw new IllegalArgumentException(
                    "Request at most 25 symbols."
            );
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();

        for (String symbol : requested) {
            if (symbol.isBlank()) {
                throw new IllegalArgumentException(
                        "Symbols cannot be empty."
                );
            }

            normalized.add(symbols.requireSymbol(symbol).symbol());
        }

        return List.copyOf(normalized);
    }

    private List<MarketQuoteResultDto> load(
            List<String> requested,
            boolean allowRecentResults
    ) {
        Map<String, CompletableFuture<MarketQuoteResultDto>> pending =
                new LinkedHashMap<>();

        List<String> owned = new ArrayList<>();

        for (String symbol : requested) {
            CachedResult cached = recentResults.get(symbol);

            if (allowRecentResults && reusable(cached)) {
                pending.put(
                        symbol,
                        CompletableFuture.completedFuture(cached.result())
                );
                continue;
            }

            CompletableFuture<MarketQuoteResultDto> created =
                    new CompletableFuture<>();

            CompletableFuture<MarketQuoteResultDto> existing =
                    inFlight.putIfAbsent(symbol, created);

            pending.put(symbol, existing == null ? created : existing);

            if (existing == null) {
                owned.add(symbol);
            }
        }

        if (!owned.isEmpty()) {
            fetchOwned(owned, pending, allowRecentResults);
        }

        return requested.stream()
                .map(symbol -> await(pending.get(symbol)))
                .toList();
    }

    private void fetchOwned(
            List<String> owned,
            Map<String, CompletableFuture<MarketQuoteResultDto>> pending,
            boolean batchRequest
    ) {
        try {
            requireQuotaAvailable();

            List<MarketQuoteResultDto> results = batchRequest
                    ? provider.fetchQuotes(List.copyOf(owned))
                    : List.of(provider.fetchQuote(owned.get(0)));

            Map<String, MarketQuoteResultDto> bySymbol = new HashMap<>();

            for (MarketQuoteResultDto result : results) {
                if (result != null && owned.contains(result.symbol())) {
                    bySymbol.put(result.symbol(), result);
                }
            }

            for (String symbol : owned) {
                MarketQuoteResultDto result = bySymbol.get(symbol);

                if (result == null) {
                    result = new MarketQuoteResultDto(
                            symbol,
                            null,
                            null,
                            false,
                            new MarketErrorDto(
                                    "QUOTE_UNAVAILABLE",
                                    "The provider returned no result for this symbol."
                            )
                    );
                }

                remember(symbol, result);
                pending.get(symbol).complete(result);
            }
        } catch (QuoteProviderException failure) {
            if (failure.kind() == RATE_LIMITED) {
                rememberQuotaFailure(failure);
            }

            completeExceptionally(owned, pending, failure);
        } catch (RuntimeException | Error failure) {
            // Ensure other threads waiting for these symbols are released.
            completeExceptionally(owned, pending, failure);
        } finally {
            for (String symbol : owned) {
                inFlight.remove(symbol, pending.get(symbol));
            }
        }
    }

    private void remember(
            String symbol,
            MarketQuoteResultDto result
    ) {
        recentResults.put(
                symbol,
                new CachedResult(result, clock.instant())
        );

        if (result.error() != null || result.quote() == null) {
            return;
        }

        lastQuotes.compute(symbol, (key, previous) -> {
            if (previous == null) {
                return result;
            }

            Instant previousTime =
                    OffsetDateTime.parse(previous.quote().asOf()).toInstant();

            Instant incomingTime =
                    OffsetDateTime.parse(result.quote().asOf()).toInstant();

            return incomingTime.isBefore(previousTime) ? previous : result;
        });
    }

    private boolean reusable(CachedResult cached) {
        return cached != null
                && cached.fetchedAt().plus(CACHE_TTL)
                .isAfter(clock.instant());
    }

    private void requireQuotaAvailable() {
        Instant retryAt = quotaRetryAt.get();

        if (retryAt != null && retryAt.isAfter(clock.instant())) {
            throw new QuoteProviderException(
                    RATE_LIMITED,
                    "Market data quota is exhausted. "
                            + "Requests will resume after the quota resets.",
                    retryAt
            );
        }
    }

    private void rememberQuotaFailure(QuoteProviderException failure) {
        Instant candidate = failure.retryAt();

        if (candidate == null) {
            candidate = clock.instant()
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                    .plusDays(1)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant();
        }

        Instant retryAt = candidate;

        quotaRetryAt.accumulateAndGet(
                retryAt,
                (existing, incoming) ->
                        existing == null || incoming.isAfter(existing)
                                ? incoming
                                : existing
        );
    }

    private void completeExceptionally(
            List<String> owned,
            Map<String, CompletableFuture<MarketQuoteResultDto>> pending,
            Throwable failure
    ) {
        for (String symbol : owned) {
            pending.get(symbol).completeExceptionally(failure);
        }
    }

    private MarketQuoteResultDto await(
            CompletableFuture<MarketQuoteResultDto> future
    ) {
        try {
            return future.join();
        } catch (CompletionException failure) {
            Throwable cause = failure.getCause();

            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }

            if (cause instanceof Error error) {
                throw error;
            }

            throw new IllegalStateException(
                    "Quote retrieval failed.",
                    cause
            );
        }
    }

    private record CachedResult(
            MarketQuoteResultDto result,
            Instant fetchedAt
    ) {
    }
}
