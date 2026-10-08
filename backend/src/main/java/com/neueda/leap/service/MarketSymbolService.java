package com.neueda.leap.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.models.MarketSymbolDto;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** The curated files are the only supported search and quote universe. */
@Service
public class MarketSymbolService {
    private final Map<String, MarketSymbolDto> symbols;

    public MarketSymbolService(ObjectMapper mapper) throws IOException {
        Map<String, MarketSymbolDto> catalogue = new LinkedHashMap<>();
        for (String resource : List.of("us-v1.json", "crypto-us.json")) {
            try (var input = new ClassPathResource(resource).getInputStream()) {
                for (var node : mapper.readTree(input).withArray("symbols")) {
                    MarketSymbolDto symbol = mapper.treeToValue(node, MarketSymbolDto.class);
                    catalogue.put(symbol.symbol(), symbol);
                }
            }
        }
        symbols = Map.copyOf(catalogue);
    }

    public List<MarketSymbolDto> search(String query, int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Limit must be between 1 and 1000.");
        }
        String term = query == null ? "" : query.trim().toUpperCase(Locale.ROOT);
        if (term.length() > 100) throw new IllegalArgumentException("Search must be at most 100 characters.");
        return symbols.values().stream()
                .filter(item -> item.symbol().contains(term) || item.name().toUpperCase(Locale.ROOT).contains(term))
                .sorted(Comparator.comparingInt((MarketSymbolDto item) -> item.symbol().equals(term) ? 0
                        : item.symbol().startsWith(term) ? 1 : 2).thenComparing(MarketSymbolDto::symbol))
                .limit(limit).toList();
    }

    public MarketSymbolDto requireSymbol(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        MarketSymbolDto result = symbols.get(normalized);
        if (result == null) throw new NoSuchElementException("Symbol is not in the US or crypto catalogue.");
        return result;
    }
}
