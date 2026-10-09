package com.neueda.leap;

import com.neueda.leap.entities.Instrument;
import com.neueda.leap.enums.AssetClass;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.repository.InstrumentRepository;
import com.neueda.leap.service.MarketDataService;
import com.neueda.leap.service.MarketSymbolService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarketDataServiceTest {
    private RestClient client;
    private MarketSymbolService symbols;
    private MarketDataService service;
    private int responseStatus;
    private String responseBody;
    private final List<String> paths = new ArrayList<>();
    private final List<String> keys = new ArrayList<>();
    private static final String QUOTE = """
        {"symbol":"AAPL","price":210.5,"bid":210.4,"ask":210.6,"spreadBps":9.5,
         "currency":"USD","change":-1.5,"changePercent":-0.71,"previousClose":212,
         "asOf":"2026-10-07T12:00:00Z","marketState":"open"}
        """;

    @BeforeEach
    void setUp() throws Exception {
        responseStatus = 200;
        responseBody = "{\"data\":" + QUOTE + ",\"meta\":{\"source\":\"cache\",\"stale\":false}}";
        InstrumentRepository instruments = mock(InstrumentRepository.class);
        Instrument apple = instrument("AAPL", "Apple Inc.", AssetClass.EQUITY);
        Instrument microsoft = instrument("MSFT", "Microsoft Corporation", AssetClass.EQUITY);
        Instrument bitcoin = instrument("X:BTC-USD", "Bitcoin", AssetClass.CRYPTO);
        when(instruments.findAllByOrderByMarketAscSymbolAsc()).thenReturn(List.of(apple, microsoft, bitcoin));
        when(instruments.findFirstBySymbolIgnoreCase("AAPL")).thenReturn(java.util.Optional.of(apple));
        when(instruments.findFirstBySymbolIgnoreCase("MSFT")).thenReturn(java.util.Optional.of(microsoft));
        when(instruments.findFirstBySymbolIgnoreCase("INVALID")).thenReturn(java.util.Optional.empty());
        symbols = new MarketSymbolService(instruments);
        var builder = RestClient.builder().baseUrl("http://provider.test/v1").defaultHeader("X-Api-Key", "test-server-key");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.manyTimes(), request -> {}).andRespond(request -> {
            paths.add(request.getURI().toString().replace("http://provider.test", ""));
            keys.add(request.getHeaders().getFirst("X-Api-Key"));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            var response = new MockClientHttpResponse(bytes, HttpStatusCode.valueOf(responseStatus));
            response.getHeaders().add("Content-Type", "application/json");
            return response;
        });
        client = builder.build();
        service = new MarketDataService(symbols, client, "test-server-key");
    }

    @Test
    void usesServerKeyCanonicalSymbolAndCachesSingleQuote() {
        var first = service.quote(" aapl ");
        assertThat(first.quote().price()).isEqualByComparingTo("210.5");
        assertThat(first.quote().changePercent()).isNegative();
        assertThat(service.quote("AAPL")).isEqualTo(first);
        assertThat(paths).containsExactly("/v1/quotes/AAPL");
        assertThat(keys).containsExactly("test-server-key");
    }

    @Test
    void batchPreservesPartialErrorsOrderAndSharesCacheWithDetail() {
        responseBody = "{\"data\":{\"quotes\":[{\"symbol\":\"AAPL\",\"source\":\"synthetic\",\"stale\":true,\"quote\":"
                + QUOTE + "},{\"symbol\":\"MSFT\",\"error\":{\"code\":\"UPSTREAM_UNAVAILABLE\",\"message\":\"Try later.\"}}]}}";
        var result = service.quotes("aapl,MSFT,AAPL");
        assertThat(result.quotes()).hasSize(2);
        assertThat(result.quotes().get(0).stale()).isTrue();
        assertThat(result.quotes().get(0).source()).isEqualTo("synthetic");
        assertThat(result.quotes().get(1).error().code()).isEqualTo("UPSTREAM_UNAVAILABLE");
        assertThat(service.quote("AAPL")).isEqualTo(result.quotes().get(0));
        assertThat(service.quotes("MSFT,AAPL").quotes().get(0).symbol()).isEqualTo("MSFT");
        assertThat(paths).hasSize(1);
        assertThat(paths.get(0)).contains("symbols=AAPL,MSFT");
    }

    @Test
    void validatesCatalogueAndBatchLimitBeforeCallingProvider() {
        assertThatThrownBy(() -> service.quotes(String.join(",", java.util.Collections.nCopies(26, "AAPL"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.quotes("AAPL,,MSFT")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.quote("INVALID")).isInstanceOf(NoSuchElementException.class);
        assertThat(paths).isEmpty();
        assertThat(symbols.search("btc", 15)).anyMatch(item -> item.symbol().equals("X:BTC-USD"));
        assertThat(symbols.search("apple", 15)).anyMatch(item -> item.symbol().equals("AAPL"));
        assertThat(symbols.search("", 1000)).hasSize(3);
    }

    private static Instrument instrument(String symbol, String name, AssetClass assetClass) {
        Instrument instrument = new Instrument("US", symbol, name, assetClass, Currency.USD);
        instrument.enableTrading();
        return instrument;
    }

    @Test
    void missingKeyNeverContactsProvider() {
        var unconfigured = new MarketDataService(symbols, client, "");
        assertThatThrownBy(() -> unconfigured.quote("AAPL")).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(503));
        assertThat(paths).isEmpty();
    }

    @Test
    void providerAuthenticationDoesNotMasqueradeAsUserSessionFailure() {
        responseStatus = 403;
        responseBody = "{\"message\":\"secret provider diagnostic\"}";
        assertThatThrownBy(() -> service.quote("AAPL")).isInstanceOfSatisfying(ResponseStatusException.class, error -> {
            assertThat(error.getStatusCode().value()).isEqualTo(503);
            assertThat(error.getReason()).doesNotContain("secret provider diagnostic");
        });
    }

    @Test
    void rateLimitAndBackfillReturnRecoverableErrors() {
        responseStatus = 429;
        assertThatThrownBy(() -> service.quote("AAPL")).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(429));
        responseStatus = 202;
        responseBody = "{}";
        assertThatThrownBy(() -> service.quote("AAPL")).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(503));
    }

    @Test
    void malformedQuoteDoesNotBecomeATradablePrice() {
        responseBody = "{\"data\":{\"symbol\":\"AAPL\",\"price\":0},\"meta\":{\"source\":\"cache\"}}";
        assertThatThrownBy(() -> service.quote("AAPL")).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(502));
        responseBody = "{\"data\":{\"quotes\":[]}}";
        assertThat(service.quotes("MSFT").quotes().get(0).error().code()).isEqualTo("QUOTE_UNAVAILABLE");
    }

}
