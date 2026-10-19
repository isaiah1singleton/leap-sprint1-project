package com.neueda.leap.entities;

import com.neueda.leap.models.TradeAuditEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_outbox")
public class AuditOutbox {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long outboxId;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;
    @Column(name = "order_id", nullable = false)
    private Integer orderId;
    @Column(name = "event_type", nullable = false)
    private String eventType;
    @Column(name = "status", nullable = false)
    private String status;
    @Column(name = "reason")
    private String reason;
    @Column(name = "account_id", nullable = false)
    private Integer accountId;
    @Column(name = "client_id", nullable = false)
    private Integer clientId;
    @Column(name = "client_email", nullable = false)
    private String clientEmail;
    @Column(name = "client_name", nullable = false)
    private String clientName;
    @Column(name = "instrument", nullable = false)
    private String instrument;
    @Column(name = "side", nullable = false)
    private String side;
    @Column(name = "quantity", nullable = false, precision = 24, scale = 8)
    private BigDecimal quantity;
    @Column(name = "price", precision = 24, scale = 8)
    private BigDecimal price;
    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "last_error")
    private String lastError;

    protected AuditOutbox() { }

    public AuditOutbox(TradeAuditEvent event) {
        this.eventId = event.eventId();
        this.orderId = event.orderId();
        this.eventType = event.eventType();
        this.status = event.status();
        this.reason = event.reason();
        this.accountId = event.accountId();
        this.clientId = event.clientId();
        this.clientEmail = event.clientEmail();
        this.clientName = event.clientName();
        this.instrument = event.instrument();
        this.side = event.side();
        this.quantity = event.quantity();
        this.price = event.price();
        this.occurredAt = event.occurredAt();
    }

    public TradeAuditEvent event() {
        return new TradeAuditEvent(eventId, eventType, occurredAt, orderId, accountId,
                clientId, clientEmail, clientName, instrument, side, quantity, price, status, reason);
    }

    public Long getOutboxId() { return outboxId; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public int getAttemptCount() { return attemptCount; }
    public String getLastError() { return lastError; }

    public void markPublished(OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
        this.lastError = null;
    }

    public void markFailure(String error) {
        this.attemptCount++;
        this.lastError = error == null ? "Kafka publish failed." : error.substring(0, Math.min(error.length(), 1000));
    }
}
