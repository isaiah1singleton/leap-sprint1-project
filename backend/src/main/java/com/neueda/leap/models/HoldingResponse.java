package com.neueda.leap.models;

import com.neueda.leap.entities.Holding;
import java.math.BigDecimal;

public record HoldingResponse(Integer instrumentId, String symbol, String name, BigDecimal totalQuantity,
        BigDecimal reservedQuantity, BigDecimal availableQuantity) {
    public static HoldingResponse from(Holding holding) {
        return new HoldingResponse(holding.getInstrument().getInstrumentId(), holding.getInstrument().getSymbol(),
                holding.getInstrument().getInstrumentName(), holding.getTotalQuantity(),
                holding.getReservedQuantity(), holding.availableQuantity());
    }
}
