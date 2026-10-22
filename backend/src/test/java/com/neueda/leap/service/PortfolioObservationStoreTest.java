package com.neueda.leap.service;

import com.neueda.leap.models.PortfolioSummaryResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.OffsetDateTime;
import java.util.List;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Tests recording decisions and saved values, not SQL execution or database locks. */
@Tag("unit")
class PortfolioObservationStoreTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PortfolioObservationStore store = new PortfolioObservationStore(jdbc);
    private PortfolioSummaryResponse summary(boolean synthetic, boolean missingValue) {
        var s = mock(PortfolioSummaryResponse.class);
        when(s.accountId()).thenReturn(1); when(s.accountAsOf()).thenReturn(NOW.plusSeconds(20));
        when(s.cashBalance()).thenReturn(bd("100")); when(s.investedAssetsValue()).thenReturn(bd("200"));
        when(s.estimatedPortfolioValue()).thenReturn(missingValue ? null : bd("300"));
        when(s.lifetimeProfitLoss()).thenReturn(bd("25")); when(s.oldestQuoteAt()).thenReturn(NOW.minusSeconds(60));
        when(s.valuationWarnings()).thenReturn(synthetic ? List.of("Valuation includes synthetic provider quotes.") : missingValue ? List.of("Missing price") : List.of());
        return s;
    }
    @Test void newerObservationPreservesActualTimesAndValues() {
        store.record(summary(false, false));
        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class); verify(jdbc).update(anyString(), values.capture());
        assertThat(values.getValue()).containsExactly(1, AT.plusSeconds(20), bd("100"), bd("200"), bd("300"), bd("25"), AT.minusSeconds(60), null, null);
    }
    @Test void sameMinuteAndOlderSnapshotsAreSkipped() {
        when(jdbc.queryForObject(contains("MAX(observed_at)"), eq(OffsetDateTime.class), eq(1))).thenReturn(AT, AT.plusMinutes(1));
        store.record(summary(false, false)); store.record(summary(false, false)); verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
    @Test void earlierMinuteAllowsNewObservation() {
        when(jdbc.queryForObject(contains("MAX(observed_at)"), eq(OffsetDateTime.class), eq(1))).thenReturn(AT.minusMinutes(1));
        store.record(summary(false, false)); verify(jdbc).update(anyString(), any(Object[].class));
    }
    @Test void syntheticQuotesCreateGapsButPreserveCash() {
        store.record(summary(true, false));
        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class); verify(jdbc).update(anyString(), values.capture());
        Object[] v = values.getValue(); assertThat(v[2]).isEqualTo(bd("100"));
        assertThat(v[3]).isNull(); assertThat(v[4]).isNull(); assertThat(v[5]).isNull();
        assertThat(v[7]).isEqualTo("Synthetic quotes are excluded from performance history."); assertThat(v[8]).isEqualTo(v[7]);
    }
    @Test void unavailableValueAndProfitLossReasonsArePreserved() {
        var s = summary(false, true); when(s.oldestQuoteAt()).thenReturn(null); when(s.lifetimeProfitLoss()).thenReturn(null);
        when(s.profitLossUnavailableReason()).thenReturn("History incomplete"); store.record(s);
        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class); verify(jdbc).update(anyString(), values.capture());
        assertThat(values.getValue()[4]).isNull(); assertThat(values.getValue()[5]).isNull(); assertThat(values.getValue()[6]).isNull();
        assertThat(values.getValue()[7]).isEqualTo("Missing price"); assertThat(values.getValue()[8]).isEqualTo("History incomplete");
    }
    @Test void recordingStartHandlesEmptyAndExistingHistory() {
        assertThat(store.recordingStartedAt(1)).isNull();
        when(jdbc.queryForObject(contains("MIN(observed_at)"), eq(OffsetDateTime.class), eq(1))).thenReturn(AT);
        assertThat(store.recordingStartedAt(1)).isEqualTo(NOW);
    }
    @Test void historyMappingPreservesGapsAndQuoteTime() throws Exception {
        java.sql.ResultSet row = mock(java.sql.ResultSet.class);
        when(row.getObject("observed_at", OffsetDateTime.class)).thenReturn(AT);
        when(row.getObject("oldest_quote_at", OffsetDateTime.class)).thenReturn(AT.minusMinutes(1), AT.minusMinutes(1), null);
        when(row.getBigDecimal("cash_balance")).thenReturn(bd("100"));
        when(row.getString("value_unavailable_reason")).thenReturn("missing price");
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<com.neueda.leap.models.PortfolioHistoryResponse.Point>>any(), eq(1), eq(AT.minusDays(1))))
            .thenAnswer(call -> {
                org.springframework.jdbc.core.RowMapper<com.neueda.leap.models.PortfolioHistoryResponse.Point> mapper = call.getArgument(1);
                return List.of(mapper.mapRow(row, 0), mapper.mapRow(row, 1));
            });
        var result = store.points(1, NOW.minusSeconds(86400));
        assertThat(result).hasSize(2); assertThat(result.get(0).oldestQuoteAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(result.get(1).oldestQuoteAt()).isNull(); assertThat(result.get(0).portfolioValue()).isNull();
        assertThat(result.get(0).cashBalance()).isEqualByComparingTo("100"); assertThat(result.get(0).valueUnavailableReason()).isEqualTo("missing price");
    }
}
