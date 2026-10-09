package com.neueda.leap.models;

import com.neueda.leap.entities.Order;
import com.neueda.leap.entities.OrderEvent;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderResponse(Integer orderId, Integer accountId, Integer instrumentId, String symbol,
        OrderSide side, BigDecimal quantity, BigDecimal indicativePrice, OffsetDateTime quoteAt,
        OffsetDateTime submittedAt, OrderStatus status, String reason) {
    public static OrderResponse from(Order order, OrderEvent event) {
        return new OrderResponse(order.getOrderId(), order.getAccount().getAccountId(),
                order.getInstrument().getInstrumentId(), order.getInstrument().getSymbol(),
                order.getSide(), order.getRequestedQuantity(), order.getSubmittedQuotePrice(),
                order.getSubmittedQuoteAt(), order.getSubmittedAt(), event.getStatus(),
                event.getReasonForStatusChange());
    }
}
