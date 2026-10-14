package com.neueda.leap.models;

import com.neueda.leap.enums.OrderSide;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SubmitOrderRequest(
        Integer accountId,
        Integer instrumentId,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal indicativePrice,
        OffsetDateTime quoteAsOf,
        String quoteCurrency) {
}
