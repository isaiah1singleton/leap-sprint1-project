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

    private static final int BALANCE_SCALE = 8;

    private static final BigDecimal MAX_BALANCE =
            new BigDecimal("9999999999999999.99999999");

    @EmbeddedId
    private CashBalanceId id;

    @MapsId("accountId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(
            name = "total_balance",
            nullable = false,
            precision = 24,
            scale = 8
    )
    private BigDecimal totalBalance;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected CashBalance() {
    }

    public CashBalance(Account account, Currency currency) {
        this.account = Objects.requireNonNull(
                account,
                "Account is required."
        );
        this.id = new CashBalanceId(
                account.getAccountId(),
                Objects.requireNonNull(currency, "Currency is required.")
        );
        this.totalBalance = BigDecimal.ZERO;
        touch();
    }

    @PrePersist
    private void beforeInsert() {
        if (updatedAt == null) {
            touch();
        }
    }

    public CashBalanceId getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public Currency getCurrency() {
        return id.getCurrency();
    }

    public Money getTotalBalance() {
        return new Money(totalBalance, getCurrency());
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void depositCash(Money amount) {
        BigDecimal value = requirePositiveAmount(amount);
        BigDecimal resultingBalance = totalBalance.add(value);

        if (resultingBalance.compareTo(MAX_BALANCE) > 0) {
            throw new IllegalStateException(
                    "Resulting cash balance exceeds the supported balance range."
            );
        }

        totalBalance = resultingBalance;
        touch();
    }

    public void withdrawCash(Money amount) {
        BigDecimal value = requirePositiveAmount(amount);

        if (totalBalance.compareTo(value) < 0) {
            throw new IllegalStateException(
                    "Insufficient cash to withdraw."
            );
        }

        totalBalance = totalBalance.subtract(value);
        touch();
    }

    private BigDecimal requirePositiveAmount(Money amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required.");
        }

        if (amount.currency() != getCurrency()) {
            throw new IllegalArgumentException(
                    "Amount must be in the balance currency."
            );
        }

        BigDecimal value = amount.amount();

        if (value.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be positive."
            );
        }

        int fractionalDigits = Math.max(
                0,
                value.stripTrailingZeros().scale()
        );

        if (fractionalDigits > BALANCE_SCALE) {
            throw new IllegalArgumentException(
                    "Amount must have at most 8 decimal places."
            );
        }

        if (value.compareTo(MAX_BALANCE) > 0) {
            throw new IllegalArgumentException(
                    "Amount exceeds the supported balance range."
            );
        }

        return value;
    }

    private void touch() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
