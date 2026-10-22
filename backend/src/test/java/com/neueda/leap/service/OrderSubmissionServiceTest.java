package com.neueda.leap.service;

import com.neueda.leap.entities.*;
import com.neueda.leap.enums.*;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.repository.*;
import com.neueda.leap.service.order_validation.*;
import jakarta.persistence.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class OrderSubmissionServiceTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final InstrumentRepository instruments = mock(InstrumentRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderEventService events = mock(OrderEventService.class);
    private final OrderValidationService validation = spy(new OrderValidationService(new TradingRules(), CLOCK));
    private final EntityManager em = mock(EntityManager.class);
    private final Account account = account();
    private final Instrument instrument = instrument();
    private final OrderSubmissionService service = new OrderSubmissionService(accounts, instruments, orders, events, validation, em, CLOCK);
    @BeforeEach void setup() {
        when(accounts.findByAccountIdAndClient_ClientId(1, 7)).thenReturn(Optional.of(account));
        when(instruments.findById(2)).thenReturn(Optional.of(instrument));
        when(orders.saveAndFlush(any())).thenAnswer(c -> id(c.getArgument(0), "orderId", 3));
        when(events.appendEvent(anyInt(), any(), any(), any(), any())).thenAnswer(c ->
            new OrderEvent(order(account, instrument, OrderSide.BUY), c.getArgument(1), c.getArgument(2), c.getArgument(3), c.getArgument(4), AT));
    }
    @Test void buyAcceptedWithoutDebitingCash() {
        CashBalance cash = new CashBalance(account, Currency.USD); cash.depositCash(usd("200"));
        when(em.find(CashBalance.class, new CashBalanceId(1, Currency.USD), LockModeType.PESSIMISTIC_WRITE)).thenReturn(cash);
        var response = service.submit(request(OrderSide.BUY), 7, "alice@example.com");
        assertThat(response.status()).isEqualTo(OrderStatus.ACCEPTED); assertThat(response.executionPrice()).isNull();
        assertThat(cash.getTotalBalance().amount()).isEqualByComparingTo("200");
        var sequence = inOrder(events);
        sequence.verify(events).appendEvent(3, OrderStatus.SUBMITTED, null, null, null);
        sequence.verify(events).appendEvent(eq(3), eq(OrderStatus.ACCEPTED), isNull(), eq(bd("100")), any());
    }
    @Test void sellReservesUnitsWithoutSettlingThem() {
        Holding h = new Holding(account, instrument); h.addQuantity(bd("3"));
        when(em.find(Holding.class, new HoldingId(1, 2), LockModeType.PESSIMISTIC_WRITE)).thenReturn(h);
        assertThat(service.submit(request(OrderSide.SELL), 7, "alice@example.com").status()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(h.getReservedQuantity()).isEqualByComparingTo("2"); assertThat(h.getTotalQuantity()).isEqualByComparingTo("3");
    }
    @Test void insufficientResourcesRecordRejectionWithoutReservation() {
        var buy = service.submit(request(OrderSide.BUY), 7, "alice@example.com");
        assertThat(buy.status()).isEqualTo(OrderStatus.REJECTED); assertThat(buy.reason()).contains("INSUFFICIENT_CASH");
        var sell = service.submit(request(OrderSide.SELL), 7, "alice@example.com");
        assertThat(sell.status()).isEqualTo(OrderStatus.REJECTED); assertThat(sell.reason()).contains("INSUFFICIENT_UNITS");
    }
    @Test void invalidRequestsAccountsInstrumentsAndPendingOrdersNeverSave() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.submit(null, 7, "alice@example.com"));
        when(accounts.findByAccountIdAndClient_ClientId(1, 8)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.submit(request(OrderSide.BUY), 8, "other")).isInstanceOf(NoSuchElementException.class);
        when(instruments.findById(2)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.submit(request(OrderSide.BUY), 7, "alice@example.com")).isInstanceOf(NoSuchElementException.class);
        when(instruments.findById(2)).thenReturn(Optional.of(instrument)); when(orders.existsPendingAcceptedOrder(1)).thenReturn(true);
        assertThatIllegalStateException().isThrownBy(() -> service.submit(request(OrderSide.BUY), 7, "alice@example.com"));
        verify(orders, never()).saveAndFlush(any()); verifyNoInteractions(events);
    }
    @Test void unexpectedPersistenceFailureIsNotConvertedToBusinessRejection() {
        doThrow(new IllegalStateException("database unavailable")).when(orders).saveAndFlush(any());
        assertThatIllegalStateException().isThrownBy(() -> service.submit(request(OrderSide.BUY), 7, "alice@example.com")).withMessage("database unavailable");
        verifyNoInteractions(events);
    }
}
