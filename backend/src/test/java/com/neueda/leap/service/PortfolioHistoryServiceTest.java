package com.neueda.leap.service;

import com.neueda.leap.models.*;
import com.neueda.leap.repository.AccountRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class PortfolioHistoryServiceTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final PortfolioSummaryService summaries = mock(PortfolioSummaryService.class);
    private final PortfolioObservationStore observations = mock(PortfolioObservationStore.class);
    private final PortfolioHistoryService service = new PortfolioHistoryService(accounts, summaries, observations, CLOCK);
    @ParameterizedTest @CsvSource({"1D,1", "1W,7", "1M,30", "ALL,0"})
    void rangesRecordCurrentValueAndKeepOriginalStart(String range, long days) {
        when(accounts.findByAccountIdAndClient_ClientId(1, 7)).thenReturn(Optional.of(account()));
        var summary = mock(PortfolioSummaryResponse.class); when(summaries.summary(1, 7)).thenReturn(summary);
        var start = NOW.minusSeconds(100 * 86400L); when(observations.recordingStartedAt(1)).thenReturn(start);
        var result = service.history(1, 7, range);
        assertThat(result.recordingStartedAt()).isEqualTo(start); assertThat(result.range()).isEqualTo(range);
        var sequence = inOrder(summaries, observations); sequence.verify(summaries).summary(1, 7); sequence.verify(observations).record(summary);
        verify(observations).points(1, days == 0 ? Instant.EPOCH : NOW.minusSeconds(days * 86400));
    }
    @Test void invalidInputsAndNonOwnerNeverConsumeMarketQuota() {
        for (Integer invalid : Arrays.asList(null, 0, -1)) assertThatIllegalArgumentException().isThrownBy(() -> service.history(invalid, 7, "ALL"));
        for (String range : new String[]{"", "1Y", "all"}) assertThatIllegalArgumentException().isThrownBy(() -> service.history(1, 7, range));
        assertThatThrownBy(() -> service.history(1, 8, "ALL")).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(summaries, observations);
    }
    @Test void summaryFailureDoesNotRecordInventedObservation() {
        when(accounts.findByAccountIdAndClient_ClientId(1, 7)).thenReturn(Optional.of(account()));
        when(summaries.summary(1, 7)).thenThrow(new IllegalStateException("provider"));
        assertThatIllegalStateException().isThrownBy(() -> service.history(1, 7, "ALL")); verifyNoInteractions(observations);
    }
}
