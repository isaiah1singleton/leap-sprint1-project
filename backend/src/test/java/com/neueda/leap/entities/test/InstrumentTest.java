package com.neueda.leap.entities.test;

import com.neueda.leap.entities.Instrument;
import com.neueda.leap.enums.AssetClass;
import com.neueda.leap.enums.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class InstrumentTest {
    
    private Instrument apple;
    
    @BeforeEach
    void setUp() {
        this.apple = new Instrument(
                "NASDAQ",
                "AAPL",
                "Apple Inc.",
                AssetClass.EQUITY,
                Currency.USD
        );
    }
    
    @Test
    void newInstrumentDefaultsTradingDisabled() {
        assertFalse(apple.isTradable());
        assertNull(apple.getInstrumentId());
    }
    
    @Test
    void constructorNormalizesIdentifiersAndPreservesDetails() {
        Instrument instrument = new Instrument(
                "  nasdaq ",
                " aaPl  ",
                "Apple  Inc.  ",
                AssetClass.EQUITY,
                Currency.USD
        );
        
        assertAll(
                () -> assertEquals("NASDAQ", instrument.getMarket()),
                () -> assertEquals("AAPL", instrument.getSymbol()),
                () -> assertEquals("Apple Inc.", instrument.getInstrumentName()),
                () -> assertEquals(AssetClass.EQUITY, instrument.getAssetClass()),
                () -> assertEquals(Currency.USD, instrument.getQuoteCurrency())
        );
    }
    
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void rejectsMissingMarket(String market) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Instrument(
                        market,
                        "AAPL",
                        "Apple Inc.",
                        AssetClass.EQUITY,
                        Currency.USD
                )
        );
    }
    
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void rejectsMissingSymbol(String symbol) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Instrument(
                        "NASDAQ",
                        symbol,
                        "Apple Inc.",
                        AssetClass.EQUITY,
                        Currency.USD
                )
        );
    }
    
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void rejectsMissingName(String name) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Instrument(
                        "NASDAQ",
                        "AAPL",
                        name,
                        AssetClass.EQUITY,
                        Currency.USD
                )
        );
    }
    
    @Test
    void rejectsMissingAssetClass() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Instrument(
                        "NASDAQ",
                        "AAPL",
                        "Apple Inc.",
                        null,
                        Currency.USD
                )
        );
    }
    
    @Test
    void rejectsMissingQuoteCurrency() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Instrument(
                        "NASDAQ",
                        "AAPL",
                        "Apple Inc.",
                        AssetClass.EQUITY,
                        null
                )
        );
    }
    
    @Test
    void enablesTrading() {
        apple.enableTrading();
        
        assertTrue(apple.isTradable());
    }
    
    @Test
    void disablesTrading() {
        apple.enableTrading();
        
        apple.disableTrading();
        
        assertFalse(apple.isTradable());
    }
    
    @Test
    void representsRequiredLaunchMarkets() {
        Instrument fx = new Instrument(
                "FX",
                "EUR/USD",
                "Euro / US Dollar",
                AssetClass.FOREX,
                Currency.USD
        );
        
        Instrument ukEquity = new Instrument(
                "LSE",
                "VOD",
                "Vodafone Group",
                AssetClass.EQUITY,
                Currency.GBP
        );
        
        Instrument indianEquity = new Instrument(
                "NSE",
                "INFY",
                "Infosys",
                AssetClass.EQUITY,
                Currency.INR
        );
        
        Instrument crypto = new Instrument(
                "CRYPTO",
                "BTC/USD",
                "Bitcoin / US Dollar",
                AssetClass.CRYPTO,
                Currency.USD
        );
        
        assertAll(
                () -> assertEquals(
                        AssetClass.FOREX,
                        fx.getAssetClass()
                ),
                () -> assertEquals(
                        Currency.GBP,
                        ukEquity.getQuoteCurrency()
                ),
                () -> assertEquals(
                        Currency.INR,
                        indianEquity.getQuoteCurrency()
                ),
                () -> assertEquals(
                        AssetClass.CRYPTO,
                        crypto.getAssetClass()
                )
        );
    }
}