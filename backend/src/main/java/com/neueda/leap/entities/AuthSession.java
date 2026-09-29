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

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "is_revoked", nullable = false)
    private boolean revoked;

    protected AuthSession() {
    }

    public AuthSession(String sessionTokenHash, Client client, OffsetDateTime expiresAt) {
        this.sessionTokenHash = sessionTokenHash;
        this.client = client;
        this.expiresAt = expiresAt;
        this.revoked = false;
    }

    public Client getClient() {
        return client;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }
}
