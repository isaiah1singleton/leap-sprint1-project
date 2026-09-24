package com.neueda.leap.models;

import com.neueda.leap.entities.Account;
import com.neueda.leap.enums.AccountStatus;

import java.time.Instant;

public record AccountResponse(
        Integer accountId,
        String accountName,
        AccountStatus accountStatus,
        Instant openedAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getAccountId(),
                account.getAccountName(),
                account.getAccountStatus(),
                account.getOpenedAt()
        );
    }
}
