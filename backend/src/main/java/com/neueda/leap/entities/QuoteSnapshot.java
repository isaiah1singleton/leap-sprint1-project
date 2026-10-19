package com.neueda.leap.entities;

import java.time.OffsetDateTime;
import java.util.Objects;

public record QuoteSnapshot(Money price, OffsetDateTime quoteAt) {
    public QuoteSnapshot {
        Objects.requireNonNull(price, "Price is required.");
        Objects.requireNonNull(quoteAt, "Quote time is required.");
    }
}
