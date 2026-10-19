package com.neueda.leap.models;

import java.util.List;

public record MarketQuotesDto(List<MarketQuoteResultDto> quotes, String disclaimer) {
}
