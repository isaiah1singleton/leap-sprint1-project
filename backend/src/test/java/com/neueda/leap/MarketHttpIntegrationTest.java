package com.neueda.leap;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/** Uses real HTTP so Tomcat's error dispatch is covered as well as controller handling. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "spring.datasource.url=jdbc:h2:mem:market-http;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=false",
        "spring.sql.init.mode=always", "spring.sql.init.schema-locations=file:../database/transaction_schema.sql",
        "market.fauxnance.api-key="
})
class MarketHttpIntegrationTest {
    @LocalServerPort private int port;
    @Autowired private ObjectMapper mapper;
    @Autowired private com.neueda.leap.repository.InstrumentRepository instruments;
    private final HttpClient http = HttpClient.newHttpClient();
    private String token;

    @BeforeEach
    void signIn() {
        if (instruments.findAll().stream().noneMatch(instrument -> instrument.getSymbol().equals("AAPL"))) {
            instruments.save(new com.neueda.leap.entities.Instrument("US", "AAPL", "Apple Inc.",
                    com.neueda.leap.enums.AssetClass.EQUITY, com.neueda.leap.enums.Currency.USD));
        }
        token = com.neueda.leap.support.TestTokens.bearer(UUID.randomUUID() + "@example.test").substring(7);
    }

    @Test
    void freshSessionCanSearchAndProviderConfigurationFailureIsNotUnauthorized() throws Exception {
        var symbols = get(ApiRoutes.MARKET + ApiRoutes.MARKET_SYMBOLS + "?q=AAPL", token);
        assertThat(symbols.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(symbols.body()).isArray()).isTrue();
        var quote = get(ApiRoutes.MARKET + ApiRoutes.MARKET_QUOTES + "/AAPL", token);
        assertThat(quote.statusCode()).isEqualTo(503);
        assertThat(mapper.readTree(quote.body()).path("detail").asText()).contains("FAUXNANCE_API_KEY");
        assertThat(get(ApiRoutes.MARKET + ApiRoutes.MARKET_SYMBOLS, token).statusCode()).isEqualTo(200);
    }

    @Test
    void missingMarketRouteKeepsNotFoundInsteadOfBecomingAnExpiredSession() throws Exception {
        assertThat(get(ApiRoutes.MARKET + "/missing-route", token).statusCode()).isEqualTo(404);
    }

    @Test
    void missingQueryParameterKeepsBadRequestInsteadOfBecomingAnExpiredSession() throws Exception {
        assertThat(get(ApiRoutes.MARKET + ApiRoutes.MARKET_QUOTES, token).statusCode()).isEqualTo(400);
    }

    @Test
    void marketDataStillRequiresAValidSession() throws Exception {
        String route = ApiRoutes.MARKET + ApiRoutes.MARKET_SYMBOLS;
        assertThat(get(route, null).statusCode()).isEqualTo(401);
        assertThat(get(route, "invalid").statusCode()).isEqualTo(401);
        String expired = com.neueda.leap.support.TestTokens.token("expired@example.test", java.time.Instant.now().minusSeconds(120), com.neueda.leap.support.TestTokens.SECRET);
        assertThat(get(route, expired).statusCode()).isEqualTo(401);
        // A direct request to Spring's error endpoint must not bypass authentication.
        assertThat(get("/error", null).statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        return http.send(request(path, bearer).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String bearer) throws Exception {
        return http.send(request(path, bearer).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path, String bearer) {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return request;
    }
}
