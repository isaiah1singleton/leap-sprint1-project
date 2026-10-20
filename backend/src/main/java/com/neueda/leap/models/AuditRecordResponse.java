package com.neueda.leap.models;

import com.neueda.leap.entities.AuditRecord;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditRecordResponse(
        Long auditId,
        UUID eventId,
        String orderRef,
        String eventType,
        Integer orderId,
        Integer accountId,
        Integer clientId,
        String clientName,
        String clientEmail,
        String instrument,
        String side,
        BigDecimal quantity,
        BigDecimal price,
        String status,
        String reason,
        OffsetDateTime occurredAt,
        boolean suspicious
) {
    public static AuditRecordResponse from(AuditRecord record) {
        return new AuditRecordResponse(record.getAuditRecordId(), record.getEventId(),
                "ORD-" + record.getOrderId(), record.getEventType(), record.getOrderId(),
                record.getAccountId(), record.getClientId(), record.getClientName(),
                record.getClientEmail(), record.getInstrument(), record.getSide(),
                record.getQuantity(), record.getPrice(), record.getStatus(), record.getReason(),
                record.getOccurredAt(), false);
    }
}
