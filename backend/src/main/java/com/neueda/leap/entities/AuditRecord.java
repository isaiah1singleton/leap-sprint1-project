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
@Table(name = "audit_records")
public class AuditRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_record_id")
    private Long auditRecordId;
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
    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    protected AuditRecord() { }

    public AuditRecord(TradeAuditEvent event, OffsetDateTime receivedAt) {
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
        this.receivedAt = receivedAt;
    }

    public Long getAuditRecordId() { return auditRecordId; }
    public UUID getEventId() { return eventId; }
    public Integer getOrderId() { return orderId; }
    public String getEventType() { return eventType; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public Integer getAccountId() { return accountId; }
    public Integer getClientId() { return clientId; }
    public String getClientEmail() { return clientEmail; }
    public String getClientName() { return clientName; }
    public String getInstrument() { return instrument; }
    public String getSide() { return side; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
}
