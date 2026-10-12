package com.neueda.leap.entities;

import com.neueda.leap.enums.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class CashBalanceId implements Serializable {
    @Column(name = "account_id")
    private Integer accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency")
    private Currency currency;

    protected CashBalanceId() {
    }

    public CashBalanceId(Integer accountId, Currency currency) {
        this.accountId = accountId;
        this.currency = Objects.requireNonNull(currency, "Currency is required.");
    }

    public Integer getAccountId() { return accountId; }
    public Currency getCurrency() { return currency; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CashBalanceId that)) return false;
        return Objects.equals(accountId, that.accountId) && currency == that.currency;
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, currency);
    }
}
