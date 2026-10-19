package com.neueda.leap.models;

import com.neueda.leap.entities.Order;
import com.neueda.leap.entities.OrderEvent;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TradeAuditEvent(
        UUID eventId,
        String eventType,
        OffsetDateTime occurredAt,
        Integer orderId,
        Integer accountId,
        Integer clientId,
        String clientEmail,
        String clientName,
        String instrument,
        String side,
        BigDecimal quantity,
        BigDecimal price,
        String status,
        String reason
) {
    public static TradeAuditEvent from(Order order, OrderEvent event) {
        return new TradeAuditEvent(
                UUID.randomUUID(),
                "ORDER_STATUS_CHANGED",
                event.getOccurredAt(),
                order.getOrderId(),
                order.getAccount().getAccountId(),
                order.getAccount().getClient().getClientId(),
                order.getAccount().getClient().getEmail(),
                order.getAccount().getClient().getClientName(),
                order.getInstrument().getSymbol(),
                order.getSide().name(),
                order.getRequestedQuantity(),
                event.getDecisionQuotePrice(),
                event.getStatus().name(),
                event.getReasonForStatusChange()
        );
    }
}
