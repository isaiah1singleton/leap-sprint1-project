package com.neueda.leap.service;

import com.neueda.leap.entities.*;
import com.neueda.leap.enums.*;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.repository.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class FillCashAndOrderQueryServicesTest {
    private final FillRepository fills = mock(FillRepository.class);
    private final CashMovementRepository movements = mock(CashMovementRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderEventRepository events = mock(OrderEventRepository.class);
    private final FillService fillService = new FillService(fills);
    private final CashMovementService cashService = new CashMovementService(movements, accounts, CLOCK);
    private final OrderQueryService query = new OrderQueryService(accounts, orders, events);
    @Test void persistedOrderCanCreateFillButInvalidOrdersCannot() {
        Order o = order(OrderSide.BUY); when(fills.save(any())).thenAnswer(c -> c.getArgument(0));
        var f = fillService.recordFill(o, bd("101"), AT); assertThat(f.getExecutionPrice()).isEqualByComparingTo("101");
        assertThatIllegalArgumentException().isThrownBy(() -> fillService.recordFill(null, bd("1"), AT));
        for (Integer invalid : Arrays.asList(null, 0, -1)) {
            id(o, "orderId", invalid); assertThatIllegalArgumentException().isThrownBy(() -> fillService.recordFill(o, bd("1"), AT));
        }
        verify(fills, times(1)).save(any());
    }
    @Test void fillLookupsAreScopedToClientAndValidateIds() {
        when(fills.findByOrderIdAndClientId(3, 7)).thenReturn(Optional.of(fill(order(OrderSide.BUY))));
        assertThat(fillService.getFillFromOrder(3, 7)).isNotNull();
        assertThatThrownBy(() -> fillService.getFillFromOrder(3, 8)).isInstanceOf(NoSuchElementException.class);
        for (Integer invalid : Arrays.asList(null, 0, -1)) {
            assertThatIllegalArgumentException().isThrownBy(() -> fillService.getFillFromOrder(invalid, 7));
            assertThatIllegalArgumentException().isThrownBy(() -> fillService.getFillFromOrder(3, invalid));
        }
    }
    @Test void cashRecordersPreserveSignsCurrencyAndTime() {
        when(movements.save(any())).thenAnswer(c -> c.getArgument(0)); Account a = account();
        assertThat(cashService.recordDeposit(a, bd("10"), Currency.USD).getAmount()).isEqualByComparingTo("10");
        assertThat(cashService.recordWithdrawal(a, bd("5"), Currency.USD).getAmount()).isEqualByComparingTo("-5");
        assertThat(cashService.recordAdjustment(a, bd("-1"), Currency.USD, "review").getReason()).isEqualTo("review");
        assertThat(cashService.recordTrade(fill(order(OrderSide.BUY)), usd("202")).getAmount()).isEqualByComparingTo("-202");
        ArgumentCaptor<CashMovement> saved = ArgumentCaptor.forClass(CashMovement.class); verify(movements, times(4)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(m -> assertThat(m.getOccurredAt()).isEqualTo(AT));
        assertThatIllegalArgumentException().isThrownBy(() -> cashService.recordTrade(null, usd("1")));
        for (Integer invalid : Arrays.asList(null, 0, -1)) {
            Fill f = id(fill(order(OrderSide.BUY)), "fillId", invalid);
            assertThatIllegalArgumentException().isThrownBy(() -> cashService.recordTrade(f, usd("1")));
            assertThatIllegalArgumentException().isThrownBy(() -> cashService.recordDeposit(id(account(), "accountId", invalid), bd("1"), Currency.USD));
        }
    }
    @Test void cashHistoryAndDetailRejectNonOwnersAndInvalidIds() {
        assertThatThrownBy(() -> cashService.getMovements(1, 8)).isInstanceOf(NoSuchElementException.class);
        verify(movements, never()).findOwnedHistory(anyInt(), anyInt());
        when(accounts.findByAccountIdAndClient_ClientId(1, 7)).thenReturn(Optional.of(account()));
        assertThat(cashService.getMovements(1, 7)).isEmpty();
        CashMovement m = CashMovement.deposit(account(), bd("1"), Currency.USD, AT);
        when(movements.findOwnedHistory(1, 7)).thenReturn(List.of(m)); assertThat(cashService.getMovements(1, 7)).hasSize(1);
        when(movements.findOwnedMovement(9, 7)).thenReturn(Optional.of(m)); assertThat(cashService.getMovement(9, 7)).isNotNull();
        assertThatThrownBy(() -> cashService.getMovement(9, 8)).isInstanceOf(NoSuchElementException.class);
        for (Integer invalid : Arrays.asList(null, 0, -1)) {
            assertThatIllegalArgumentException().isThrownBy(() -> cashService.getMovement(invalid, 7));
            assertThatIllegalArgumentException().isThrownBy(() -> cashService.getMovement(9, invalid));
            assertThatIllegalArgumentException().isThrownBy(() -> cashService.getMovements(invalid, 7));
            assertThatIllegalArgumentException().isThrownBy(() -> cashService.getMovements(1, invalid));
        }
    }
    @Test void queriesExposeLatestStatusAndSeparateIndicativeAndExecutionPrices() {
        Order o = order(OrderSide.BUY); when(accounts.findByAccountIdAndClient_ClientId(1, 7)).thenReturn(Optional.of(o.getAccount()));
        assertThat(query.list(1, 7)).isEmpty();
        when(orders.findByAccount_AccountIdAndAccount_Client_ClientIdOrderBySubmittedAtDesc(1, 7)).thenReturn(List.of(o));
        when(orders.findByOrderIdAndAccount_Client_ClientId(3, 7)).thenReturn(Optional.of(o));
        when(events.findFirstByOrder_OrderIdOrderByOrderEventIdDesc(3)).thenReturn(Optional.of(new OrderEvent(o, OrderStatus.FILLED, null, bd("101"), AT, AT)));
        assertThat(query.get(3, 7).executionPrice()).isEqualByComparingTo("101");
        assertThat(query.list(1, 7).get(0).indicativePrice()).isEqualByComparingTo("100");
        when(events.findFirstByOrder_OrderIdOrderByOrderEventIdDesc(3)).thenReturn(Optional.empty());
        assertThatIllegalStateException().isThrownBy(() -> query.get(3, 7));
        assertThatThrownBy(() -> query.get(3, 8)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> query.list(1, 8)).isInstanceOf(NoSuchElementException.class);
        for (Integer invalid : Arrays.asList(null, 0, -1)) assertThatIllegalArgumentException().isThrownBy(() -> query.get(invalid, 7));
    }
}
