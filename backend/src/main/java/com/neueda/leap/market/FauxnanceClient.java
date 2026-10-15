package com.neueda.leap.market;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.neueda.leap.models.MarketErrorDto;
import com.neueda.leap.models.MarketQuoteDto;
import com.neueda.leap.models.MarketQuoteResultDto;
import com.neueda.leap.service.MarketDataService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.neueda.leap.market.QuoteProviderException.Kind.*;

@Component
public class FauxnanceClient implements QuoteProvider {

    private final RestClient client;
    private final String apiKey;
    private final Clock clock;

    public FauxnanceClient(@Qualifier("marketRestClient") RestClient client, @Value("${market.fauxnance.api-key}") String apiKey, Clock clock) {
        this.client = client;
        this.apiKey = apiKey;
        this.clock = clock;
    }

    @Override
    public MarketQuoteResultDto fetchQuote(String symbol) {
        requireKey();

        try {
            var response = client.get()
                    .uri("/quotes/{symbol}", symbol)
                    .retrieve()
                    .toEntity(QuoteEnvelope.class);
            if (response.getStatusCode().value() == 202) {
                throw pending(response.getHeaders());
            }
           QuoteEnvelope body = response.getBody();

            if (body == null
                    || body.meta() == null
                    || body.meta().source() == null
                    || body.meta().stale() == null
                    || !validQuote(body.data(), symbol)) {
                throw invalidResponse();
            }
            return new MarketQuoteResultDto(
                    symbol,
                    body.data(),
                    body.meta().source(),
                    body.meta().stale(),
                    null
            );
        } catch (RestClientResponseException failure) {
            throw translate(failure);
        } catch (RestClientException failure) {
            throw new QuoteProviderException(UNAVAILABLE, "Market data is temporarily unavailable.");
        }
    }
    @Override
    public List<MarketQuoteResultDto> fetchQuotes(
            List<String> symbols
    ) {
        if (symbols == null || symbols.isEmpty() || symbols.size() > 25) {
            throw new IllegalArgumentException(
                    "Request between 1 and 25 symbols."
            );
        }

        requireKey();

        try {
            var response = client.get()
                    .uri(builder -> builder.path("/quotes")
                            .queryParam("symbols", String.join(",", symbols))
                            .build())
                    .retrieve()
                    .toEntity(BatchEnvelope.class);

            if (response.getStatusCode().value() == 202) {
                throw pending(response.getHeaders());
            }

            BatchEnvelope body = response.getBody();

            if (body == null
                    || body.data() == null
                    || body.data().quotes() == null) {
                throw invalidResponse();
            }

            Map<String, MarketQuoteResultDto> received = new HashMap<>();

            for (MarketQuoteResultDto item : body.data().quotes()) {
                if (item != null && symbols.contains(item.symbol())) {
                    received.put(item.symbol(), item);
                }
            }

            return symbols.stream()
                    .map(symbol -> normalize(symbol, received.get(symbol)))
                    .toList();
        } catch (RestClientResponseException failure) {
            throw translate(failure);
        } catch (RestClientException failure) {
            throw new QuoteProviderException(
                    UNAVAILABLE,
                    "Market data is temporarily unavailable."
            );
        }
    }
    private MarketQuoteResultDto normalize(
            String symbol,
            MarketQuoteResultDto item
    ) {
        if (item != null && item.error() != null) {
            return item;
        }

        if (item == null
                || item.source() == null
                || !validQuote(item.quote(), symbol)) {
            return new MarketQuoteResultDto(
                    symbol,
                    null,
                    null,
                    false,
                    new MarketErrorDto(
                            "QUOTE_UNAVAILABLE",
                            "The provider did not return a valid quote."
                    )
            );
        }

        return item;
    }
    private boolean validQuote(MarketQuoteDto quote, String symbol) {
        if (quote == null
                || !symbol.equals(quote.symbol())
                || !positive(quote.price())
                || !positive(quote.bid())
                || !positive(quote.ask())
                || quote.currency() == null
                || quote.currency().isBlank()
                || quote.asOf() == null) {
            return false;
        }

        try {
            OffsetDateTime.parse(quote.asOf());
            return true;
        } catch (DateTimeParseException failure) {
            return false;
        }
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private void requireKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new QuoteProviderException(
                    CONFIGURATION,
                    "Market data is not configured. "
                            + "Set FAUXNANCE_API_KEY on the backend."
            );
        }
    }

    private QuoteProviderException translate(
            RestClientResponseException failure
    ) {
        Instant retryAt = parseRetryAt(failure.getResponseHeaders());

        return switch (failure.getStatusCode().value()) {
            case 401, 403 -> new QuoteProviderException(
                    CONFIGURATION,
                    "The market data provider rejected the backend API key."
            );

            case 404 -> new QuoteProviderException(
                    NOT_FOUND,
                    "The provider has no data for this symbol."
            );

            case 429 -> new QuoteProviderException(
                    RATE_LIMITED,
                    "Market data quota is exhausted. "
                            + "Requests will resume after the quota resets.",
                    retryAt != null ? retryAt : nextUtcMidnight()
            );

            default -> new QuoteProviderException(
                    UNAVAILABLE,
                    "Market data is temporarily unavailable.",
                    retryAt
            );
        };
    }

    private QuoteProviderException pending(HttpHeaders headers) {
        return new QuoteProviderException(
                UNAVAILABLE,
                "The provider is preparing this symbol's data.",
                parseRetryAt(headers)
        );
    }

    private QuoteProviderException invalidResponse() {
        return new QuoteProviderException(
                INVALID_RESPONSE,
                "The provider returned an incomplete or invalid quote."
        );
    }

    private Instant parseRetryAt(HttpHeaders headers) {
        String value = headers == null
                ? null
                : headers.getFirst(HttpHeaders.RETRY_AFTER);

        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            long seconds = Long.parseLong(value.trim());
            return clock.instant().plusSeconds(Math.max(0, seconds));
        } catch (NumberFormatException failure) {
            try {
                return ZonedDateTime.parse(
                        value.trim(),
                        DateTimeFormatter.RFC_1123_DATE_TIME
                ).toInstant();
            } catch (DateTimeParseException invalidDate) {
                return null;
            }
        }
    }

    private Instant nextUtcMidnight() {
        return clock.instant()
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuoteMeta(String source, Boolean stale) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuoteEnvelope(MarketQuoteDto data, QuoteMeta meta) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BatchData(List<MarketQuoteResultDto> quotes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BatchEnvelope(BatchData data) {
    }
}
