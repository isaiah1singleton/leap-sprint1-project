package com.neueda.leap.entities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** A committed business event waiting to be published to Kafka. */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    public static final String ORDER_ACCEPTED = "OrderAccepted";

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, updatable = false, columnDefinition = "text")
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, updatable = false, columnDefinition = "text")
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false, columnDefinition = "text")
    private String aggregateId;

    @Column(name = "schema_version", nullable = false, updatable = false)
    private int schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(String eventType, String aggregateType, String aggregateId,
                       int schemaVersion, JsonNode payload) {
        this(UUID.randomUUID(), eventType, aggregateType, aggregateId, schemaVersion, payload);
    }

    private OutboxEvent(UUID eventId, String eventType, String aggregateType, String aggregateId,
                        int schemaVersion, JsonNode payload) {
        this.eventId = eventId;
        this.eventType = requireText(eventType, "Event type");
        this.aggregateType = requireText(aggregateType, "Aggregate type");
        this.aggregateId = requireText(aggregateId, "Aggregate ID");
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Schema version must be positive.");
        }
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("Outbox payload must be a JSON object.");
        }
        this.schemaVersion = schemaVersion;
        this.payload = payload.deepCopy();
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /** Payload contract for the first Kafka execution command. */
    public static OutboxEvent orderAccepted(Integer orderId) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("A persisted order ID is required.");
        }
        UUID eventId = UUID.randomUUID();
        JsonNode payload = JsonNodeFactory.instance.objectNode()
                .put("eventId", eventId.toString())
                .put("orderId", orderId)
                .put("schemaVersion", 1);
        return new OutboxEvent(eventId, ORDER_ACCEPTED, "Order", orderId.toString(), 1, payload);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value.trim();
    }

    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public int getSchemaVersion() { return schemaVersion; }
    public JsonNode getPayload() { return payload.deepCopy(); }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }

    public void markPublished(OffsetDateTime acknowledgedAt) {
        if (acknowledgedAt == null || acknowledgedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Acknowledgement time must follow creation.");
        }
        if (publishedAt != null) {
            throw new IllegalStateException("Outbox event is already published.");
        }
        publishedAt = acknowledgedAt;
    }
}
