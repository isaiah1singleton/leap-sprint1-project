package com.neueda.leap;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionSchemaTest {

    @Test
    void freshSchemaCanBeReappliedWithoutChangingHoldings() throws Exception {
        try (Connection connection = database()) {
            applySchema(connection);
            insertHolding(connection);
            execute(connection, "UPDATE account_holdings SET reserved_quantity = 3");

            applySchema(connection);

            assertHolding(connection, 3);
            assertReservationConstraints(connection);
        }
    }

    @Test
    void existingHoldingsGainReservedQuantityWithoutLosingData() throws Exception {
        try (Connection connection = database()) {
            applySchema(connection);
            execute(connection, "DROP TABLE account_holdings");
            execute(connection, """
                    CREATE TABLE account_holdings (
                        account_id INTEGER NOT NULL REFERENCES accounts(account_id),
                        instrument_id INTEGER NOT NULL REFERENCES instruments(instrument_id),
                        PRIMARY KEY (account_id, instrument_id),
                        total_quantity NUMERIC(24, 8) NOT NULL DEFAULT 0 CHECK(total_quantity >= 0),
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            insertHolding(connection);

            applySchema(connection);

            assertHolding(connection, 0);
            assertReservationConstraints(connection);
            execute(connection, "UPDATE account_holdings SET reserved_quantity = 4");

            applySchema(connection);

            assertHolding(connection, 4);
        }
    }

    private Connection database() throws SQLException {
        return DriverManager.getConnection(
                "jdbc:h2:mem:schema-" + UUID.randomUUID() + ";MODE=PostgreSQL", "sa", "");
    }

    private void applySchema(Connection connection) {
        ScriptUtils.executeSqlScript(connection,
                new FileSystemResource("../database/transaction_schema.sql"));
    }

    private void insertHolding(Connection connection) throws SQLException {
        execute(connection, """
                INSERT INTO clients (client_id, client_name, email, password_hash, client_status, client_segment)
                VALUES (1, 'Schema test', 'schema@example.com', 'hash', 'ACTIVE', 'RETAIL')
                """);
        execute(connection, """
                INSERT INTO accounts (account_id, client_id, account_status, account_name)
                VALUES (1, 1, 'ACTIVE', 'Trading')
                """);
        execute(connection, """
                INSERT INTO instruments (instrument_id, market, symbol, asset_class, instrument_name, quote_currency)
                VALUES (1, 'TEST', 'TEST', 'EQUITY', 'Schema test', 'USD')
                """);
        execute(connection, """
                INSERT INTO account_holdings (account_id, instrument_id, total_quantity)
                VALUES (1, 1, 10)
                """);
    }

    private void assertHolding(Connection connection, int reserved) throws SQLException {
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT * FROM account_holdings")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt("account_id")).isEqualTo(1);
            assertThat(rows.getInt("instrument_id")).isEqualTo(1);
            assertThat(rows.getBigDecimal("total_quantity")).isEqualByComparingTo("10");
            assertThat(rows.getBigDecimal("reserved_quantity")).isEqualByComparingTo(String.valueOf(reserved));
            assertThat(rows.getObject("updated_at")).isNotNull();
            assertThat(rows.next()).isFalse();
        }
    }

    private void assertReservationConstraints(Connection connection) {
        for (String invalid : new String[] {"-1", "11", "NULL"}) {
            assertThatThrownBy(() -> execute(connection,
                    "UPDATE account_holdings SET reserved_quantity = " + invalid))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
