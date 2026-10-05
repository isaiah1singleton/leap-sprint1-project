package com.neueda.leap.entities;

import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class Order {

    private Integer orderId;
    private Integer instrumentId;
    private OrderSide side;
    private BigDecimal RequestedQuantity;
    private OffsetDateTime submittedAt;
    private OrderStatus currentOrderStatus;

    public OrderStatus submit(){
        return OrderStatus.SUBMITTED;
    }

    public OrderStatus accept(){
        return OrderStatus.ACCEPTED;
    }

    public OrderStatus currentStatus(){
        return currentOrderStatus;
    }
}
