package com.neueda.leap.models;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TradeLifecycleResponse(
        Integer orderId,
        String orderRef,
        Integer accountId,
        Integer clientId,
        String clientName,
        String clientEmail,
        String instrument,
        String side,
        BigDecimal quantity,
        OffsetDateTime submittedAt,
        List<LifecycleEvent> events,
        Execution execution,
        List<Settlement> settlements,
        boolean complete
) {
    public record LifecycleEvent(UUID eventId, String eventType, String status, String reason,
                                 BigDecimal price, OffsetDateTime occurredAt) { }

    public record Execution(BigDecimal price, BigDecimal quantity, OffsetDateTime executedAt) { }

    public record Settlement(Integer movementId, BigDecimal amount, String movementType,
                             String currency, String reason, OffsetDateTime occurredAt) { }
}