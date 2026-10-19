package com.neueda.leap.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketSymbolDto(Integer instrumentId, String symbol, String name, String type, String exchange, String currency) {
}
