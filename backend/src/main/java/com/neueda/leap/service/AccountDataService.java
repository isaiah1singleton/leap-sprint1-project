package com.neueda.leap.service;

import com.neueda.leap.entities.*;
import com.neueda.leap.enums.CashMovementType;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.models.*;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.CashBalanceRepository;
import com.neueda.leap.repository.HoldingRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDataService {
    private final AccountRepository accounts;
    private final CashBalanceRepository balances;
    private final HoldingRepository holdings;
    private final CashMovementService movements;
    private final EntityManager entityManager;

    public AccountDataService(AccountRepository accounts, CashBalanceRepository balances,
            HoldingRepository holdings, CashMovementService movements, EntityManager entityManager) {
        this.accounts = accounts;
        this.balances = balances;
        this.holdings = holdings;
        this.movements = movements;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public AccountBalanceResponse balance(Integer accountId, Integer clientId) {
        owned(accountId, clientId);
        return balances.findById(new CashBalanceId(accountId, Currency.USD))
                .map(AccountBalanceResponse::from)
                .orElse(new AccountBalanceResponse(accountId, Currency.USD, BigDecimal.ZERO));
    }

    @Transactional(readOnly = true)
    public List<HoldingResponse> holdings(Integer accountId, Integer clientId) {
        owned(accountId, clientId);
        return holdings.findOwnedHoldings(accountId, clientId).stream().map(HoldingResponse::from).toList();
    }

    @Transactional
    public TransferResponse transfer(Integer accountId, Integer clientId, TransferRequest request) {
        if (request == null || (request.type() != CashMovementType.DEPOSIT
                && request.type() != CashMovementType.WITHDRAW) || request.amount() == null
                || request.amount().signum() <= 0 || Math.max(0, request.amount().stripTrailingZeros().scale()) > 2
                || request.amount().precision() - request.amount().scale() > 16) {
            throw new IllegalArgumentException("Provide a positive USD amount with at most two decimal places and a DEPOSIT or WITHDRAW type.");
        }
        Account account = owned(accountId, clientId);
        entityManager.lock(account, LockModeType.PESSIMISTIC_WRITE);
        if (!account.isActive()) throw new IllegalStateException("This account is inactive.");
        CashBalance balance = entityManager.find(CashBalance.class,
                new CashBalanceId(accountId, Currency.USD), LockModeType.PESSIMISTIC_WRITE);
        if (balance == null) balance = balances.saveAndFlush(new CashBalance(account, Currency.USD));
        Money amount = new Money(request.amount(), Currency.USD);
        CashMovement movement;
        if (request.type() == CashMovementType.DEPOSIT) {
            balance.depositCash(amount);
            movement = movements.recordDeposit(account, request.amount(), Currency.USD);
        } else {
            balance.withdrawCash(amount);
            movement = movements.recordWithdrawal(account, request.amount(), Currency.USD);
        }
        return new TransferResponse(CashMovementResponse.from(movement), AccountBalanceResponse.from(balance));
    }

    private Account owned(Integer accountId, Integer clientId) {
        if (accountId == null || accountId <= 0) throw new IllegalArgumentException("Account ID must be positive.");
        return accounts.findByAccountIdAndClient_ClientId(accountId, clientId)
                .orElseThrow(() -> new NoSuchElementException("Account not found."));
    }
}
