package com.neueda.leap.models;

import com.neueda.leap.enums.OrderSide;

import java.math.BigDecimal;

public record SubmitOrderRequest
        (
                int accountId,
                int instrumentId,
                OrderSide side,
                BigDecimal requestedQuantity,
                BigDecimal submittedQuotePrice
        )
{
}
