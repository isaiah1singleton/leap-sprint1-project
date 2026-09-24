package com.neueda.leap.service;

import com.neueda.leap.entities.Account;
import com.neueda.leap.entities.Client;
import com.neueda.leap.enums.ClientStatus;
import com.neueda.leap.models.AccountResponse;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.ClientRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;

    public AccountService(
            AccountRepository accountRepository,
            ClientRepository clientRepository
    ) {
        this.accountRepository = accountRepository;
        this.clientRepository = clientRepository;
    }

    @Transactional
    public AccountResponse openAccount(Integer authenticatedClientId, String accountName) {
        if (accountName == null || accountName.isBlank()) {
            throw new IllegalArgumentException("Account name is required.");
        }
        String normalizedAccountName = accountName.trim();
        if (accountRepository.existsByClient_ClientIdAndAccountNameIgnoreCase(authenticatedClientId, normalizedAccountName)) {
            throw new IllegalStateException("You already have an account with that name.");
        }
        Client client = clientRepository.findById(authenticatedClientId)
                .orElseThrow(() -> new NoSuchElementException("Client not found."));
        if (client.getClientStatus() != ClientStatus.ACTIVE) {
            throw new IllegalStateException("An inactive client cannot open an account");
        }
        Account account = new Account(client, normalizedAccountName);
        Account savedAccount = accountRepository.save(account);
        return AccountResponse.from(savedAccount);
    }

    @Transactional
    public List<AccountResponse> getAccounts(Integer authenticatedClientId) {
        return accountRepository.findByClient_ClientId(authenticatedClientId).stream().map(AccountResponse::from).toList();
    }

    @Transactional
    public AccountResponse getAccount(Integer accountId, Integer authenticatedClientId) {
        Account account = accountRepository.findByAccountIdAndClient_ClientId(
                accountId, authenticatedClientId
        ).orElseThrow(() ->  new NoSuchElementException("Account not found."));

        return AccountResponse.from(account);
    }
}
