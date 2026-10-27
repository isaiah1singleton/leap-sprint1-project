package com.neueda.leap.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PortfolioHistoryResponse(Integer accountId, String currency, Instant recordingStartedAt,
        String range, List<Point> points) {
    public record Point(Instant observedAt, BigDecimal cashBalance, BigDecimal investedValue,
            BigDecimal portfolioValue, BigDecimal lifetimeProfitLoss, BigDecimal returnPercent,
            Instant oldestQuoteAt, String valueUnavailableReason, String profitLossUnavailableReason) {
        public Point withReturnPercent(BigDecimal percent) {
            return new Point(observedAt, cashBalance, investedValue, portfolioValue,
                    lifetimeProfitLoss, percent, oldestQuoteAt, valueUnavailableReason,
                    profitLossUnavailableReason);
        }
    }
}
