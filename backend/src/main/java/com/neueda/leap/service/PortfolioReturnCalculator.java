package com.neueda.leap.service;

import com.neueda.leap.models.PortfolioHistoryResponse.Point;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Compounds Modified Dietz returns between recorded portfolio valuations. */
final class PortfolioReturnCalculator {
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    record CashFlow(Instant occurredAt, BigDecimal amount) { }

    private PortfolioReturnCalculator() { }

    static List<Point> calculate(List<Point> points, List<CashFlow> flows) {
        List<Point> result = new ArrayList<>(points.size());
        Point previous = null;
        BigDecimal growth = BigDecimal.ONE;
        int flowIndex = 0;

        for (Point point : points) {
            if (point.portfolioValue() == null || point.lifetimeProfitLoss() == null) {
                result.add(point.withReturnPercent(null));
                continue;
            }

            if (previous == null) {
                while (flowIndex < flows.size() && !flows.get(flowIndex).occurredAt().isAfter(point.observedAt())) {
                    flowIndex++;
                }
                if (point.portfolioValue().signum() <= 0) {
                    result.add(point.withReturnPercent(null));
                    continue;
                }
                previous = point;
                result.add(point.withReturnPercent(BigDecimal.ZERO.setScale(2)));
                continue;
            }

            long periodMillis = Duration.between(previous.observedAt(), point.observedAt()).toMillis();
            if (periodMillis <= 0) {
                result.add(point.withReturnPercent(null));
                continue;
            }

            BigDecimal netFlow = BigDecimal.ZERO;
            BigDecimal weightedFlow = BigDecimal.ZERO;
            int nextFlowIndex = flowIndex;
            while (nextFlowIndex < flows.size() && !flows.get(nextFlowIndex).occurredAt().isAfter(point.observedAt())) {
                CashFlow flow = flows.get(nextFlowIndex++);
                if (!flow.occurredAt().isAfter(previous.observedAt())) continue;
                netFlow = netFlow.add(flow.amount());
                long remainingMillis = Duration.between(flow.occurredAt(), point.observedAt()).toMillis();
                BigDecimal weight = BigDecimal.valueOf(remainingMillis)
                        .divide(BigDecimal.valueOf(periodMillis), PRECISION);
                weightedFlow = weightedFlow.add(flow.amount().multiply(weight, PRECISION));
            }

            BigDecimal denominator = previous.portfolioValue().add(weightedFlow);
            if (denominator.signum() <= 0) {
                result.add(point.withReturnPercent(null));
                continue;
            }

            BigDecimal profit = point.portfolioValue().subtract(previous.portfolioValue()).subtract(netFlow);
            BigDecimal periodGrowth = BigDecimal.ONE.add(profit.divide(denominator, PRECISION));
            if (periodGrowth.signum() < 0) {
                result.add(point.withReturnPercent(null));
                continue;
            }
            growth = growth.multiply(periodGrowth, PRECISION);
            flowIndex = nextFlowIndex;
            previous = point;
            result.add(point.withReturnPercent(growth.subtract(BigDecimal.ONE)
                    .multiply(HUNDRED).setScale(2, RoundingMode.HALF_EVEN)));
        }
        return List.copyOf(result);
    }
}
