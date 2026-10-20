package com.neueda.leap.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TaxReport(int year, String document, String clientReference, String currency, List<Sale> sales,
                        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal gains,
                        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal losses,
                        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal net,
                        int unavailableBasisCount) {
    public record Lot(LocalDate acquired, String holdingPeriod,
                      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal quantity,
                      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal proceeds,
                      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal basis,
                      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal gain) { }
    public record Sale(int fillId, String symbol, String description, String assetClass, String currency, LocalDate sold,
                       @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal quantity,
                       @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal proceeds,
                       @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal basis,
                       @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal gain,
                       List<Lot> lots) { }
}
