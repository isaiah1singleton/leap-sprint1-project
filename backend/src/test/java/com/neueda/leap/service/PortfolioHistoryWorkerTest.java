package com.neueda.leap.service;

import com.neueda.leap.entities.Account;
import com.neueda.leap.entities.Client;
import com.neueda.leap.models.PortfolioSummaryResponse;
import com.neueda.leap.repository.AccountRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.Tag("unit")
class PortfolioHistoryWorkerTest {
    @Test void traversesPagesAndHandlesEmptyRepository() {
        var accounts = mock(AccountRepository.class);
        var summaries = mock(PortfolioSummaryService.class);
        var observations = mock(PortfolioObservationStore.class);
        var worker = new PortfolioHistoryWorker(accounts, summaries, observations);
        when(accounts.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        worker.sampleAccounts(); verifyNoInteractions(summaries, observations);
        var first = com.neueda.leap.support.TradingFixtures.account();
        var second = com.neueda.leap.support.TradingFixtures.id(com.neueda.leap.support.TradingFixtures.account(), "accountId", 2);
        when(accounts.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(
            new PageImpl<>(List.of(first), org.springframework.data.domain.PageRequest.of(0, 1), 2),
            new PageImpl<>(List.of(second), org.springframework.data.domain.PageRequest.of(1, 1), 2));
        worker.sampleAccounts(); verify(summaries).summary(1, 7); verify(summaries).summary(2, 7);
    }
    @Test void continuesAfterProviderFailureAndSkipsInactiveAccounts() {
        var accounts = mock(AccountRepository.class);
        var summaries = mock(PortfolioSummaryService.class);
        var observations = mock(PortfolioObservationStore.class);
        var client = mock(Client.class);
        when(client.getClientId()).thenReturn(7);
        var first = account(1, client, true);
        var second = account(2, client, true);
        var inactive = account(3, client, false);
        when(accounts.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second, inactive)));
        when(summaries.summary(1, 7)).thenThrow(new IllegalStateException("Provider unavailable"));
        var response = mock(PortfolioSummaryResponse.class);
        when(summaries.summary(2, 7)).thenReturn(response);
        new PortfolioHistoryWorker(accounts, summaries, observations).sampleAccounts();
        verify(observations).record(response);
        verify(summaries, never()).summary(3, 7);
    }

    private Account account(int id, Client client, boolean active) {
        var account = mock(Account.class);
        when(account.getAccountId()).thenReturn(id);
        when(account.getClient()).thenReturn(client);
        when(account.isActive()).thenReturn(active);
        return account;
    }
}
