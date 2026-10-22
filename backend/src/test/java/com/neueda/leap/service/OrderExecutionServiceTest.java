package com.neueda.leap.service;

import com.neueda.leap.entities.*;
import com.neueda.leap.enums.*;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.repository.*;
import jakarta.persistence.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import static com.neueda.leap.support.TradingFixtures.*;
import static com.neueda.leap.service.OrderExecutionService.ExecutionResult.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class OrderExecutionServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderEventRepository history = mock(OrderEventRepository.class);
    private final OrderEventService events = mock(OrderEventService.class);
    private final FillService fills = mock(FillService.class);
    private final CashMovementService movements = mock(CashMovementService.class);
    private final EntityManager em = mock(EntityManager.class);
    private final Account account = account();
    private final Instrument instrument = instrument();
    private final CashBalance cash = new CashBalance(account, Currency.USD);
    private final Holding holding = new Holding(account, instrument);
    private Order order;
    private final OrderExecutionService service = new OrderExecutionService(orders, history, events, fills, movements,
        new OrderExecutionPolicy(new TradingRules()), new TradeAmountCalculator(), em, CLOCK);
    private void given(OrderSide side) {
        order = order(account, instrument, side);
        when(orders.findById(3)).thenReturn(Optional.of(order)); when(orders.findByIdForUpdate(3)).thenReturn(Optional.of(order));
        when(em.find(Account.class, 1, LockModeType.PESSIMISTIC_WRITE)).thenReturn(account);
        when(em.find(CashBalance.class, new CashBalanceId(1, Currency.USD), LockModeType.PESSIMISTIC_WRITE)).thenReturn(cash);
        when(em.find(Holding.class, new HoldingId(1, 2), LockModeType.PESSIMISTIC_WRITE)).thenReturn(holding);
        when(history.findFirstByOrder_OrderIdOrderByOrderEventIdDesc(3)).thenReturn(Optional.of(event(order, OrderStatus.ACCEPTED)));
        when(fills.recordFill(eq(order), any(), any())).thenAnswer(c -> id(new Fill(order, c.getArgument(1), c.getArgument(2)), "fillId", 5));
    }
    @Test void buySettlesAtAskAndRecordsMatchingFillMovementAndEvent() {
        given(OrderSide.BUY); cash.depositCash(usd("202")); holding.addQuantity(bd("1"));
        assertThat(service.execute(3, quote())).isEqualTo(FILLED);
        assertThat(cash.getTotalBalance().amount()).isZero(); assertThat(holding.getTotalQuantity()).isEqualByComparingTo("3");
        verify(fills).recordFill(order, bd("101"), AT);
        verify(movements).recordTrade(any(Fill.class), eq(usd("202.00000000")));
        verify(events).appendEvent(3, OrderStatus.FILLED, null, bd("101"), AT.minusMinutes(10));
    }
    @Test void buyCreatesMissingHolding() {
        given(OrderSide.BUY); cash.depositCash(usd("300"));
        when(em.find(Holding.class, new HoldingId(1, 2), LockModeType.PESSIMISTIC_WRITE)).thenReturn(null);
        assertThat(service.execute(3, quote())).isEqualTo(FILLED);
        ArgumentCaptor<Holding> saved = ArgumentCaptor.forClass(Holding.class); verify(em).persist(saved.capture());
        assertThat(saved.getValue().getTotalQuantity()).isEqualByComparingTo("2");
        assertThat(cash.getTotalBalance().amount()).isEqualByComparingTo("98");
    }
    @Test void sellSettlesAtBidAndConsumesReservation() {
        given(OrderSide.SELL); holding.addQuantity(bd("3")); holding.reserveQuantity(bd("2"));
        assertThat(service.execute(3, quote())).isEqualTo(FILLED);
        assertThat(cash.getTotalBalance().amount()).isEqualByComparingTo("198");
        assertThat(holding.getTotalQuantity()).isEqualByComparingTo("1"); assertThat(holding.getReservedQuantity()).isZero();
        verify(fills).recordFill(order, bd("99"), AT);
    }
    @Test void sellCreatesMissingCashBalance() {
        given(OrderSide.SELL); holding.addQuantity(bd("2")); holding.reserveQuantity(bd("2"));
        when(em.find(CashBalance.class, new CashBalanceId(1, Currency.USD), LockModeType.PESSIMISTIC_WRITE)).thenReturn(null);
        assertThat(service.execute(3, quote())).isEqualTo(FILLED);
        ArgumentCaptor<CashBalance> saved = ArgumentCaptor.forClass(CashBalance.class); verify(em).persist(saved.capture());
        assertThat(saved.getValue().getTotalBalance().amount()).isEqualByComparingTo("198");
    }
    @Test void insufficientExecutionCashRejectsWithoutChangingHoldings() {
        given(OrderSide.BUY); cash.depositCash(usd("200"));
        assertThat(service.execute(3, quote())).isEqualTo(REJECTED);
        assertThat(cash.getTotalBalance().amount()).isEqualByComparingTo("200"); assertThat(holding.getTotalQuantity()).isZero();
        verifyNoInteractions(fills, movements);
        verify(events).appendEvent(eq(3), eq(OrderStatus.REJECTED), contains("Insufficient cash"), isNull(), isNull());
    }
    @Test void absentCashAlsoRejectsBuy() {
        given(OrderSide.BUY); when(em.find(CashBalance.class, new CashBalanceId(1, Currency.USD), LockModeType.PESSIMISTIC_WRITE)).thenReturn(null);
        assertThat(service.execute(3, quote())).isEqualTo(REJECTED); verifyNoInteractions(fills, movements);
    }
    @Test void buyHoldingOverflowDoesNotDebitCash() {
        given(OrderSide.BUY); cash.depositCash(usd("300")); holding.addQuantity(bd("9999999999999999"));
        assertThat(service.execute(3, quote())).isEqualTo(REJECTED);
        assertThat(cash.getTotalBalance().amount()).isEqualByComparingTo("300");
        assertThat(holding.getTotalQuantity()).isEqualByComparingTo("9999999999999999"); verifyNoInteractions(fills, movements);
    }
    @Test void sellCashOverflowReleasesReservationWithoutSellingUnits() {
        given(OrderSide.SELL); cash.depositCash(usd("9999999999999999")); holding.addQuantity(bd("2")); holding.reserveQuantity(bd("2"));
        assertThat(service.execute(3, quote())).isEqualTo(REJECTED);
        assertThat(holding.getReservedQuantity()).isZero(); assertThat(holding.getTotalQuantity()).isEqualByComparingTo("2");
        assertThat(cash.getTotalBalance().amount()).isEqualByComparingTo("9999999999999999"); verifyNoInteractions(fills, movements);
    }
    @Test void expiryPrecedesQuoteAndReleasesSellReservation() {
        given(OrderSide.SELL); holding.addQuantity(bd("2")); holding.reserveQuantity(bd("2"));
        OrderEvent accepted = new OrderEvent(order, OrderStatus.ACCEPTED, null, bd("100"), AT.minusMinutes(3), AT.minusMinutes(2));
        when(history.findFirstByOrder_OrderIdOrderByOrderEventIdDesc(3)).thenReturn(Optional.of(accepted));
        assertThat(service.execute(3, null)).isEqualTo(EXPIRED); assertThat(holding.getReservedQuantity()).isZero();
        verifyNoInteractions(fills, movements); verify(events).appendEvent(eq(3), eq(OrderStatus.EXPIRED), anyString(), isNull(), isNull());
    }
    @Test void missingOrBadQuoteRetriesWithoutMutation() {
        given(OrderSide.BUY); cash.depositCash(usd("300"));
        assertThat(service.execute(3, null)).isEqualTo(RETRY);
        assertThat(service.execute(3, quote("OTHER", "99", "101", AT.toString()))).isEqualTo(RETRY);
        assertThat(cash.getTotalBalance().amount()).isEqualByComparingTo("300"); verifyNoInteractions(events, fills, movements);
    }
    @ParameterizedTest @EnumSource(value=OrderStatus.class, names={"SUBMITTED", "FILLED", "REJECTED", "EXPIRED"})
    void nonAcceptedOrdersNeverSettle(OrderStatus status) {
        given(OrderSide.BUY); when(history.findFirstByOrder_OrderIdOrderByOrderEventIdDesc(3)).thenReturn(Optional.of(event(order, status)));
        assertThat(service.execute(3, quote())).isEqualTo(SKIPPED); verifyNoInteractions(events, fills, movements);
    }
    @Test void missingOrderAndInvalidIdsAreHandled() {
        assertThat(service.execute(3, quote())).isEqualTo(SKIPPED);
        for (Integer invalid : Arrays.asList(null, 0, -1)) assertThatIllegalArgumentException().isThrownBy(() -> service.execute(invalid, quote()));
    }
    @Test void brokenAccountHistoryAndReservationFailRatherThanInventingSettlement() {
        given(OrderSide.SELL);
        assertThatIllegalStateException().isThrownBy(() -> service.execute(3, quote())).withMessageContaining("reservation");
        when(history.findFirstByOrder_OrderIdOrderByOrderEventIdDesc(3)).thenReturn(Optional.empty());
        assertThatIllegalStateException().isThrownBy(() -> service.execute(3, quote()));
        when(em.find(Account.class, 1, LockModeType.PESSIMISTIC_WRITE)).thenReturn(null);
        assertThatThrownBy(() -> service.execute(3, quote())).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(fills, movements);
    }
    @Test void unrepresentableSettlementRejectsAndReleasesSellReservation() {
        given(OrderSide.SELL); holding.addQuantity(bd("2")); holding.reserveQuantity(bd("2"));
        assertThat(service.execute(3, quote("AAPL", "10000000000000000", "10000000000000000", AT.toString()))).isEqualTo(REJECTED);
        assertThat(holding.getReservedQuantity()).isZero(); verifyNoInteractions(fills, movements);
    }
    @Test void fillPersistenceFailurePropagatesForTransactionRollback() {
        given(OrderSide.BUY); cash.depositCash(usd("300")); when(fills.recordFill(eq(order), any(), any())).thenThrow(new IllegalStateException("storage"));
        assertThatIllegalStateException().isThrownBy(() -> service.execute(3, quote())).withMessage("storage");
        verifyNoInteractions(movements, events);
        // In-memory entities are not rolled back by Mockito; real rollback is an integration concern.
    }
    @Test void orderDisappearingBetweenReadAndLockedLookupFailsClearly() {
        given(OrderSide.BUY); when(orders.findByIdForUpdate(3)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.execute(3, quote())).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(fills, movements, events);
    }
    @Test void absentOrInconsistentSellReservationNeverSettles() {
        given(OrderSide.SELL);
        when(em.find(Holding.class, new HoldingId(1, 2), LockModeType.PESSIMISTIC_WRITE)).thenReturn(null);
        assertThatIllegalStateException().isThrownBy(() -> service.execute(3, quote()));
        when(em.find(Holding.class, new HoldingId(1, 2), LockModeType.PESSIMISTIC_WRITE)).thenReturn(holding);
        id(holding, "reservedQuantity", bd("2"));
        assertThatIllegalStateException().isThrownBy(() -> service.execute(3, quote())); verifyNoInteractions(fills, movements);
    }
}
