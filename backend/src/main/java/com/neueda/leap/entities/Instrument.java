package com.neueda.leap.entities;

import com.neueda.leap.enums.AssetClass;
import com.neueda.leap.enums.Currency;
import jakarta.persistence.*;

import java.util.Locale;

@Entity
@Table(
        name = "instruments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_instruments_market_symbol",
                columnNames = {"market", "symbol"}
        )
)
public class Instrument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "instrument_id")
    private Integer instrumentId;
    
    @Column(name = "market", nullable = false, columnDefinition = "text")
    private String market;
    
    @Column(name = "symbol", nullable = false, columnDefinition = "text")
    private String symbol;
    
    @Column(
            name = "instrument_name",
            nullable = false,
            columnDefinition = "text"
    )
    private String instrumentName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "asset_class", nullable = false, columnDefinition = "text")
    private AssetClass assetClass;
    
    @Column(name = "is_tradable", nullable = false)
    private boolean tradable;
    
    @Enumerated(EnumType.STRING)
    @Column(
            name = "quote_currency",
            nullable = false,
            columnDefinition = "text"
    )
    private Currency quoteCurrency;
    
    protected Instrument() { }
    
    public Instrument(
            String market,
            String symbol,
            String instrumentName,
            AssetClass assetClass,
            Currency quoteCurrency
    ) {
        this.market = requireText(market, "Market").toUpperCase(Locale.ROOT);
        this.symbol = requireText(symbol, "Symbol").toUpperCase(Locale.ROOT);
        this.instrumentName = requireText(instrumentName, "Instrument name");
        
        if (assetClass == null) {
            throw new IllegalArgumentException("Asset class is required.");
        }
        
        if (quoteCurrency == null) {
            throw new IllegalArgumentException("Quote currency is required.");
        }
        
        this.assetClass = assetClass;
        this.quoteCurrency = quoteCurrency;
        this.tradable = false;
    }
    
    public Integer getInstrumentId() {
        return instrumentId;
    }
    
    public String getMarket() {
        return market;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public String getInstrumentName() {
        return instrumentName;
    }
    
    public AssetClass getAssetClass() {
        return assetClass;
    }
    
    public Currency getQuoteCurrency() {
        return quoteCurrency;
    }
    
    public boolean isTradable() {
        return tradable;
    }
    
    public void enableTrading() {
        tradable = true;
    }
    
    public void disableTrading() {
        tradable = false;
    }
    
    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}
