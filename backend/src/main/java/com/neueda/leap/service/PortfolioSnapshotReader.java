package com.neueda.leap.service;

import com.neueda.leap.enums.Currency;
import com.neueda.leap.enums.CashMovementType;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Read account state and history consistently before making any provider requests. */
@Service
public class PortfolioSnapshotReader {
    private final AccountRepository accounts;
    private final CashBalanceRepository balances;
    private final HoldingRepository holdings;
    private final CashMovementRepository movements;
    private final FillRepository fills;
    private final OrderRepository orders;
    private final Clock clock;

    public PortfolioSnapshotReader(AccountRepository accounts, CashBalanceRepository balances,
            HoldingRepository holdings, CashMovementRepository movements, FillRepository fills,
            OrderRepository orders, Clock clock) {
        this.accounts = accounts;
        this.balances = balances;
        this.holdings = holdings;
        this.movements = movements;
        this.fills = fills;
        this.orders = orders;
        this.clock = clock;
    }

    public record Position(Integer instrumentId, String symbol, String name, Currency currency,
            BigDecimal quantity, BigDecimal reservedQuantity, BigDecimal availableQuantity) { }
    public record Snapshot(Integer accountId, BigDecimal usdCash, List<Position> positions,
            boolean foreignBalance, BigDecimal netFunding, String historyProblem,
            String buyingPowerProblem, Instant asOf) { }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Snapshot read(Integer accountId, Integer clientId) {
        if (accountId == null || accountId <= 0) throw new IllegalArgumentException("Account ID must be positive.");
        var account = accounts.findByAccountIdAndClient_ClientId(accountId, clientId)
                .orElseThrow(() -> new NoSuchElementException("Account not found."));
        Instant asOf = clock.instant();

        Map<Currency, BigDecimal> cash = new EnumMap<>(Currency.class);
        balances.findOwnedBalances(accountId, clientId).forEach(balance ->
                cash.put(balance.getCurrency(), balance.getTotalBalance().amount()));
        var positions = holdings.findOwnedHoldings(accountId, clientId).stream()
                .map(holding -> new Position(holding.getInstrument().getInstrumentId(),
                        holding.getInstrument().getSymbol(), holding.getInstrument().getInstrumentName(),
                        holding.getInstrument().getQuoteCurrency(), holding.getTotalQuantity(),
                        holding.getReservedQuantity(), holding.availableQuantity()))
                .toList();

        Map<Currency, BigDecimal> recordedCash = new EnumMap<>(Currency.class);
        BigDecimal netFunding = BigDecimal.ZERO;
        boolean adjustments = false;
        boolean foreignHistory = false;
        for (var movement : movements.findOwnedHistory(accountId, clientId)) {
            recordedCash.merge(movement.getCurrency(), movement.getAmount(), BigDecimal::add);
            foreignHistory |= movement.getCurrency() != Currency.USD;
            adjustments |= movement.getCashMovementType() == CashMovementType.ADJUSTMENT;
            if (movement.getCurrency() == Currency.USD &&
                    (movement.getCashMovementType() == CashMovementType.DEPOSIT ||
                            movement.getCashMovementType() == CashMovementType.WITHDRAW)) {
                netFunding = netFunding.add(movement.getAmount()); // Withdrawals are negative.
            }
        }

        Map<Integer, BigDecimal> filledUnits = new HashMap<>();
        for (var fill : fills.findOwnedFills(accountId, clientId)) {
            var order = fill.getOrder();
            BigDecimal signedQuantity = order.getSide() == OrderSide.BUY
                    ? fill.getFillQuantity() : fill.getFillQuantity().negate();
            filledUnits.merge(order.getInstrument().getInstrumentId(), signedQuantity, BigDecimal::add);
            foreignHistory |= fill.getExecutionCurrency() != Currency.USD;
        }
        Map<Integer, BigDecimal> heldUnits = new HashMap<>();
        positions.forEach(position -> heldUnits.put(position.instrumentId(), position.quantity()));

        String historyProblem = adjustments ? "Cash adjustments need review before lifetime profit/loss can be calculated."
                : foreignHistory ? "Lifetime profit/loss currently supports USD-only account history."
                : !equalAmounts(cash, recordedCash) || !equalAmounts(heldUnits, filledUnits)
                ? "Account balances or holdings do not reconcile with recorded history." : null;
        String buyingPowerProblem = !account.isActive() ? "This account is inactive."
                : orders.existsPendingAcceptedOrder(accountId) ? "Trading is locked while an accepted order awaits execution." : null;

        boolean foreignBalance = cash.entrySet().stream().anyMatch(entry ->
                entry.getKey() != Currency.USD && entry.getValue().signum() != 0);
        return new Snapshot(accountId, cash.getOrDefault(Currency.USD, BigDecimal.ZERO), positions,
                foreignBalance, netFunding, historyProblem, buyingPowerProblem, asOf);
    }

    private static <K> boolean equalAmounts(Map<K, BigDecimal> actual, Map<K, BigDecimal> recorded) {
        Set<K> keys = new HashSet<>(actual.keySet());
        keys.addAll(recorded.keySet());
        return keys.stream().allMatch(key ->
                actual.getOrDefault(key, BigDecimal.ZERO).compareTo(recorded.getOrDefault(key, BigDecimal.ZERO)) == 0);
    }
}
