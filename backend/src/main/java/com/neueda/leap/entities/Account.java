package com.neueda.leap.entities;

import com.neueda.leap.enums.AccountStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Integer accountId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "client_id",
            nullable = false,
            updatable = false
    )
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false)
    private AccountStatus accountStatus;

    @Column(
            name = "opened_at",
            nullable = false,
            updatable = false
    )
    private Instant openedAt;

    @Column(name = "account_name", nullable = false)
    private String accountName;

    protected Account() {}

    public Account(Client client, String accountName) {
        this.client = Objects.requireNonNull(client, "An account must belong to a client.");
        this.accountStatus = AccountStatus.ACTIVE;
        this.accountName = Objects.requireNonNull(accountName, "Account name is required").trim();

        if (this.accountName.isEmpty()) {
            throw new IllegalArgumentException("Account name is required");
        }
    }

    // Runs before JPA inserts a new account
    @PrePersist
    private void beforeInsert() {
        if (openedAt == null) {
            openedAt = Instant.now();
        }
    }

    public Integer getAccountId() {
        return accountId;
    }

    public Client getClient() {
        return client;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public String getAccountName() {
        return accountName;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public boolean isActive() {
        return accountStatus == AccountStatus.ACTIVE;
    }

    public boolean isOwnedBy(Client otherClient) {
        return otherClient != null && otherClient.getClientId() != null && otherClient.getClientId().equals(client.getClientId());
    }

    public void changeStatus(AccountStatus status) {
        this.accountStatus = Objects.requireNonNull(
                status,
                "Account status is required"
        );
    }
    public void changeAccountName(String accountName) {
        String normalized = Objects.requireNonNull(accountName, "Account name is required").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Account name is required");
        }
        this.accountName = normalized;
    }
}
