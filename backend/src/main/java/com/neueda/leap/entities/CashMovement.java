package com.neueda.leap.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.EnumType;
import jakarta.persistence.OneToOne;
import java.math.BigDecimal;

import java.time.OffsetDateTime;
import com.neueda.leap.enums.CashMovementType;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.enums.OrderSide;


@Entity 
@Table(
    name = "cash_movements",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_cash_movements_fill",
        columnNames = "fill_id"
    )
)
public class CashMovement {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cash_movement_id")
    private Integer cashMovementId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fill_id", updatable = false)
    private Fill fill;

    @Column(
            name = "amount",
            nullable = false,
            updatable = false,
            columnDefinition = "numeric"
    )
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "movement_type",
            nullable = false,
            updatable = false,
            columnDefinition = "text"
    )
    private CashMovementType cashMovementType;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "currency",
            nullable = false,
            updatable = false,
            columnDefinition = "text"
    )
    private Currency currency;

    @Column(
            name = "occurred_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime occurredAt;

     @Column(
            name = "reason",
            updatable = false,
            columnDefinition = "text"
    )
    private String reason;

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be greater than zero."
            );
        }
    }

    protected CashMovement() {}
    
    private CashMovement(Account account, Fill fill, BigDecimal amount, CashMovementType cashMovementType, Currency currency, OffsetDateTime occurredAt, String reason) {
        if (account == null) {
            throw new IllegalArgumentException("Account is required.");
        }

        if (amount == null || amount.signum() == 0) {
            throw new IllegalArgumentException(
                    "Movement amount must be nonzero."
            );
        }

        if (cashMovementType == null) {
            throw new IllegalArgumentException(
                    "Movement type is required."
            );
        }

        if (currency == null) {
            throw new IllegalArgumentException("Currency is required.");
        }

        if (occurredAt == null) {
            throw new IllegalArgumentException(
                    "Movement time is required."
            );
        }

        boolean isTrade = cashMovementType == CashMovementType.TRADE;

        if (isTrade != (fill != null)) {
            throw new IllegalArgumentException(
                    "Only trade movements must reference a fill."
            );
        }

        if (cashMovementType == CashMovementType.DEPOSIT
                && amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Deposits must increase cash."
            );
        }

        if ((cashMovementType == CashMovementType.WITHDRAW
                || cashMovementType == CashMovementType.FEE)
                && amount.signum() >= 0) {
            throw new IllegalArgumentException(
                    "Withdrawals and fees must decrease cash."
            );
        }

        String normalizedReason =
                reason == null || reason.isBlank()
                        ? null
                        : reason.strip();

        if (cashMovementType == CashMovementType.ADJUSTMENT
                && normalizedReason == null) {
            throw new IllegalArgumentException(
                    "Adjustments require a reason."
            );
        }

        this.account = account;
        this.fill = fill;
        this.amount = amount;
        this.cashMovementType = cashMovementType;
        this.currency = currency;
        this.occurredAt = occurredAt;
        this.reason = normalizedReason;
    }

    public static CashMovement forTrade(Fill fill) {
        if (fill == null) {
            throw new IllegalArgumentException("Fill is required.");
        }

        Order order = fill.getOrder();

        if (order == null || order.getSide() == null) {
            throw new IllegalArgumentException(
                    "Fill must reference an order with a side."
            );
        }

        requirePositive(fill.getFillQuantity());
        requirePositive(fill.getExecutionPrice());

        BigDecimal amount = fill.getFillQuantity()
                .multiply(fill.getExecutionPrice());

        if (order.getSide() == OrderSide.BUY) {
            amount = amount.negate();
        }

        return new CashMovement(
                order.getAccount(),
                fill,
                amount,
                CashMovementType.TRADE,
                fill.getExecutionCurrency(),
                fill.getExecutionTime(),
                null
        );
    }

    public static CashMovement deposit(Account account, BigDecimal amount, Currency currency, OffsetDateTime occurredAt) {
        requirePositive(amount);

        return new CashMovement(
                account,
                null,
                amount,
                CashMovementType.DEPOSIT,
                currency,
                occurredAt,
                null
        );
    }

    public static CashMovement withdrawal(
            Account account,
            BigDecimal amount,
            Currency currency,
            OffsetDateTime occurredAt
    ) {
        requirePositive(amount);

        return new CashMovement(
                account, null, amount.negate(),
                CashMovementType.WITHDRAW,
                currency, occurredAt, null
        );
    }

    public static CashMovement fee(
            Account account,
            BigDecimal amount,
            Currency currency,
            OffsetDateTime occurredAt
    ) {
        requirePositive(amount);

        return new CashMovement(
                account, null, amount.negate(),
                CashMovementType.FEE,
                currency, occurredAt, null
        );
    }

    public static CashMovement adjustment(
            Account account,
            BigDecimal signedAmount,
            Currency currency,
            String reason,
            OffsetDateTime occurredAt
    ) {
        return new CashMovement(
                account, null, signedAmount,
                CashMovementType.ADJUSTMENT,
                currency, occurredAt, reason
        );
    }

    public Integer getCashMovementId() {
        return cashMovementId;
    }

    public Account getAccount() {
        return account;
    }

    public Fill getFill() {
        return fill;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public CashMovementType getCashMovementType() {
        return cashMovementType;
    }

    public Currency getCurrency() {
        return currency;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getReason() {
        return reason;
    }
}
