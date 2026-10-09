package com.neueda.leap.entities;

import com.neueda.leap.enums.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Entity
@Table(name = "account_balances")
public class CashBalance {
    @EmbeddedId
    private CashBalanceId id;

    @MapsId("accountId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "total_balance", nullable = false, precision = 24, scale = 8)
    private BigDecimal totalBalance;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected CashBalance() {
    }

    public CashBalance(Account account, Currency currency) {
        this.account = Objects.requireNonNull(account, "Account is required.");
        this.id = new CashBalanceId(account.getAccountId(), currency);
        this.totalBalance = BigDecimal.ZERO;
        touch();
    }

    @PrePersist
    private void beforeInsert() {
        if (updatedAt == null) touch();
    }

    public CashBalanceId getId() { return id; }
    public Account getAccount() { return account; }
    public Currency getCurrency() { return id.getCurrency(); }
    public Money getTotalBalance() { return new Money(totalBalance, getCurrency()); }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public Money availableBalance() { return new Money(totalBalance, getCurrency()); }

    public void depositCash(Money amount) {
        totalBalance = totalBalance.add(requirePositiveAmount(amount));
        touch();
    }

    public void withdrawCash(Money amount) {
        BigDecimal value = requirePositiveAmount(amount);
        if (availableBalance().amount().compareTo(value) < 0) {
            throw new IllegalStateException("Insufficient available cash to withdraw.");
        }
        totalBalance = totalBalance.subtract(value);
        touch();
    }

    private BigDecimal requirePositiveAmount(Money amount) {
        if (amount == null || amount.currency() != getCurrency()
                || amount.amount().signum() <= 0
                || Math.max(0, amount.amount().stripTrailingZeros().scale()) > 8) {
            throw new IllegalArgumentException(
                    "Amount must be positive, in the balance currency, and have at most 8 decimal places.");
        }
        return amount.amount();
    }

    private void touch() { updatedAt = OffsetDateTime.now(ZoneOffset.UTC); }
}
