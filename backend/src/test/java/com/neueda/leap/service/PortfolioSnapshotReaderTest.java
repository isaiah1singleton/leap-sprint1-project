package com.neueda.leap.service;

import com.neueda.leap.entities.*;
import com.neueda.leap.enums.*;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.repository.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class PortfolioSnapshotReaderTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final CashBalanceRepository balances = mock(CashBalanceRepository.class);
    private final HoldingRepository holdings = mock(HoldingRepository.class);
    private final CashMovementRepository movements = mock(CashMovementRepository.class);
    private final FillRepository fills = mock(FillRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final Account account = account();
    private final PortfolioSnapshotReader reader = new PortfolioSnapshotReader(accounts, balances, holdings, movements, fills, orders, CLOCK);
    private void owned() { when(accounts.findByAccountIdAndClient_ClientId(1, 7)).thenReturn(Optional.of(account)); }
    @Test void ownershipAndInvalidIdAreCheckedBeforeOtherReads() {
        for (Integer invalid : Arrays.asList(null, 0, -1)) assertThatIllegalArgumentException().isThrownBy(() -> reader.read(invalid, 7));
        assertThatThrownBy(() -> reader.read(1, 8)).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(balances, holdings, movements, fills, orders);
    }
    @Test void emptyAccountIsReconciledWithZeroCashAndFixedTimestamp() {
        owned(); var s = reader.read(1, 7);
        assertThat(s.usdCash()).isZero(); assertThat(s.netFunding()).isZero(); assertThat(s.positions()).isEmpty();
        assertThat(s.historyProblem()).isNull(); assertThat(s.buyingPowerProblem()).isNull(); assertThat(s.foreignBalance()).isFalse(); assertThat(s.asOf()).isEqualTo(NOW);
    }
    @Test void depositsWithdrawalsAndTradeCashAreSeparatedFromFunding() {
        owned(); CashBalance cash = new CashBalance(account, Currency.USD); cash.depositCash(usd("600.00"));
        when(balances.findOwnedBalances(1, 7)).thenReturn(List.of(cash));
        var buy = order(account, instrument(), OrderSide.BUY); var sell = order(account, instrument(), OrderSide.SELL);
        var buyFill = fill(buy); var sellFill = fill(sell);
        when(movements.findOwnedHistory(1, 7)).thenReturn(List.of(
            CashMovement.deposit(account, bd("1000"), Currency.USD, AT), CashMovement.withdrawal(account, bd("400"), Currency.USD, AT),
            CashMovement.forTrade(buyFill, usd("200")), CashMovement.forTrade(sellFill, usd("200"))));
        when(fills.findOwnedFills(1, 7)).thenReturn(List.of(buyFill, sellFill));
        Holding h = new Holding(account, instrument()); when(holdings.findOwnedHoldings(1, 7)).thenReturn(List.of(h));
        var s = reader.read(1, 7); assertThat(s.netFunding()).isEqualByComparingTo("600"); assertThat(s.historyProblem()).isNull();
        assertThat(s.positions().get(0).quantity()).isZero();
    }
    @Test void cashOrHoldingMismatchPreventsProfitLoss() {
        owned(); CashBalance cash = new CashBalance(account, Currency.USD); cash.depositCash(usd("1"));
        when(balances.findOwnedBalances(1, 7)).thenReturn(List.of(cash));
        assertThat(reader.read(1, 7).historyProblem()).contains("reconcile");
        when(balances.findOwnedBalances(1, 7)).thenReturn(List.of()); Holding h = new Holding(account, instrument()); h.addQuantity(bd("2")); h.reserveQuantity(bd("1"));
        when(holdings.findOwnedHoldings(1, 7)).thenReturn(List.of(h));
        assertThat(reader.read(1, 7).historyProblem()).contains("reconcile");
        when(fills.findOwnedFills(1, 7)).thenReturn(List.of(fill(order(account, instrument(), OrderSide.BUY))));
        var s = reader.read(1, 7); assertThat(s.historyProblem()).isNull();
        assertThat(s.positions().get(0).availableQuantity()).isEqualByComparingTo("1"); assertThat(s.positions().get(0).reservedQuantity()).isEqualByComparingTo("1");
    }
    @Test void adjustmentsAndForeignHistoryAreExplicitlyUnsupported() {
        owned(); when(movements.findOwnedHistory(1, 7)).thenReturn(List.of(CashMovement.adjustment(account, bd("1"), Currency.USD, "review", AT)));
        assertThat(reader.read(1, 7).historyProblem()).contains("adjustments");
        when(movements.findOwnedHistory(1, 7)).thenReturn(List.of(CashMovement.deposit(account, bd("1"), Currency.EUR, AT)));
        assertThat(reader.read(1, 7).historyProblem()).contains("USD-only");
        CashBalance eur = new CashBalance(account, Currency.EUR); when(balances.findOwnedBalances(1, 7)).thenReturn(List.of(eur));
        assertThat(reader.read(1, 7).foreignBalance()).isFalse(); eur.depositCash(new Money(bd("1"), Currency.EUR));
        assertThat(reader.read(1, 7).foreignBalance()).isTrue();
    }
    @Test void inactiveAndPendingAccountsExplainUnavailableBuyingPower() {
        owned(); account.changeStatus(AccountStatus.INACTIVE); assertThat(reader.read(1, 7).buyingPowerProblem()).contains("inactive");
        account.changeStatus(AccountStatus.ACTIVE); when(orders.existsPendingAcceptedOrder(1)).thenReturn(true);
        assertThat(reader.read(1, 7).buyingPowerProblem()).contains("accepted order");
    }
    @Test void foreignFillHistoryPreventsUsdProfitEvenAfterPositionsAreClosed() {
        owned(); Instrument foreign = instrument(); id(foreign, "quoteCurrency", Currency.EUR);
        when(fills.findOwnedFills(1, 7)).thenReturn(List.of(fill(order(account, foreign, OrderSide.BUY))));
        assertThat(reader.read(1, 7).historyProblem()).contains("USD-only");
    }
}
