package com.neueda.leap.service;

import com.neueda.leap.entities.Account;
import com.neueda.leap.entities.CashMovement;
import com.neueda.leap.entities.Fill;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.models.CashMovementResponse;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.CashMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class CashMovementService {
    private final CashMovementRepository movementRepository;
    private final AccountRepository accountRepository;
    private final Clock clock;

    private CashMovement saveMovement(CashMovement movement) {
        Integer accountId = movement.getAccount().getAccountId();

        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException(
                    "A persisted account is required."
            );
        }

        return movementRepository.save(movement);
    }

    private static void requirePositiveId(
            Integer id,
            String field
    ) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    field + " ID must be positive."
            );
        }
    }

     public CashMovementService(
            CashMovementRepository movementRepository,
            AccountRepository accountRepository,
            Clock clock
    ) {
        this.movementRepository = movementRepository;
        this.accountRepository = accountRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CashMovement recordTrade(Fill fill) {
        if (fill == null || fill.getFillId() == null || fill.getFillId() <= 0) {
            throw new IllegalArgumentException("A persisted fill is required.");
        }
        return saveMovement(CashMovement.forTrade(fill));
    }
    
    @Transactional(propagation = Propagation.MANDATORY)
    public CashMovement recordDeposit(Account account, BigDecimal amount, Currency currency) {
        return saveMovement(CashMovement.deposit(account, amount, currency, OffsetDateTime.now(clock)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CashMovement recordWithdrawal(
            Account account,
            BigDecimal amount,
            Currency currency
    ) {
        return saveMovement(CashMovement.withdrawal(
                account,
                amount,
                currency,
                OffsetDateTime.now(clock)
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CashMovement recordFee(
            Account account,
            BigDecimal amount,
            Currency currency
    ) {
        return saveMovement(CashMovement.fee(
                account,
                amount,
                currency,
                OffsetDateTime.now(clock)
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CashMovement recordAdjustment(
            Account account,
            BigDecimal signedAmount,
            Currency currency,
            String reason
    ) {
        return saveMovement(CashMovement.adjustment(
                account,
                signedAmount,
                currency,
                reason,
                OffsetDateTime.now(clock)
        ));
    }

    @Transactional(readOnly = true)
    public List<CashMovementResponse> getMovements(
            Integer accountId,
            Integer authenticatedClientId
    ) {
        requirePositiveId(accountId, "Account");
        requirePositiveId(authenticatedClientId, "Client");

        accountRepository.findByAccountIdAndClient_ClientId(
                accountId,
                authenticatedClientId
        ).orElseThrow(() ->
                new NoSuchElementException("Account not found.")
        );

        return movementRepository
                .findOwnedHistory(accountId, authenticatedClientId)
                .stream()
                .map(CashMovementResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CashMovementResponse getMovement(
            Integer movementId,
            Integer authenticatedClientId
    ) {
        requirePositiveId(movementId, "Cash movement");
        requirePositiveId(authenticatedClientId, "Client");

        CashMovement movement = movementRepository
                .findOwnedMovement(
                        movementId,
                        authenticatedClientId
                )
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Cash movement not found."
                        )
                );

        return CashMovementResponse.from(movement);
    }





}
