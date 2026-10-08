package com.neueda.leap.models;

import com.neueda.leap.entities.OrderEvent;
import com.neueda.leap.enums.OrderStatus;

import java.time.OffsetDateTime;

public record OrderEventResponse(
        Integer orderEventId,
        Integer orderId,
        OrderStatus status,
        String reasonForStatusChange,
        QuoteSnapshotResponse decisionQuote,
        OffsetDateTime decisionQuoteAt,
        OffsetDateTime occurredAt
) {

    public static OrderEventResponse from(OrderEvent event) {
        QuoteSnapshotResponse quote = null;

        if (event.getDecisionQuotePrice() != null) {
            quote = new QuoteSnapshotResponse(
                    new MoneyResponse(
                            event.getDecisionQuotePrice(),
                            event.getOrder().getInstrument().getQuoteCurrency()
                        ),
                    event.getDecisionQuoteAt()
            );
        }

        return new OrderEventResponse(
                event.getOrderEventId(),
                event.getOrder().getOrderId(),
                event.getStatus(),
                event.getReasonForStatusChange(),
                quote,
                event.getDecisionQuoteAt(),
                event.getOccurredAt()
        );
    }
}
