package com.neueda.leap.repository;

import com.neueda.leap.entities.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InstrumentRepository
        extends JpaRepository<Instrument, Integer> {
    
    List<Instrument> findAllByOrderByMarketAscSymbolAsc();
    Optional<Instrument> findFirstBySymbolIgnoreCase(String symbol);
}
