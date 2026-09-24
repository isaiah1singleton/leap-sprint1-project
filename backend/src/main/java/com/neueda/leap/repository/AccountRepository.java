package com.neueda.leap.repository;

import com.neueda.leap.entities.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// save(account) and findById(accountId) are inherited via Spring Data

public interface AccountRepository extends JpaRepository<Account, Integer> {

    // List the accounts owned by a client
    List<Account> findByClient_ClientId(Integer clientId);

    // Find a particular account only if it belongs to that client
    Optional<Account> findByAccountIdAndClient_ClientId(Integer accountId, Integer clientId);

    boolean existsByClient_ClientIdAndAccountNameIgnoreCase(Integer clientId, String accountName);
}
