package com.neueda.leap.entities;

import com.neueda.leap.enums.AccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "suspicious_trade_flags")
public class SuspiciousTradeFlag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "flag_id")
    private Long flagId;

    @Column(name = "order_id", nullable = false, unique = true)
    private Integer orderId;

    @Column(name = "account_id", nullable = false)
    private Integer accountId;

    @Column(name = "flagged_by", nullable = false)
    private String flaggedBy;

    @Column(name = "flagged_at", nullable = false)
    private OffsetDateTime flaggedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_account_status", nullable = false)
    private AccountStatus previousAccountStatus;

    protected SuspiciousTradeFlag() { }

    public SuspiciousTradeFlag(Integer orderId, Integer accountId, String flaggedBy,
                               OffsetDateTime flaggedAt, AccountStatus previousAccountStatus) {
        this.orderId = orderId;
        this.accountId = accountId;
        this.flaggedBy = flaggedBy;
        this.flaggedAt = flaggedAt;
        this.previousAccountStatus = previousAccountStatus;
    }

    public Integer getOrderId() { return orderId; }
    public Integer getAccountId() { return accountId; }
    public AccountStatus getPreviousAccountStatus() { return previousAccountStatus; }
}
