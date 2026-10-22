package com.neueda.leap.service;

import com.neueda.leap.models.PortfolioHistoryResponse.Point;
import com.neueda.leap.models.PortfolioSummaryResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioObservationStore {
    private final JdbcTemplate jdbc;

    public PortfolioObservationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Lock only while saving, never while fetching prices. One observation per account/minute. */
    @Transactional
    public void record(PortfolioSummaryResponse summary) {
        jdbc.queryForObject("SELECT account_id FROM accounts WHERE account_id = ? FOR UPDATE",
                Integer.class, summary.accountId());
        OffsetDateTime latest = jdbc.queryForObject(
                "SELECT MAX(observed_at) FROM portfolio_observations WHERE account_id = ?",
                OffsetDateTime.class, summary.accountId());
        if (latest != null && !latest.toInstant().isBefore(summary.accountAsOf().truncatedTo(ChronoUnit.MINUTES))) return;

        boolean synthetic = summary.valuationWarnings().stream().anyMatch(w -> w.contains("synthetic"));
        String valueProblem = synthetic ? "Synthetic quotes are excluded from performance history."
                : summary.estimatedPortfolioValue() == null ? String.join(" ", summary.valuationWarnings()) : null;
        jdbc.update("""
                INSERT INTO portfolio_observations
                  (account_id, observed_at, cash_balance, invested_value, portfolio_value,
                   lifetime_profit_loss, oldest_quote_at, value_unavailable_reason, profit_loss_unavailable_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, summary.accountId(), summary.accountAsOf().atOffset(java.time.ZoneOffset.UTC),
                summary.cashBalance(), synthetic ? null : summary.investedAssetsValue(),
                synthetic ? null : summary.estimatedPortfolioValue(), synthetic ? null : summary.lifetimeProfitLoss(),
                summary.oldestQuoteAt() == null ? null : summary.oldestQuoteAt().atOffset(java.time.ZoneOffset.UTC),
                valueProblem, synthetic ? valueProblem : summary.profitLossUnavailableReason());
    }

    public Instant recordingStartedAt(Integer accountId) {
        OffsetDateTime first = jdbc.queryForObject(
                "SELECT MIN(observed_at) FROM portfolio_observations WHERE account_id = ?",
                OffsetDateTime.class, accountId);
        return first == null ? null : first.toInstant();
    }

    public List<Point> points(Integer accountId, Instant since) {
        return jdbc.query("""
                SELECT * FROM portfolio_observations WHERE account_id = ? AND observed_at >= ? ORDER BY observed_at
                """, (row, index) -> new Point(row.getObject("observed_at", OffsetDateTime.class).toInstant(),
                row.getBigDecimal("cash_balance"), row.getBigDecimal("invested_value"),
                row.getBigDecimal("portfolio_value"), row.getBigDecimal("lifetime_profit_loss"),
                row.getObject("oldest_quote_at", OffsetDateTime.class) == null ? null
                        : row.getObject("oldest_quote_at", OffsetDateTime.class).toInstant(),
                row.getString("value_unavailable_reason"), row.getString("profit_loss_unavailable_reason")),
                accountId, since.atOffset(java.time.ZoneOffset.UTC));
    }
}
