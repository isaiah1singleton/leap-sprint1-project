package com.neueda.leap.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.neueda.leap.models.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MarketDataService {
    public static final String DISCLAIMER = "Educational data. Not for investment use.";
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private final MarketSymbolService symbols;
    private final RestClient client;
    private final String apiKey;
    private final Map<String, CachedQuote> cache = new HashMap<>();

    public MarketDataService(MarketSymbolService symbols, @Qualifier("marketRestClient") RestClient client,
            @Value("${market.fauxnance.api-key}") String apiKey) {
        this.symbols = symbols;
        this.apiKey = apiKey;
        this.client = client;
    }

    public synchronized MarketQuoteResultDto quote(String input) {
        String symbol = symbols.requireSymbol(input).symbol();
        CachedQuote existing = cache.get(symbol);
        if (isFresh(existing)) return existing.result();
        requireKey();
        try {
            var response = client.get().uri("/quotes/{symbol}", symbol).retrieve().toEntity(QuoteEnvelope.class);
            QuoteEnvelope body = response.getBody();
            if (response.getStatusCode().value() == 202) throw pending();
            if (body == null || body.data() == null || body.meta() == null
                    || !validQuote(body.data(), symbol)) throw invalidResponse();
            var result = new MarketQuoteResultDto(symbol, body.data(), body.meta().source(), body.meta().stale(), null);
            cache.put(symbol, new CachedQuote(result, Instant.now()));
            return result;
        } catch (RestClientException exception) {
            throw translate(exception);
        }
    }

    /** One provider request for all cache misses; partial symbol errors remain visible. */
    public synchronized MarketQuotesDto quotes(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("Provide at least one symbol.");
        String[] requested = input.split(",", -1);
        if (requested.length > 25) throw new IllegalArgumentException("Request at most 25 symbols.");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String symbol : requested) {
            if (symbol.isBlank()) throw new IllegalArgumentException("Symbols cannot be empty.");
            normalized.add(symbols.requireSymbol(symbol).symbol());
        }
        List<String> missing = normalized.stream().filter(symbol -> !isFresh(cache.get(symbol))).toList();
        if (!missing.isEmpty()) {
            requireKey();
            try {
                var response = client.get().uri(uri -> uri.path("/quotes")
                        .queryParam("symbols", String.join(",", missing)).build())
                        .retrieve().toEntity(BatchEnvelope.class);
                BatchEnvelope body = response.getBody();
                if (response.getStatusCode().value() == 202) throw pending();
                if (body == null || body.data() == null || body.data().quotes() == null) throw invalidResponse();
                Map<String, MarketQuoteResultDto> received = new HashMap<>();
                for (MarketQuoteResultDto item : body.data().quotes()) {
                    if (item != null && missing.contains(item.symbol())) received.put(item.symbol(), item);
                }
                Instant now = Instant.now();
                for (String symbol : missing) {
                    MarketQuoteResultDto item = received.get(symbol);
                    if (item == null || (item.error() == null && !validQuote(item.quote(), symbol))) {
                        item = new MarketQuoteResultDto(symbol, null, null, false,
                                new MarketErrorDto("QUOTE_UNAVAILABLE", "The provider did not return a valid quote."));
                    }
                    cache.put(symbol, new CachedQuote(item, now));
                }
            } catch (RestClientException exception) {
                throw translate(exception);
            }
        }
        List<MarketQuoteResultDto> results = new ArrayList<>();
        for (String symbol : normalized) results.add(cache.get(symbol).result());
        return new MarketQuotesDto(results, DISCLAIMER);
    }

    private boolean isFresh(CachedQuote quote) {
        return quote != null && quote.cachedAt().plus(CACHE_TTL).isAfter(Instant.now());
    }

    private boolean validQuote(MarketQuoteDto quote, String symbol) {
        return quote != null && symbol.equals(quote.symbol()) && quote.price() != null && quote.price().signum() > 0
                && quote.bid() != null && quote.bid().signum() > 0 && quote.ask() != null && quote.ask().signum() > 0
                && quote.asOf() != null;
    }

    private void requireKey() {
        if (apiKey == null || apiKey.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Market data is not configured. Set FAUXNANCE_API_KEY on the backend.");
    }

    private ResponseStatusException translate(RestClientException exception) {
        if (exception instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            if (status == 404) return new ResponseStatusException(HttpStatus.NOT_FOUND, "The provider has no data for this symbol.");
            if (status == 429) return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Market data quota or rate limit reached. Please try again later.");
            if (status == 401 || status == 403) return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The market data provider rejected the backend API key. Check FAUXNANCE_API_KEY.");
        }
        // Do not expose upstream response bodies, credentials, or transport internals.
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Market data is temporarily unavailable. Please try again.");
    }

    private ResponseStatusException pending() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "This symbol's data is being prepared. Please try again shortly.");
    }

    private ResponseStatusException invalidResponse() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The provider returned an incomplete quote.");
    }

    private record CachedQuote(MarketQuoteResultDto result, Instant cachedAt) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuoteMeta(String source, boolean stale) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuoteEnvelope(MarketQuoteDto data, QuoteMeta meta) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BatchData(List<MarketQuoteResultDto> quotes) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BatchEnvelope(BatchData data) {}
}
