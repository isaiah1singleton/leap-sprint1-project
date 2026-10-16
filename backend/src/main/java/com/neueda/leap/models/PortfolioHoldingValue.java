package com.neueda.leap.models;

import java.math.BigDecimal;
import java.time.Instant;

/** A current holding with a nullable valuation when its quote is unavailable. */
public record PortfolioHoldingValue(
        Integer instrumentId,
        String symbol,
        String name,
        BigDecimal quantity,
        BigDecimal reservedQuantity,
        BigDecimal availableQuantity,
        BigDecimal quotePrice,
        BigDecimal estimatedValue,
        Instant quoteAsOf
) { }
