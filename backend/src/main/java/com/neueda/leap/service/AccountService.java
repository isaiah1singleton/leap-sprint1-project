package com.neueda.leap.service;

import com.neueda.leap.entities.Account;
import com.neueda.leap.models.AccountResponse;
import com.neueda.leap.repository.AccountRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final ClientRepository  clientRepository;

    public AccountService(
            AccountRepository accountRepository,
            ClientRepository clientRepository
    ) {
        this.accountRepository = accountRepository;
        this.clientRepository = clientRepository;
    }

    @Transactional
    public AccountResponse openAccount(Integer authenticatedClientId) {
        Client client = clientRepository.findById(authenticatedClientId)
                .orElseThrow(() -> new NoSuchElementException("Client not found."));
        if (client.getClientStatus() != ClientStatus.ACTIVE) {
            throw new IllegalStateException("An inactive client cannot open an account");
        }
        Account account = new Account(client);
        Account savedAccount = accountRepository.save(account);
        return AccountResponse.from(savedAccount);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccounts(Integer authenticatedClientId) {
        return accountRepository.findByClient_ClientId(authenticatedClientId).stream().map(AccountResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(Integer accountId, Integer authenticatedClientId) {
        Account account = accountRepository.findByAccountIdAndClient_ClientId(
                accountId, authenticatedClientId
        ).orElseThrow(() ->  new NoSuchElementException("Account not found."));

        return AccountResponse.from(account);
    }

    @Transactional
    public Optional<Integer> findActiveClientIdByEmail(String email) {
        return clientRepository.findByEmailIgnoreCase(email)
                .filter(client -> client.getClientStatus() == ClientStatus.ACTIVE)
                .map(Client::getClientId);
    }



}
