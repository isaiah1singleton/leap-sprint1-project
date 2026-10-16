package com.neueda.leap.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Null monetary values mean unavailable; zero is a valid calculated value. */
public record PortfolioSummaryResponse(
        Integer accountId,
        String currency,
        BigDecimal cashBalance,
        BigDecimal estimatedPortfolioValue,
        BigDecimal buyingPower,
        BigDecimal investedAssetsValue,
        BigDecimal lifetimeProfitLoss,
        String buyingPowerUnavailableReason,
        String profitLossUnavailableReason,
        Instant accountAsOf,
        Instant oldestQuoteAt,
        List<String> valuationWarnings,
        List<PortfolioHoldingValue> holdings
) { }
