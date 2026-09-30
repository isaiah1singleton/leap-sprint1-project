package com.neueda.leap.models;

import com.neueda.leap.entities.Instrument;
import com.neueda.leap.enums.AssetClass;
import com.neueda.leap.enums.Currency;

public record InstrumentResponse(
        Integer instrumentId,
        String market,
        String symbol,
        String instrumentName,
        AssetClass assetClass,
        boolean isTradable,
        Currency quoteCurrency
) {
    
    public static InstrumentResponse from(Instrument instrument) {
        return new InstrumentResponse(
                instrument.getInstrumentId(),
                instrument.getMarket(),
                instrument.getSymbol(),
                instrument.getInstrumentName(),
                instrument.getAssetClass(),
                instrument.isTradable(),
                instrument.getQuoteCurrency()
        );
    }
}
