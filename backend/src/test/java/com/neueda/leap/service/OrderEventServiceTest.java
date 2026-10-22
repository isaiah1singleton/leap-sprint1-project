package com.neueda.leap.service;

import com.neueda.leap.entities.Order;
import com.neueda.leap.entities.OrderEvent;
import com.neueda.leap.enums.*;
import com.neueda.leap.repository.OrderEventRepository;
import com.neueda.leap.models.TradeAuditEvent;
import jakarta.persistence.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class OrderEventServiceTest {
    private final OrderEventRepository repo = mock(OrderEventRepository.class);
    private final EntityManager em = mock(EntityManager.class);
    private final AuditOutboxService outbox = mock(AuditOutboxService.class);
    private final OrderEventService service = new OrderEventService(repo, em, CLOCK, outbox);
    @Test void validTransitionRecordsEventAndMatchingAuditPayload() {
        Order o = order(OrderSide.BUY); when(em.find(Order.class, 3, LockModeType.PESSIMISTIC_WRITE)).thenReturn(o);
        when(repo.findFirstByOrder_OrderIdOrderByOrderEventIdDesc(3)).thenReturn(Optional.of(event(o, OrderStatus.SUBMITTED)));
        when(repo.save(any())).thenAnswer(c -> id(c.getArgument(0), "orderEventId", 9));
        var saved = service.appendEvent(3, OrderStatus.ACCEPTED, null, bd("100"), AT.minusMinutes(1));
        assertThat(saved.getOccurredAt()).isEqualTo(AT); assertThat(saved.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        ArgumentCaptor<TradeAuditEvent> event = ArgumentCaptor.forClass(TradeAuditEvent.class); verify(outbox).enqueue(event.capture());
        assertThat(event.getValue().orderId()).isEqualTo(3); assertThat(event.getValue().clientId()).isEqualTo(7);
        assertThat(event.getValue().status()).isEqualTo("ACCEPTED"); assertThat(event.getValue().price()).isEqualByComparingTo("100");
    }
    @Test void initialSubmissionIsAllowedButInvalidTransitionsNeverWrite() {
        Order o = order(OrderSide.BUY); when(em.find(Order.class, 3, LockModeType.PESSIMISTIC_WRITE)).thenReturn(o);
        when(repo.save(any())).thenAnswer(c -> c.getArgument(0));
        assertThat(service.appendEvent(3, OrderStatus.SUBMITTED, null, null, null).getStatus()).isEqualTo(OrderStatus.SUBMITTED);
        clearInvocations(repo, outbox);
        when(repo.findFirstByOrder_OrderIdOrderByOrderEventIdDesc(3)).thenReturn(Optional.of(event(o, OrderStatus.FILLED)));
        assertThatIllegalStateException().isThrownBy(() -> service.appendEvent(3, OrderStatus.ACCEPTED, null, bd("1"), AT));
        verify(repo, never()).save(any()); verifyNoInteractions(outbox);
    }
    @Test void validatesIdsAndMissingOrders() {
        for (Integer invalid : Arrays.asList(null, 0, -1)) {
            assertThatIllegalArgumentException().isThrownBy(() -> service.appendEvent(invalid, OrderStatus.SUBMITTED, null, null, null));
            assertThatIllegalArgumentException().isThrownBy(() -> service.getEvents(invalid, 7));
            assertThatIllegalArgumentException().isThrownBy(() -> service.getEvents(3, invalid));
        }
        assertThatThrownBy(() -> service.appendEvent(3, OrderStatus.SUBMITTED, null, null, null)).isInstanceOf(NoSuchElementException.class);
    }
    @Test void historyChecksOwnershipAndPreservesEventOrder() {
        assertThatThrownBy(() -> service.getEvents(3, 8)).isInstanceOf(NoSuchElementException.class);
        verify(repo, never()).findByOrder_OrderIdOrderByOrderEventIdAsc(anyInt());
        when(repo.existsOwnedOrder(3, 7)).thenReturn(true);
        assertThat(service.getEvents(3, 7)).isEmpty();
        Order o = order(OrderSide.BUY);
        when(repo.findByOrder_OrderIdOrderByOrderEventIdAsc(3)).thenReturn(List.of(event(o, OrderStatus.SUBMITTED), event(o, OrderStatus.ACCEPTED)));
        assertThat(service.getEvents(3, 7)).extracting("status").containsExactly(OrderStatus.SUBMITTED, OrderStatus.ACCEPTED);
    }
}
