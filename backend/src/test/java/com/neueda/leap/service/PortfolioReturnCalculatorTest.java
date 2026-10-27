package com.neueda.leap.service;

import com.neueda.leap.models.PortfolioHistoryResponse.Point;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioReturnCalculatorTest {
    private static final Instant START = Instant.parse("2026-10-20T12:00:00Z");

    @Test
    void withdrawalsAndDepositsDoNotCreatePerformance() {
        var points = List.of(point(0, "100", "0"), point(10, "120", "20"),
                point(20, "30", "20"), point(30, "130", "20"));
        var flows = List.of(flow(15, "-90"), flow(25, "100"));

        var result = PortfolioReturnCalculator.calculate(points, flows);

        assertThat(result).extracting(Point::returnPercent)
                .containsExactly(new BigDecimal("0.00"), new BigDecimal("20.00"),
                        new BigDecimal("20.00"), new BigDecimal("20.00"));
    }

    @Test
    void unavailableValuationsBreakTheSeriesWithoutInventingReturns() {
        var points = List.of(point(0, "0", "0"), point(10, "100", "0"),
                point(20, null, null), point(30, "90", "-10"));

        var result = PortfolioReturnCalculator.calculate(points, List.of());

        assertThat(result).extracting(Point::returnPercent)
                .containsExactly(null, new BigDecimal("0.00"), null, new BigDecimal("-10.00"));
    }

    private static Point point(int minutes, String value, String profit) {
        return new Point(START.plusSeconds(minutes * 60L), BigDecimal.ZERO, null,
                value == null ? null : new BigDecimal(value),
                profit == null ? null : new BigDecimal(profit), null, null,
                value == null ? "Price unavailable" : null,
                profit == null ? "Profit unavailable" : null);
    }

    private static PortfolioReturnCalculator.CashFlow flow(int minutes, String amount) {
        return new PortfolioReturnCalculator.CashFlow(START.plusSeconds(minutes * 60L), new BigDecimal(amount));
    }
}
