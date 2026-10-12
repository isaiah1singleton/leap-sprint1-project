package com.neueda.leap.models;

import java.time.OffsetDateTime;

public record QuoteSnapshotResponse(
        MoneyResponse price,
        OffsetDateTime quoteAt
) {
}
