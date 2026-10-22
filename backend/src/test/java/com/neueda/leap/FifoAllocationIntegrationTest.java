package com.neueda.leap;

import com.neueda.leap.entities.Account;
import com.neueda.leap.entities.Client;
import com.neueda.leap.entities.Instrument;
import com.neueda.leap.entities.Money;
import com.neueda.leap.entities.QuoteSnapshot;
import com.neueda.leap.enums.AssetClass;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.models.MarketQuoteResultDto;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.ClientRepository;
import com.neueda.leap.repository.InstrumentRepository;
import com.neueda.leap.service.OrderExecutionPolicy;
import com.neueda.leap.service.OrderExecutionService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static com.neueda.leap.service.OrderExecutionService.ExecutionResult.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fifo-allocation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:../database/transaction_schema.sql",
        "trading.execution.enabled=false"
})
class FifoAllocationIntegrationTest {
    @Autowired private OrderExecutionService execution;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ClientRepository clients;
    @Autowired private AccountRepository accounts;
    @Autowired private InstrumentRepository instruments;
    @MockitoBean private OrderExecutionPolicy policy;

    private Integer accountId;
    private Integer instrumentId;
    private Client client;
    private final OffsetDateTime now = OffsetDateTime.now().minusSeconds(1);

    @BeforeEach
    void setUp() {
        client = clients.save(new Client("FIFO test", UUID.randomUUID() + "@example.com", "hash"));
        accountId = accounts.save(new Account(client, "Trading")).getAccountId();
        instrumentId = instrument();
        jdbc.update("INSERT INTO account_balances VALUES (?, 'USD', 100, ?)", accountId, now);
        when(policy.expirationReason(any(), any(), any())).thenReturn(Optional.empty());
        when(policy.executionQuote(any(), any(), any())).thenReturn(Optional.of(
                new QuoteSnapshot(new Money(BigDecimal.TEN, Currency.USD), now)));
    }

    @Test
    void sellsConsumeOldestAvailableBuysAcrossLotsAndNeverReuseSoldQuantity() {
        // Insert out of chronological order; ties use fill ID.
        int newest = buy(accountId, instrumentId, "4", 10);
        int oldest = buy(accountId, instrumentId, "1.25", 30);
        int middle = buy(accountId, instrumentId, "2", 20);
        int tied = buy(accountId, instrumentId, "1", 20);
        int otherAccount = accounts.save(new Account(client, "Other")).getAccountId();
        buy(otherAccount, instrumentId, "100", 100);
        buy(accountId, instrument(), "100", 100);
        holding("8.25");

        int first = acceptedOrder("SELL", "0.5");
        assertThat(execute(first)).isEqualTo(FILLED);
        assertAllocations(first, List.of(oldest), "0.5");

        int second = acceptedOrder("SELL", "3.25");
        assertThat(execute(second)).isEqualTo(FILLED);
        assertAllocations(second, List.of(oldest, middle, tied), "0.75", "2", "0.5");

        int third = acceptedOrder("SELL", "1");
        assertThat(execute(third)).isEqualTo(FILLED);
        assertAllocations(third, List.of(newest, tied), "0.5", "0.5");
        assertThat(execute(third)).isEqualTo(SKIPPED);
        assertAllocations(third, List.of(newest, tied), "0.5", "0.5");
    }

    @Test
    void missingBuyHistoryRollsBackTheEntireSale() {
        holding("2");
        int sell = acceptedOrder("SELL", "1");
        assertThatThrownBy(() -> execute(sell))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Insufficient buy fills");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fills WHERE order_id=?", Integer.class, sell)).isZero();
        assertThat(jdbc.queryForObject("SELECT total_balance FROM account_balances WHERE account_id=?",
                BigDecimal.class, accountId)).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT total_quantity FROM account_holdings WHERE account_id=?",
                BigDecimal.class, accountId)).isEqualByComparingTo("2");
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM account_holdings WHERE account_id=?",
                BigDecimal.class, accountId)).isEqualByComparingTo("1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_events WHERE order_id=? AND status='FILLED'",
                Integer.class, sell)).isZero();
    }

    @Test
    void buysAndExpiredSellsDoNotCreateAllocations() {
        int buy = acceptedOrder("BUY", "1");
        assertThat(execute(buy)).isEqualTo(FILLED);
        int sell = acceptedOrder("SELL", "1");
        when(policy.expirationReason(any(), any(), any())).thenReturn(Optional.of("Expired"));
        assertThat(execute(sell)).isEqualTo(EXPIRED);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM fifo_allocations a JOIN fills f ON a.buy_fill_id=f.fill_id
                JOIN orders o ON f.order_id=o.order_id WHERE o.account_id=?
                """, Integer.class, accountId)).isZero();
    }

    private int instrument() {
        return instruments.save(new Instrument("NYSE", UUID.randomUUID().toString().substring(0, 8),
                "FIFO test", AssetClass.EQUITY, Currency.USD)).getInstrumentId();
    }

    private int order(int account, int instrument, String side, String quantity) {
        jdbc.update("""
                INSERT INTO orders (account_id,instrument_id,side,requested_quantity,
                submitted_quote_price,submitted_quote_at,submitted_at) VALUES (?,?,?,?,10,?,?)
                """, account, instrument, side, new BigDecimal(quantity), now.minusMinutes(5), now.minusMinutes(5));
        return jdbc.queryForObject("SELECT MAX(order_id) FROM orders WHERE account_id=?", Integer.class, account);
    }

    private int buy(int account, int instrument, String quantity, int secondsAgo) {
        int order = order(account, instrument, "BUY", quantity);
        jdbc.update("INSERT INTO fills (order_id,execution_price,execution_time) VALUES (?,10,?)",
                order, now.minusSeconds(secondsAgo));
        return jdbc.queryForObject("SELECT fill_id FROM fills WHERE order_id=?", Integer.class, order);
    }

    private void holding(String quantity) {
        jdbc.update("INSERT INTO account_holdings (account_id,instrument_id,total_quantity) VALUES (?,?,?)",
                accountId, instrumentId, new BigDecimal(quantity));
    }

    private int acceptedOrder(String side, String quantity) {
        int order = order(accountId, instrumentId, side, quantity);
        jdbc.update("""
                INSERT INTO order_events (order_id,status,decision_quote_price,decision_quote_at,occurred_at)
                VALUES (?,'ACCEPTED',10,?,?)
                """, order, now, now);
        if (side.equals("SELL")) {
            jdbc.update("UPDATE account_holdings SET reserved_quantity=reserved_quantity+? WHERE account_id=? AND instrument_id=?",
                    new BigDecimal(quantity), accountId, instrumentId);
        }
        return order;
    }

    private OrderExecutionService.ExecutionResult execute(int order) {
        return execution.execute(order, new MarketQuoteResultDto("TEST", null, null, false, null));
    }

    private void assertAllocations(int order, List<Integer> buyFills, String... quantities) {
        var rows = jdbc.queryForList("""
                SELECT a.buy_fill_id, a.allocated_quantity FROM fifo_allocations a
                JOIN fills f ON a.sell_fill_id=f.fill_id WHERE f.order_id=? ORDER BY a.buy_fill_id
                """, order);
        assertThat(rows).hasSize(buyFills.size());
        for (int i = 0; i < rows.size(); i++) {
            assertThat(((Number) rows.get(i).get("buy_fill_id")).intValue()).isEqualTo(buyFills.get(i));
            assertThat((BigDecimal) rows.get(i).get("allocated_quantity")).isEqualByComparingTo(quantities[i]);
        }
    }
}
