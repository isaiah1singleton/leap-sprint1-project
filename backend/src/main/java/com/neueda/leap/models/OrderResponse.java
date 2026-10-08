package com.neueda.leap.models;

import com.neueda.leap.entities.Order;

public record OrderResponse(
    Integer orderId
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getOrderId()
        );
    }
}
