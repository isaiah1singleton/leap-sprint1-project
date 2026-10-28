package com.neueda.leap;

import com.neueda.leap.entities.*;
import com.neueda.leap.enums.*;
import com.neueda.leap.models.*;
import com.neueda.leap.repository.*;
import com.neueda.leap.service.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real PostgreSQL transactions; mocks only the external quote decision and injected failures. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.jpa.hibernate.ddl-auto=validate", "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=file:../database/transaction_schema.sql"
})
class TradingPostgresIT {
    private static PostgreSQLContainer<?> postgres;
    private static String externalUrl;
    private static String externalUser;
    private static String externalPassword;
    private static final String schema = "leap_suite_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("LEAP_TEST_JDBC_URL");
        if (url == null || url.isBlank()) {
            postgres = new PostgreSQLContainer<>("postgres:18").withDatabaseName("leap_test");
            postgres.start();
            properties.add("spring.datasource.url", postgres::getJdbcUrl);
            properties.add("spring.datasource.username", postgres::getUsername);
            properties.add("spring.datasource.password", postgres::getPassword);
        } else {
            if (!url.matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):\\d+/[a-zA-Z0-9_]*_test")) {
                throw new IllegalArgumentException("LEAP_TEST_JDBC_URL must point to a local disposable *_test database.");
            }
            externalUrl = url;
            externalUser = System.getenv().getOrDefault("LEAP_TEST_DB_USER", "leap_test");
            externalPassword = System.getenv().getOrDefault("LEAP_TEST_DB_PASSWORD", "");
            try (var connection = java.sql.DriverManager.getConnection(url, externalUser, externalPassword);
                 var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + schema);
            } catch (java.sql.SQLException failure) { throw new IllegalStateException(failure); }
            properties.add("spring.datasource.url", () -> url + "?currentSchema=" + schema);
            properties.add("spring.datasource.username", () -> externalUser);
            properties.add("spring.datasource.password", () -> externalPassword);
        }
    }

    @org.junit.jupiter.api.AfterAll
    static void cleanUp() throws Exception {
        if (externalUrl != null) {
            try (var connection = java.sql.DriverManager.getConnection(externalUrl, externalUser, externalPassword);
                 var statement = connection.createStatement()) { statement.execute("DROP SCHEMA " + schema + " CASCADE"); }
        }
        if (postgres != null) postgres.stop();
    }

    @Autowired AccountDataService accountsData;
    @Autowired OrderExecutionService execution;
    @Autowired OrderEventService lifecycle;
    @Autowired JdbcTemplate jdbc;
    @Autowired ClientRepository clients;
    @Autowired AccountRepository accounts;
    @Autowired InstrumentRepository instruments;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean OrderExecutionPolicy policy;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean CashMovementService movements;
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    private CashMovementService movementTarget;
    private int accountId;
    private int clientId;
    private int instrumentId;
    private final OffsetDateTime now = OffsetDateTime.now().minusSeconds(5);

    @BeforeEach
    void setUp() {
        var client = clients.save(new Client("ACID test", UUID.randomUUID() + "@example.test", "unused"));
        clientId = client.getClientId();
        accountId = accounts.save(new Account(client, "Isolated test account")).getAccountId();
        var instrument = new Instrument("CRYPTO", "T" + UUID.randomUUID().toString().substring(0, 7),
                "ACID instrument", AssetClass.CRYPTO, Currency.USD);
        instrument.enableTrading();
        instrumentId = instruments.save(instrument).getInstrumentId();
        movementTarget = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(movements);
        jdbc.update("INSERT INTO account_balances VALUES (?, 'USD', 100, ?)", accountId, now);
        // Identical quote decisions isolate settlement from external prices and wall-clock market hours.
        lenient().when(policy.expirationReason(any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(policy.executionQuote(any(), any(), any())).thenReturn(Optional.of(
                new QuoteSnapshot(new Money(BigDecimal.TEN, Currency.USD), now)));
    }

    @Test
    void failedLedgerWriteRollsBackTransferBalanceAndMovementTogether() {
        doThrow(new IllegalStateException("ledger unavailable")).when(movementTarget).recordDeposit(any(), any(), any());
        assertThatThrownBy(() -> transfer(CashMovementType.DEPOSIT, "25"))
                .hasMessageContaining("ledger unavailable");
        assertThat(balance()).isEqualByComparingTo("100");
        assertThat(count("cash_movements")).isZero();
    }

    @Test
    void failureAfterFillCreationRollsBackCashHoldingsFillAndAuditOutbox() {
        int order = acceptedOrder();
        doThrow(new IllegalStateException("settlement ledger unavailable")).when(movementTarget).recordTrade(any(), any());
        assertThatThrownBy(() -> execute(order)).hasMessageContaining("settlement ledger unavailable");
        assertThat(balance()).isEqualByComparingTo("100");
        assertThat(count("account_holdings")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fills WHERE order_id=?", Long.class, order)).isZero();
        assertThat(count("cash_movements")).isZero();
        assertThat(count("audit_outbox")).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM order_events WHERE order_id=?", String.class, order)).isEqualTo("ACCEPTED");
    }

    @Test
    void duplicateExecutionCannotSettleAnOrderTwice() {
        int order = acceptedOrder();
        assertThat(execute(order)).isEqualTo(OrderExecutionService.ExecutionResult.FILLED);
        assertThat(execute(order)).isEqualTo(OrderExecutionService.ExecutionResult.SKIPPED);
        assertThat(balance()).isEqualByComparingTo("90");
        assertThat(count("cash_movements")).isOne();
        assertThat(count("audit_outbox")).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fills WHERE order_id=?", Long.class, order)).isOne();
    }

    @Test
    void concurrentWithdrawalsSerializeAndCannotOverdraw() throws Exception {
        var results = race(() -> {
            try { transfer(CashMovementType.WITHDRAW, "80"); return "committed"; }
            catch (IllegalStateException rejected) { return "rejected"; }
        });
        assertThat(results).containsExactlyInAnyOrder("committed", "rejected");
        assertThat(balance()).isEqualByComparingTo("20");
        assertThat(count("cash_movements")).isOne();
    }

    @Test
    void concurrentExecutionAttemptsProduceOneFillAndOneCashDebit() throws Exception {
        int order = acceptedOrder();
        assertThat(race(() -> execute(order))).containsExactlyInAnyOrder(
                OrderExecutionService.ExecutionResult.FILLED, OrderExecutionService.ExecutionResult.SKIPPED);
        assertThat(balance()).isEqualByComparingTo("90");
        assertThat(count("cash_movements")).isOne();
        assertThat(count("audit_outbox")).isOne();
    }

    @Test
    void uncommittedBalanceIsInvisibleToAnotherConnectionAndRollbackRestoresIt() throws Exception {
        var changed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var writer = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                jdbc.update("UPDATE account_balances SET total_balance=1 WHERE account_id=?", accountId);
                changed.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out"); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
                status.setRollbackOnly();
                return null;
            }));
            assertThat(changed.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(balance()).isEqualByComparingTo("100");
            release.countDown();
            writer.get(10, TimeUnit.SECONDS);
            assertThat(balance()).isEqualByComparingTo("100");
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test
    void databaseConstraintsRejectNegativeBalancesWithoutChangingCommittedData() {
        assertThatThrownBy(() -> jdbc.update("UPDATE account_balances SET total_balance=-1 WHERE account_id=?", accountId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(balance()).isEqualByComparingTo("100");
    }

    @Test
    void committedTransferIsVisibleThroughAFreshIndependentConnection() throws Exception {
        transfer(CashMovementType.DEPOSIT, "0.10");
        transfer(CashMovementType.DEPOSIT, "0.20");
        var dataSource = jdbc.getDataSource();
        try (var connection = java.sql.DriverManager.getConnection(
                ((com.zaxxer.hikari.HikariDataSource) dataSource).getJdbcUrl(),
                ((com.zaxxer.hikari.HikariDataSource) dataSource).getUsername(),
                ((com.zaxxer.hikari.HikariDataSource) dataSource).getPassword());
             var statement = connection.prepareStatement("SELECT total_balance FROM account_balances WHERE account_id=?")) {
            statement.setInt(1, accountId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBigDecimal(1)).isEqualByComparingTo("100.30");
            }
        }
    }

    @Test
    void outboxEnqueueRequiresATransaction() {
        assertThatThrownBy(() -> lifecycle.appendEvent(1, OrderStatus.SUBMITTED, null, null, null))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    void lifecycleAndOutboxRowsRollbackTogetherAfterTheyHaveBothBeenWritten() {
        int order = acceptedOrder();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
            lifecycle.appendEvent(order, OrderStatus.FILLED, null, BigDecimal.TEN, now);
            assertThat(count("audit_outbox")).isOne();
            throw new IllegalStateException("failure after enqueue");
        })).hasMessageContaining("failure after enqueue");
        assertThat(count("audit_outbox")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_events WHERE order_id=? AND status='FILLED'", Long.class, order)).isZero();
    }

    @Test
    void jwtAuthenticatedHttpSubmissionSettlesAndReturnsConsistentAccountAndOrderState() throws Exception {
        var http = java.net.http.HttpClient.newHttpClient();
        String base = "http://127.0.0.1:" + port;
        String bearer = com.neueda.leap.support.TestTokens.bearer(clients.findById(clientId).orElseThrow().getEmail());
        String body = mapper.writeValueAsString(new SubmitOrderRequest(accountId, instrumentId, OrderSide.BUY,
                BigDecimal.ONE, BigDecimal.TEN, now, "USD"));
        var submitted = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + ApiRoutes.ORDERS))
                .header("Authorization", bearer).header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(submitted.statusCode()).isEqualTo(201);
        var order = mapper.readTree(submitted.body());
        assertThat(order.path("status").asText()).isEqualTo("ACCEPTED");
        int id = order.path("orderId").asInt();
        assertThat(execute(id)).isEqualTo(OrderExecutionService.ExecutionResult.FILLED);
        var balance = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + ApiRoutes.ACCOUNTS + "/" + accountId + "/balance"))
                .header("Authorization", bearer).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(balance.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(balance.body()).path("totalBalance").decimalValue()).isEqualByComparingTo("90");
        var status = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + ApiRoutes.ORDERS + "/" + id))
                .header("Authorization", bearer).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(status.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(status.body()).path("status").asText()).isEqualTo("FILLED");
        assertThat(count("audit_outbox")).isEqualTo(3);
    }

    private TransferResponse transfer(CashMovementType type, String amount) {
        return accountsData.transfer(accountId, clientId, new TransferRequest(type, new BigDecimal(amount)));
    }
    private BigDecimal balance() { return jdbc.queryForObject("SELECT total_balance FROM account_balances WHERE account_id=?", BigDecimal.class, accountId); }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE account_id=?", Long.class, accountId); }
    private int acceptedOrder() {
        int id = jdbc.queryForObject("INSERT INTO orders(account_id,instrument_id,side,requested_quantity,submitted_quote_price,submitted_quote_at,submitted_at) VALUES (?,?,'BUY',1,10,?,?) RETURNING order_id",
                Integer.class, accountId, instrumentId, now, now);
        jdbc.update("INSERT INTO order_events(order_id,status,decision_quote_price,decision_quote_at,occurred_at) VALUES (?,'ACCEPTED',10,?,?)", id, now, now);
        return id;
    }
    private OrderExecutionService.ExecutionResult execute(int order) { return execution.execute(order, new MarketQuoteResultDto("TEST", null, null, false, null)); }
    private <T> List<T> race(Callable<T> action) throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<T> task = () -> { if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out"); return action.call(); };
            var first = pool.submit(task);
            var second = pool.submit(task);
            start.countDown();
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
}
