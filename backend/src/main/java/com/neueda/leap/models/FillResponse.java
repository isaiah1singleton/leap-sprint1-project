package com.neueda.leap.models;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import com.neueda.leap.models.MoneyResponse;
import com.neueda.leap.entities.Fill;

public record FillResponse(
    Integer fillId,
    Integer orderId,
    BigDecimal fillQuantity,
    MoneyResponse executionPrice,
    OffsetDateTime executionTime
) {
    public static FillResponse from(Fill fill) {
        return new FillResponse(
            fill.getFillId(),
            fill.getOrder().getOrderId(),
            fill.getFillQuantity(),
            new MoneyResponse(
                fill.getExecutionPrice(),
                fill.getExecutionCurrency()
            ),
            fill.getExecutionTime()
        );
    }
}