package com.neueda.leap.models;

import com.neueda.leap.entities.CashBalance;
import com.neueda.leap.enums.Currency;
import java.math.BigDecimal;

public record AccountBalanceResponse(Integer accountId, Currency currency, BigDecimal totalBalance) {
    public static AccountBalanceResponse from(CashBalance balance) {
        return new AccountBalanceResponse(balance.getAccount().getAccountId(), balance.getCurrency(),
                balance.getTotalBalance().amount());
    }
}
