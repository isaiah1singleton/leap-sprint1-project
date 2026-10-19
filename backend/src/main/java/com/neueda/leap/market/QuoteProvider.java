package com.neueda.leap.market;

import com.neueda.leap.models.MarketQuoteResultDto;

import java.util.List;

public interface QuoteProvider {

    MarketQuoteResultDto fetchQuote(String symbol);

    List<MarketQuoteResultDto> fetchQuotes(List<String> symbols);
}
