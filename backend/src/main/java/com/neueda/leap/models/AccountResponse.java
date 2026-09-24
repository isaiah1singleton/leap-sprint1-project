package com.neueda.leap.models;

import com.neueda.leap.entity.Account;
import com.neueda.leap.enums.AccountStatus;

import java.time.Instant;

public record AccountResponse(
        Integer accountId,
        AccountStatus accountStatus,
        Instant openedAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getAccountId(),
                account.getAccountStatus(),
                account.getOpenedAt()
        );
    }
}
