package com.neueda.leap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.entities.*;
import com.neueda.leap.enums.AssetClass;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.models.MarketQuoteDto;
import com.neueda.leap.models.MarketQuoteResultDto;
import com.neueda.leap.repository.*;
import com.neueda.leap.service.MarketDataService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-submission;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=false",
        "spring.sql.init.mode=always", "spring.sql.init.schema-locations=file:../database/transaction_schema.sql"
})
@AutoConfigureMockMvc
class OrderSubmissionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired InstrumentRepository instruments;
    @Autowired AccountRepository accounts;
    @Autowired HoldingRepository holdings;
    @Autowired OrderRepository orders;
    @Autowired OrderEventRepository events;
    @Autowired OutboxEventRepository outbox;
    @MockitoBean MarketDataService market;

    @Test
    void submitsAgainstPersistedCashAndHoldingsAndCancelsReservedSell() throws Exception {
        String registration = mvc.perform(post(ApiRoutes.AUTH_REGISTER).contentType(APPLICATION_JSON)
                .content("{\"name\":\"Trader\",\"email\":\"order@example.com\",\"password\":\"Trade123!\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(registration).path("accessToken").asText();
        String auth = "Bearer " + token;
        String accountBody = mvc.perform(post(ApiRoutes.ACCOUNTS).header("Authorization", auth)
                .contentType(APPLICATION_JSON).content("{\"accountName\":\"Main\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int accountId = mapper.readTree(accountBody).path("accountId").asInt();
        assertThat(mvc.perform(get(ApiRoutes.ACCOUNTS + "/" + accountId + "/balance")
                .header("Authorization", auth)).andReturn().getResponse().getContentAsString()).contains("0");

        Instrument bitcoin = new Instrument("CRYPTO", "X:BTC-USD", "Bitcoin", AssetClass.CRYPTO, Currency.USD);
        bitcoin.enableTrading();
        bitcoin = instruments.saveAndFlush(bitcoin);
        var quote = new MarketQuoteDto("X:BTC-USD", new BigDecimal("100"), new BigDecimal("99"),
                new BigDecimal("101"), new BigDecimal("200"), "USD", null, null, null,
                OffsetDateTime.now().minusMinutes(2).toString(), "open");
        when(market.quote("X:BTC-USD")).thenReturn(new MarketQuoteResultDto("X:BTC-USD", quote, "provider", false, null));

        String buy = "{\"accountId\":" + accountId + ",\"instrumentId\":" + bitcoin.getInstrumentId()
                + ",\"side\":\"BUY\",\"quantity\":1}";
        JsonNode rejected = mapper.readTree(mvc.perform(post(ApiRoutes.ORDERS).header("Authorization", auth)
                .contentType(APPLICATION_JSON).content(buy)).andExpect(status().isCreated())
                .andExpect(header().exists("Location")).andExpect(jsonPath("$.status").value("REJECTED"))
                .andReturn().getResponse().getContentAsString());
        assertThat(events.findByOrder_OrderIdOrderByOrderEventIdAsc(rejected.path("orderId").asInt()))
                .extracting(event -> event.getStatus().name()).containsExactly("SUBMITTED", "REJECTED");
        assertThat(outbox.count()).isZero();

        mvc.perform(post(ApiRoutes.ACCOUNTS + "/" + accountId + "/transfers").header("Authorization", auth)
                .contentType(APPLICATION_JSON).content("{\"type\":\"DEPOSIT\",\"amount\":500}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance.totalBalance").value(500));
        JsonNode accepted = mapper.readTree(mvc.perform(post(ApiRoutes.ORDERS).header("Authorization", auth)
                .contentType(APPLICATION_JSON).content(buy)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andReturn().getResponse().getContentAsString());
        assertThat(events.findByOrder_OrderIdOrderByOrderEventIdAsc(accepted.path("orderId").asInt()))
                .extracting(event -> event.getStatus().name()).containsExactly("SUBMITTED", "ACCEPTED");
        assertThat(outbox.count()).isEqualTo(1);

        // Acceptance does not spend cash. Simulated withdrawals do, immediately.
        mvc.perform(post(ApiRoutes.ACCOUNTS + "/" + accountId + "/transfers").header("Authorization", auth)
                .contentType(APPLICATION_JSON).content("{\"type\":\"WITHDRAW\",\"amount\":100}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance.totalBalance").value(400));

        Account account = accounts.findById(accountId).orElseThrow();
        Holding holding = new Holding(account, bitcoin);
        holding.addQuantity(new BigDecimal("2"));
        holdings.saveAndFlush(holding);
        String sell = buy.replace("BUY", "SELL");
        JsonNode sellOrder = mapper.readTree(mvc.perform(post(ApiRoutes.ORDERS).header("Authorization", auth)
                .contentType(APPLICATION_JSON).content(sell)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andReturn().getResponse().getContentAsString());
        assertThat(holdings.findById(new HoldingId(accountId, bitcoin.getInstrumentId())).orElseThrow()
                .getReservedQuantity()).isEqualByComparingTo("1");
        mvc.perform(post(ApiRoutes.ORDERS + "/" + sellOrder.path("orderId").asInt() + "/cancel")
                .header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(holdings.findById(new HoldingId(accountId, bitcoin.getInstrumentId())).orElseThrow()
                .getReservedQuantity()).isEqualByComparingTo("0");
        assertThat(orders.count()).isEqualTo(3);
        mvc.perform(post(ApiRoutes.ORDERS).header("Authorization", auth).contentType(APPLICATION_JSON)
                .content(buy.replace("\"quantity\":1", "\"quantity\":0"))).andExpect(status().isBadRequest());
        assertThat(orders.count()).isEqualTo(3);
    }
}
