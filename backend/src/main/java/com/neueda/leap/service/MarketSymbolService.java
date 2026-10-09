package com.neueda.leap.service;

import com.neueda.leap.entities.Instrument;
import com.neueda.leap.models.MarketSymbolDto;
import com.neueda.leap.repository.InstrumentRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/** Searchable instruments are the tradable instruments persisted in PostgreSQL. */
@Service
public class MarketSymbolService {
    private final InstrumentRepository instruments;

    public MarketSymbolService(InstrumentRepository instruments) {
        this.instruments = instruments;
    }

    public List<MarketSymbolDto> search(String query, int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Limit must be between 1 and 1000.");
        }
        String term = query == null ? "" : query.trim().toUpperCase(Locale.ROOT);
        if (term.length() > 100) throw new IllegalArgumentException("Search must be at most 100 characters.");
        return instruments.findAllByOrderByMarketAscSymbolAsc().stream()
                .filter(Instrument::isTradable)
                .filter(item -> item.getSymbol().contains(term)
                        || item.getInstrumentName().toUpperCase(Locale.ROOT).contains(term))
                .map(MarketSymbolService::toDto)
                .sorted(Comparator.comparingInt((MarketSymbolDto item) -> item.symbol().equals(term) ? 0
                        : item.symbol().startsWith(term) ? 1 : 2).thenComparing(MarketSymbolDto::symbol))
                .limit(limit).toList();
    }

    public MarketSymbolDto requireSymbol(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        return instruments.findFirstBySymbolIgnoreCase(normalized).map(MarketSymbolService::toDto)
                .orElseThrow(() -> new NoSuchElementException("Symbol is not in the instrument database."));
    }

    private static MarketSymbolDto toDto(Instrument item) {
        return new MarketSymbolDto(item.getInstrumentId(), item.getSymbol(), item.getInstrumentName(),
                item.getAssetClass().name().toLowerCase(Locale.ROOT), item.getMarket(),
                item.getQuoteCurrency().name());
    }
}
