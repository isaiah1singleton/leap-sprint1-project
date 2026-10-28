package com.neueda.leap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.models.MarketQuotesDto;
import com.neueda.leap.entities.Instrument;
import com.neueda.leap.enums.AssetClass;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.repository.InstrumentRepository;
import com.neueda.leap.service.MarketDataService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:market-auth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=false",
        "spring.sql.init.mode=always", "spring.sql.init.schema-locations=file:../database/transaction_schema.sql"
})
@AutoConfigureMockMvc
class MarketAuthenticationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private MarketDataService market;
    @Autowired private InstrumentRepository instruments;

    @Test
    void allMarketRoutesRejectMissingAndInvalidSessions() throws Exception {
        for (String route : List.of(ApiRoutes.MARKET + ApiRoutes.MARKET_SYMBOLS,
                ApiRoutes.MARKET + ApiRoutes.MARKET_QUOTES + "?symbols=AAPL",
                ApiRoutes.MARKET + ApiRoutes.MARKET_QUOTES + "/AAPL")) {
            mvc.perform(get(route)).andExpect(status().isUnauthorized());
            mvc.perform(get(route).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(market);
    }

    @Test
    void signedJwtAllowsMarketDataWhileExpiredAndWronglySignedTokensAreRejected() throws Exception {
        Instrument bitcoin = new Instrument("US", "X:BTC-USD", "Bitcoin", AssetClass.CRYPTO, Currency.USD);
        bitcoin.enableTrading();
        instruments.save(bitcoin);
        String token = com.neueda.leap.support.TestTokens.bearer("market-auth@example.com");
        mvc.perform(get(ApiRoutes.MARKET + ApiRoutes.MARKET_SYMBOLS).param("q", "btc")
                        .header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("X:BTC-USD"));

        when(market.quotes("AAPL")).thenReturn(new MarketQuotesDto(List.of(), MarketDataService.DISCLAIMER));
        mvc.perform(get(ApiRoutes.MARKET + ApiRoutes.MARKET_QUOTES).param("symbols", "AAPL")
                .header("Authorization", token)).andExpect(status().isOk());
        for (String invalid : List.of(
                com.neueda.leap.support.TestTokens.token("market-auth@example.com", java.time.Instant.now().minusSeconds(120), com.neueda.leap.support.TestTokens.SECRET),
                com.neueda.leap.support.TestTokens.token("market-auth@example.com", java.time.Instant.now().plusSeconds(120), "another-test-signing-secret-at-least-32-bytes"))) {
            mvc.perform(get(ApiRoutes.MARKET + ApiRoutes.MARKET_SYMBOLS).header("Authorization", "Bearer " + invalid))
                    .andExpect(status().isUnauthorized());
        }
    }
}
