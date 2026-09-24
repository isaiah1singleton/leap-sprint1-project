package com.neueda.leap.entities;

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
@Table(name = "clients")
public class Client {

    public enum ClientStatus {
        ACTIVE,
        INACTIVE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "client_id", nullable = false)
    private Integer clientId;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_status", nullable = false)
    private ClientStatus clientStatus = ClientStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "client_segment", nullable = false)
    private String clientSegment = "RETAIL";

    protected Client() {
    }

    public Client(String email, String passwordHash) {
        this.email = email.toLowerCase(java.util.Locale.ROOT);
        this.passwordHash = passwordHash;
    }

    public Integer getClientId() {
        return clientId;
    }

    public String getEmail() {
        return email;
    }

    public String getClientSegment() {
        return clientSegment;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}
