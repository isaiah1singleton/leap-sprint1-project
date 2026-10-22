package com.neueda.leap;

import com.neueda.leap.entities.Account;
import com.neueda.leap.entities.Client;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.ClientRepository;
import com.neueda.leap.service.MarketDataService;
import com.neueda.leap.service.TokenService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.datasource.url=jdbc:h2:mem:portfolio-summary;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:../database/transaction_schema.sql",
        "trading.execution.enabled=false"
})
@AutoConfigureMockMvc
class PortfolioSummaryIntegrationTest {
    @Autowired private ClientRepository clients;
    @Autowired private AccountRepository accounts;
    @Autowired private TokenService tokens;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @MockitoBean private MarketDataService market;

    @Test void cashOnlyPortfolioUsesTheRecordedDepositAndRequiresAuthentication() throws Exception {
        var owner = createAccount();
        fund(owner.accountId(), "1000.00");

        mvc.perform(get(path(owner.accountId())))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(path(owner.accountId())).header("Authorization", bearer(owner.client())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.estimatedPortfolioValue").value(1000.0))
                .andExpect(jsonPath("$.buyingPower").value(1000.0))
                .andExpect(jsonPath("$.investedAssetsValue").value(0.0))
                .andExpect(jsonPath("$.lifetimeProfitLoss").value(0.0))
                .andExpect(jsonPath("$.holdings.length()").value(0));
        verifyNoInteractions(market);
    }

    @Test void anotherClientCannotValueAnAccountOrConsumeProviderQuota() throws Exception {
        var owner = createAccount();
        var other = createAccount();
        mvc.perform(get(path(owner.accountId())).header("Authorization", bearer(other.client())))
                .andExpect(status().isNotFound());
        verifyNoInteractions(market);
    }

    @Test void pendingAcceptedOrderLocksBuyingPowerAndDoesNotHideCashValue() throws Exception {
        var owner = createAccount();
        fund(owner.accountId(), "1000.00");
        String symbol = "P" + owner.accountId();
        jdbc.update("INSERT INTO instruments (market,symbol,asset_class,instrument_name,is_tradable,quote_currency) " +
                "VALUES ('NASDAQ',?,'EQUITY',?,TRUE,'USD')", symbol, symbol);
        Integer instrumentId = jdbc.queryForObject("SELECT instrument_id FROM instruments WHERE symbol=?", Integer.class, symbol);
        Instant quoteAt = Instant.now().minusSeconds(60);
        jdbc.update("INSERT INTO orders (account_id,instrument_id,side,requested_quantity,submitted_quote_price,submitted_quote_at) " +
                "VALUES (?,?,'BUY',1,10,?)", owner.accountId(), instrumentId, quoteAt);
        Integer orderId = jdbc.queryForObject("SELECT MAX(order_id) FROM orders WHERE account_id=?", Integer.class, owner.accountId());
        jdbc.update("INSERT INTO order_events (order_id,status,decision_quote_price,decision_quote_at) " +
                "VALUES (?,'ACCEPTED',10,?)", orderId, quoteAt);

        mvc.perform(get(path(owner.accountId())).header("Authorization", bearer(owner.client())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedPortfolioValue").value(1000.0))
                .andExpect(jsonPath("$.buyingPower").doesNotExist())
                .andExpect(jsonPath("$.buyingPowerUnavailableReason").isNotEmpty());
        verifyNoInteractions(market);
    }

    @Test void unreconciledBalanceDoesNotInventLifetimeProfit() throws Exception {
        var owner = createAccount();
        jdbc.update("INSERT INTO account_balances (account_id,currency,total_balance,updated_at) VALUES (?,'USD',100,?)",
                owner.accountId(), Instant.now());
        mvc.perform(get(path(owner.accountId())).header("Authorization", bearer(owner.client())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedPortfolioValue").value(100.0))
                .andExpect(jsonPath("$.lifetimeProfitLoss").doesNotExist())
                .andExpect(jsonPath("$.profitLossUnavailableReason").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cash_movements WHERE account_id=?", Integer.class,
                owner.accountId())).isZero();
    }

    @Test void zeroQuantityHoldingDisappearsFromCurrentPortfolioButOrderHistoryRemains() throws Exception {
        var owner = createAccount();
        String symbol = "Z" + owner.accountId();
        jdbc.update("INSERT INTO instruments (market,symbol,asset_class,instrument_name,is_tradable,quote_currency) " +
                "VALUES ('NASDAQ',?,'EQUITY',?,TRUE,'USD')", symbol, symbol);
        Integer instrumentId = jdbc.queryForObject("SELECT instrument_id FROM instruments WHERE symbol=?", Integer.class, symbol);
        jdbc.update("INSERT INTO account_holdings (account_id,instrument_id,total_quantity,reserved_quantity) VALUES (?,?,0,0)",
                owner.accountId(), instrumentId);
        Instant quoteAt = Instant.now().minusSeconds(60);
        jdbc.update("INSERT INTO orders (account_id,instrument_id,side,requested_quantity,submitted_quote_price,submitted_quote_at) " +
                "VALUES (?,?,'SELL',1,10,?)", owner.accountId(), instrumentId, quoteAt);
        Integer orderId = jdbc.queryForObject("SELECT MAX(order_id) FROM orders WHERE account_id=?", Integer.class, owner.accountId());
        jdbc.update("INSERT INTO order_events (order_id,status,reason) VALUES (?,'REJECTED','No shares available')", orderId);
        String authorization = bearer(owner.client());

        mvc.perform(get("/api/accounts/" + owner.accountId() + "/holdings")
                        .header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(path(owner.accountId())).header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.holdings.length()").value(0));
        mvc.perform(get("/api/orders").param("accountId", owner.accountId().toString())
                        .header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].symbol").value(symbol));
    }

    private AccountOwner createAccount() {
        var client = clients.save(new Client("Portfolio test", UUID.randomUUID() + "@example.com", "hash"));
        var account = accounts.save(new Account(client, "Portfolio"));
        return new AccountOwner(client, account.getAccountId());
    }

    private void fund(Integer accountId, String amount) {
        jdbc.update("INSERT INTO account_balances (account_id,currency,total_balance,updated_at) VALUES (?,'USD',?,?)",
                accountId, new BigDecimal(amount), Instant.now());
        jdbc.update("INSERT INTO cash_movements (account_id,amount,movement_type,currency) VALUES (?,?,'DEPOSIT','USD')",
                accountId, new BigDecimal(amount));
    }

    private String bearer(Client client) { return "Bearer " + tokens.issueToken(client).accessToken(); }
    private String path(Integer accountId) { return "/api/accounts/" + accountId + "/portfolio-summary"; }
    private record AccountOwner(Client client, Integer accountId) { }
}
