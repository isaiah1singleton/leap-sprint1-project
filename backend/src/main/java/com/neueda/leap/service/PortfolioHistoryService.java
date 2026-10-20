package com.neueda.leap.service;

import com.neueda.leap.models.PortfolioHistoryResponse;
import com.neueda.leap.repository.AccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

@Service
public class PortfolioHistoryService {
    private final AccountRepository accounts;
    private final PortfolioSummaryService summaries;
    private final PortfolioObservationStore observations;
    private final Clock clock;

    public PortfolioHistoryService(AccountRepository accounts, PortfolioSummaryService summaries,
            PortfolioObservationStore observations, Clock clock) {
        this.accounts = accounts;
        this.summaries = summaries;
        this.observations = observations;
        this.clock = clock;
    }

    public PortfolioHistoryResponse history(Integer accountId, Integer clientId, String range) {
        if (accountId == null || accountId <= 0) throw new IllegalArgumentException("Account ID must be positive.");
        Instant since = switch (range) {
            case "1D" -> clock.instant().minus(1, ChronoUnit.DAYS);
            case "1W" -> clock.instant().minus(7, ChronoUnit.DAYS);
            case "1M" -> clock.instant().minus(30, ChronoUnit.DAYS);
            case "ALL" -> Instant.EPOCH;
            default -> throw new IllegalArgumentException("Choose 1D, 1W, 1M or ALL.");
        };
        // Ownership is verified before market requests or observations are exposed.
        accounts.findByAccountIdAndClient_ClientId(accountId, clientId)
                .orElseThrow(() -> new NoSuchElementException("Account not found."));
        observations.record(summaries.summary(accountId, clientId));
        return new PortfolioHistoryResponse(accountId, "USD", observations.recordingStartedAt(accountId),
                range, observations.points(accountId, since));
    }
}
