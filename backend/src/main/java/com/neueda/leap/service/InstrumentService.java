package com.neueda.leap.service;

import com.neueda.leap.entities.Instrument;
import com.neueda.leap.models.InstrumentResponse;
import com.neueda.leap.repository.InstrumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class InstrumentService {
    
    private final InstrumentRepository instrumentRepository;
    
    public InstrumentService(
            InstrumentRepository instrumentRepository
    ) {
        this.instrumentRepository = instrumentRepository;
    }
    
    public List<InstrumentResponse> getInstruments() {
        return instrumentRepository
                .findAllByOrderByMarketAscSymbolAsc()
                .stream()
                .map(InstrumentResponse::from)
                .toList();
    }
    
    public InstrumentResponse getInstrument(Integer instrumentId) {
        Instrument instrument = findRequired(instrumentId);
        
        return InstrumentResponse.from(instrument);
    }
    
    public Instrument requireTradableInstrument(Integer instrumentId) {
        Instrument instrument = findRequired(instrumentId);
        
        if (!instrument.isTradable()) {
            throw new IllegalStateException(
                    "Instrument is not currently tradable."
            );
        }
        
        return instrument;
    }
    
    private Instrument findRequired(Integer instrumentId) {
        if (instrumentId == null || instrumentId <= 0) {
            throw new IllegalArgumentException(
                    "Instrument ID must be positive."
            );
        }
        
        return instrumentRepository.findById(instrumentId)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Instrument not found."
                        )
                );
    }
}
