package com.neueda.leap.service;

import com.neueda.leap.models.TaxReport;
import com.neueda.leap.models.TaxReport.Lot;
import com.neueda.leap.models.TaxReport.Sale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TaxReportService {
    private static final Set<String> SUPPORTED_DOCUMENTS = Set.of("1099-B", "1099-DA");

    private static final String ALLOCATION_QUERY = """
            SELECT sf.fill_id, i.symbol, i.instrument_name, i.asset_class, i.quote_currency,
                   sf.execution_time sold, o.requested_quantity quantity, sf.execution_price sale_price,
                   fa.allocated_quantity, bf.execution_time acquired, bf.execution_price buy_price,
                   bo.account_id buy_account, bo.instrument_id buy_instrument, bo.side buy_side,
                   o.account_id sale_account, o.instrument_id sale_instrument
            FROM fills sf JOIN orders o ON o.order_id=sf.order_id
            JOIN accounts a ON a.account_id=o.account_id
            JOIN instruments i ON i.instrument_id=o.instrument_id
            LEFT JOIN fifo_allocations fa ON fa.sell_fill_id=sf.fill_id
            LEFT JOIN fills bf ON bf.fill_id=fa.buy_fill_id
            LEFT JOIN orders bo ON bo.order_id=bf.order_id
            WHERE a.client_id=? AND o.side='SELL'
              AND sf.execution_time>=? AND sf.execution_time<?
              AND ((?='1099-B' AND i.asset_class IN ('EQUITY','ETF'))
                OR (?='1099-DA' AND i.asset_class='CRYPTO' AND i.quote_currency='USD'))
            ORDER BY sf.execution_time, sf.fill_id, bf.execution_time, bf.fill_id
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public TaxReportService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public int currentYear() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC)).getYear();
    }

    // Read the trading engine's persisted allocations, never reconstruct a competing FIFO ledger.
    @Transactional(readOnly = true)
    public TaxReport report(int clientId, int year, String document) {
        validateRequest(clientId, year, document);

        List<Allocation> allocations = findAllocations(clientId, year, document);
        Map<Integer, List<Allocation>> allocationsBySale = groupBySale(allocations);
        List<Sale> sales = new ArrayList<>();
        ReportTotals totals = new ReportTotals();

        for (List<Allocation> saleAllocations : allocationsBySale.values()) {
            SaleResult result = buildSale(saleAllocations);
            sales.add(result.sale());
            totals.add(result);
        }

        return createReport(clientId, year, document, sales, totals);
    }

    private void validateRequest(int clientId, int year, String document) {
        if (clientId <= 0) {
            throw new IllegalArgumentException("An authenticated client is required.");
        }
        if (year < 1900 || year > currentYear()) {
            throw new IllegalArgumentException("Choose a year from 1900 through the current year.");
        }
        if (document == null || !SUPPORTED_DOCUMENTS.contains(document)) {
            throw new IllegalArgumentException("Unsupported document.");
        }
    }

    private List<Allocation> findAllocations(int clientId, int year, String document) {
        OffsetDateTime start = LocalDate.of(year, 1, 1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return jdbc.query(ALLOCATION_QUERY, this::mapAllocation,
                clientId, start, start.plusYears(1), document, document);
    }

    private Allocation mapAllocation(ResultSet row, int rowNumber) throws SQLException {
        boolean validBuy = "BUY".equals(row.getString("buy_side"))
                && row.getInt("buy_account") == row.getInt("sale_account")
                && row.getInt("buy_instrument") == row.getInt("sale_instrument");

        return new Allocation(
                row.getInt("fill_id"),
                row.getString("symbol"),
                row.getString("instrument_name"),
                row.getString("asset_class"),
                row.getString("quote_currency"),
                row.getObject("sold", OffsetDateTime.class),
                row.getBigDecimal("quantity"),
                row.getBigDecimal("sale_price"),
                row.getBigDecimal("allocated_quantity"),
                row.getObject("acquired", OffsetDateTime.class),
                row.getBigDecimal("buy_price"),
                validBuy);
    }

    private Map<Integer, List<Allocation>> groupBySale(List<Allocation> allocations) {
        Map<Integer, List<Allocation>> grouped = new LinkedHashMap<>();
        for (Allocation allocation : allocations) {
            grouped.computeIfAbsent(allocation.fillId(), ignored -> new ArrayList<>()).add(allocation);
        }
        return grouped;
    }

    private SaleResult buildSale(List<Allocation> allocations) {
        Allocation sale = allocations.get(0);
        BigDecimal proceeds = amount(sale.quantity().multiply(sale.salePrice()));

        if (!hasCompleteBasis(allocations, sale)) {
            return new SaleResult(toSale(sale, proceeds, null, null, List.of()),
                    BigDecimal.ZERO, BigDecimal.ZERO, 1);
        }

        LotCalculation lotCalculation = calculateLots(allocations);
        BigDecimal gain = proceeds.subtract(lotCalculation.basis());
        Sale completedSale = toSale(sale, proceeds, lotCalculation.basis(), gain, lotCalculation.lots());

        return new SaleResult(completedSale, gain.max(BigDecimal.ZERO),
                gain.min(BigDecimal.ZERO).negate(), 0);
    }

    private boolean hasCompleteBasis(List<Allocation> allocations, Allocation sale) {
        boolean everyLotIsValid = allocations.stream().allMatch(allocation ->
                allocation.validBuy()
                        && allocation.allocated() != null
                        && allocation.allocated().signum() > 0
                        && allocation.acquired() != null
                        && allocation.buyPrice() != null
                        && !allocation.acquired().isAfter(allocation.sold()));

        if (!everyLotIsValid) {
            return false;
        }

        BigDecimal allocatedQuantity = allocations.stream()
                .map(Allocation::allocated)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return allocatedQuantity.compareTo(sale.quantity()) == 0;
    }

    private LotCalculation calculateLots(List<Allocation> allocations) {
        List<Lot> lots = new ArrayList<>();
        BigDecimal basis = BigDecimal.ZERO;
        BigDecimal assignedProceeds = BigDecimal.ZERO;
        BigDecimal assignedQuantity = BigDecimal.ZERO;

        for (Allocation allocation : allocations) {
            LocalDate acquired = utcDate(allocation.acquired());
            LocalDate sold = utcDate(allocation.sold());
            BigDecimal cost = amount(allocation.allocated().multiply(allocation.buyPrice()));

            // Cumulative rounding reconciles to settlement without negative tiny-lot proceeds.
            assignedQuantity = assignedQuantity.add(allocation.allocated());
            BigDecimal lotProceeds = amount(assignedQuantity.multiply(allocation.salePrice()))
                    .subtract(assignedProceeds);
            assignedProceeds = assignedProceeds.add(lotProceeds);
            basis = basis.add(cost);

            String holdingPeriod = sold.isAfter(acquired.plusYears(1)) ? "Long-term" : "Short-term";
            lots.add(new Lot(acquired, holdingPeriod, allocation.allocated(),
                    lotProceeds, cost, lotProceeds.subtract(cost)));
        }

        return new LotCalculation(List.copyOf(lots), basis);
    }

    private Sale toSale(Allocation allocation, BigDecimal proceeds, BigDecimal basis,
                        BigDecimal gain, List<Lot> lots) {
        return new Sale(allocation.fillId(), allocation.symbol(), allocation.description(),
                allocation.assetClass(), allocation.currency(), utcDate(allocation.sold()),
                allocation.quantity(), proceeds, basis, gain, lots);
    }

    private TaxReport createReport(int clientId, int year, String document,
                                   List<Sale> sales, ReportTotals totals) {
        List<String> currencies = sales.stream().map(Sale::currency).distinct().toList();
        boolean mixedCurrencies = currencies.size() > 1;
        String currency = mixedCurrencies ? "Mixed" : currencies.isEmpty() ? "USD" : currencies.get(0);

        return new TaxReport(year, document, "Client " + clientId, currency, List.copyOf(sales),
                mixedCurrencies ? null : amount(totals.gains),
                mixedCurrencies ? null : amount(totals.losses),
                mixedCurrencies ? null : amount(totals.gains.subtract(totals.losses)),
                totals.unavailableBasisCount);
    }

    private static LocalDate utcDate(OffsetDateTime date) {
        return date.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
    }

    private static BigDecimal amount(BigDecimal value) {
        return value.setScale(8, RoundingMode.HALF_EVEN);
    }

    private record Allocation(int fillId, String symbol, String description, String assetClass,
                              String currency, OffsetDateTime sold, BigDecimal quantity, BigDecimal salePrice,
                              BigDecimal allocated, OffsetDateTime acquired, BigDecimal buyPrice,
                              boolean validBuy) { }

    private record LotCalculation(List<Lot> lots, BigDecimal basis) { }

    private record SaleResult(Sale sale, BigDecimal gains, BigDecimal losses,
                              int unavailableBasisCount) { }

    private static final class ReportTotals {
        private BigDecimal gains = BigDecimal.ZERO;
        private BigDecimal losses = BigDecimal.ZERO;
        private int unavailableBasisCount;

        private void add(SaleResult result) {
            gains = gains.add(result.gains());
            losses = losses.add(result.losses());
            unavailableBasisCount += result.unavailableBasisCount();
        }
    }
}
