package com.neueda.leap.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "sessions")
public class AuthSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id", nullable = false)
    private Integer sessionId;

    @Column(name = "session_token_hash", nullable = false, unique = true)
    private String sessionTokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "last_activity_at", nullable = false)
    private OffsetDateTime lastActivityAt;

    // Keep the existing flag so previously revoked sessions remain revoked.
    @Column(name = "is_revoked", nullable = false)
    private boolean revoked;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    protected AuthSession() {
    }

    public AuthSession(String sessionTokenHash, Client client, OffsetDateTime createdAt, OffsetDateTime expiresAt) {
        this.sessionTokenHash = sessionTokenHash;
        this.client = client;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.lastActivityAt = createdAt;
    }

    public Client getClient() {
        return client;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getLastActivityAt() {
        return lastActivityAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void recordActivity(OffsetDateTime at) {
        this.lastActivityAt = at;
    }

    public void revoke(OffsetDateTime at) {
        this.revoked = true;
        this.revokedAt = at;
    }
}
