package com.neueda.leap.service;

import com.neueda.leap.entities.Account;
import com.neueda.leap.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "portfolio.history.enabled", havingValue = "true")
public class PortfolioHistoryWorker {
    private static final Logger log = LoggerFactory.getLogger(PortfolioHistoryWorker.class);
    private final AccountRepository accounts;
    private final PortfolioSummaryService summaries;
    private final PortfolioObservationStore observations;

    public PortfolioHistoryWorker(AccountRepository accounts, PortfolioSummaryService summaries,
            PortfolioObservationStore observations) {
        this.accounts = accounts;
        this.summaries = summaries;
        this.observations = observations;
    }

    @Scheduled(scheduler = "portfolioHistoryScheduler", initialDelayString = "${portfolio.history.initial-delay-ms:15000}",
            fixedDelayString = "${portfolio.history.poll-delay-ms:300000}")
    public void sampleAccounts() {
        int page = 0;
        Page<Account> batch;
        do {
            batch = accounts.findAll(PageRequest.of(page++, 100, Sort.by("accountId")));
            for (var account : batch) {
                if (!account.isActive()) continue;
                try {
                    observations.record(summaries.summary(account.getAccountId(), account.getClient().getClientId()));
                } catch (RuntimeException failure) {
                    log.warn("Could not sample portfolio history for account {} ({}).",
                            account.getAccountId(), failure.getClass().getSimpleName());
                }
            }
        } while (batch.hasNext());
    }
}
