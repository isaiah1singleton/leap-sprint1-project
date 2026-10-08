package com.neueda.leap.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketQuoteDto(
        String symbol, BigDecimal price, BigDecimal bid, BigDecimal ask, BigDecimal spreadBps,
        String currency, BigDecimal change, BigDecimal changePercent, BigDecimal previousClose,
        String asOf, String marketState) {
}
