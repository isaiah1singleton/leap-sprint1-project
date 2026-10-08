package com.neueda.leap.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketQuoteResultDto(
        String symbol, MarketQuoteDto quote, String source, boolean stale, MarketErrorDto error) {
}
