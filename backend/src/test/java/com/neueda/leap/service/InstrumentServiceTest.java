package com.neueda.leap.service;

import com.neueda.leap.entities.Instrument;
import com.neueda.leap.enums.AssetClass;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.models.InstrumentResponse;
import com.neueda.leap.repository.InstrumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InstrumentServiceTest {
    @Mock
    private InstrumentRepository repository;
    
    private InstrumentService service;
    
    private Instrument mockStoredInstrument(
            Integer id,
            String symbol,
            String name,
            boolean tradable
    ) {
        Instrument instrument = mock(Instrument.class);
        
        when(instrument.getInstrumentId()).thenReturn(id);
        when(instrument.getMarket()).thenReturn("NASDAQ");
        when(instrument.getSymbol()).thenReturn(symbol);
        when(instrument.getInstrumentName()).thenReturn(name);
        when(instrument.getAssetClass()).thenReturn(AssetClass.EQUITY);
        when(instrument.isTradable()).thenReturn(tradable);
        when(instrument.getQuoteCurrency()).thenReturn(Currency.USD);
        
        return instrument;
    }
    
    private Instrument apple() {
        return new Instrument(
                "NASDAQ",
                "AAPL",
                "Apple Inc.",
                AssetClass.EQUITY,
                Currency.USD
        );
    }
    
    @BeforeEach
    void setUp() {
        service = new InstrumentService(repository);
    }
    
    @Test
    void returnsDetailsForAnExistingDisabledInstrument() {
        Instrument instrument = mockStoredInstrument(
                7,
                "AAPL",
                "Apple Inc.",
                false
        );
        
        when(repository.findById(7)).thenReturn(Optional.of(instrument));
        
        InstrumentResponse actual = service.getInstrument(7);
        InstrumentResponse expected = new InstrumentResponse(
                7,
                "NASDAQ",
                "AAPL",
                "Apple Inc.",
                AssetClass.EQUITY,
                false,
                Currency.USD
        );
        
        assertEquals(expected, actual);
    }
    
    @Test
    void returnsAnEmptyCatalogue() {
        when(repository.findAllByOrderByMarketAscSymbolAsc())
                .thenReturn(List.of());
        
        List<InstrumentResponse> actual = service.getInstruments();
        
        assertTrue(actual.isEmpty());
    }
    
    @Test
    void mapsAllCatalogueEntriesAndPreservesRepositoryOrder() {
        Instrument apple = mockStoredInstrument(
                7,
                "AAPL",
                "Apple Inc.",
                false
        );
        
        Instrument microsoft = mockStoredInstrument(
                8,
                "MSFT",
                "Microsoft",
                true
        );
        
        when(repository.findAllByOrderByMarketAscSymbolAsc())
                .thenReturn(List.of(apple, microsoft));
        
        List<InstrumentResponse> actual = service.getInstruments();
        
        List<InstrumentResponse> expected = List.of(
                new InstrumentResponse(
                        7,
                        "NASDAQ",
                        "AAPL",
                        "Apple Inc.",
                        AssetClass.EQUITY,
                        false,
                        Currency.USD
                ),
                new InstrumentResponse(
                        8,
                        "NASDAQ",
                        "MSFT",
                        "Microsoft",
                        AssetClass.EQUITY,
                        true,
                        Currency.USD
                )
        );
        
        assertEquals(expected, actual);
    }
    
    @Test
    void returnsEnabledInstrumentForTrading() {
        Instrument instrument = apple();
        instrument.enableTrading();
        
        when(repository.findById(7))
                .thenReturn(Optional.of(instrument));
        
        Instrument actual = service.requireTradableInstrument(7);
        
        assertSame(instrument, actual);
    }
    
    @Test
    void rejectsDisabledInstrumentForTrading() {
        Instrument instrument = apple();
        
        when(repository.findById(7))
                .thenReturn(Optional.of(instrument));
        
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.requireTradableInstrument(7)
        );
        
        assertEquals(
                "Instrument is not currently tradable.",
                exception.getMessage()
        );
    }
    
    @Test
    void rejectsUnknownInstrumentForTrading() {
        when(repository.findById(7))
                .thenReturn(Optional.empty());
        
        NoSuchElementException exception = assertThrows(
                NoSuchElementException.class,
                () -> service.requireTradableInstrument(7)
        );
        
        assertEquals(
                "Instrument not found.",
                exception.getMessage()
        );
    }
    
    @Test
    void rejectsUnknownInstrument() {
        when(repository.findById(7)).thenReturn(Optional.empty());
        
        NoSuchElementException exception = assertThrows(NoSuchElementException.class, () -> service.getInstrument(7));
        
        assertEquals("Instrument not found.", exception.getMessage());
    }
    
    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1})
    void testInvalidLookupIdsWithoutQuerying(Integer id) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.getInstrument(id));
        
        assertEquals("Instrument ID must be positive.", exception.getMessage());
        
        verifyNoInteractions(repository);
    }
}
