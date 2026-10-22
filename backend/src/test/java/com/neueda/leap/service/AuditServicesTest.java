package com.neueda.leap.service;

import com.neueda.leap.entities.*;
import com.neueda.leap.enums.*;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.repository.*;
import com.neueda.leap.models.TradeAuditEvent;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class AuditServicesTest {
    private final AuditRecordRepository records = mock(AuditRecordRepository.class);
    private final SuspiciousTradeFlagRepository flags = mock(SuspiciousTradeFlagRepository.class);
    private final FillRepository fills = mock(FillRepository.class);
    private final CashMovementRepository movements = mock(CashMovementRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderEventRepository events = mock(OrderEventRepository.class);
    private final AuditRecordService service = new AuditRecordService(records, flags, fills, movements, orders, events, CLOCK);
    @Test void recordsOnlyNewEventIdsAndConsumerDelegates() {
        var e = audit(); service.record(e);
        ArgumentCaptor<AuditRecord> saved = ArgumentCaptor.forClass(AuditRecord.class); verify(records).save(saved.capture());
        assertThat(saved.getValue().getEventId()).isEqualTo(e.eventId());
        when(records.existsByEventId(e.eventId())).thenReturn(true); service.record(e); verify(records, times(1)).save(any());
        AuditRecordService handler = mock(AuditRecordService.class); new AuditKafkaConsumer(handler).consume(e); verify(handler).record(e);
    }
    @Test void limitsAreBoundedAndFlagsAttachedToMatchingOrders() {
        AuditRecord record = new AuditRecord(audit(), AT);
        when(records.findAllByOrderByOccurredAtDesc(any())).thenReturn(List.of(record));
        when(flags.findAllByOrderIdIn(any())).thenReturn(List.of(new SuspiciousTradeFlag(3, 1, "admin", AT, AccountStatus.ACTIVE)));
        assertThat(service.latest(1000).get(0).suspicious()).isTrue();
        ArgumentCaptor<org.springframework.data.domain.Pageable> page = ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(records).findAllByOrderByOccurredAtDesc(page.capture()); assertThat(page.getValue().getPageSize()).isEqualTo(500);
        when(flags.findAllByOrderIdIn(any())).thenReturn(List.of()); assertThat(service.latest(0).get(0).suspicious()).isFalse();
        verify(records).findAllByOrderByOccurredAtDesc(org.springframework.data.domain.PageRequest.of(0, 1));
    }
    @Test void lifecycleNeedsFilledEventExecutionAndSettlementToBeComplete() {
        var o = order(OrderSide.BUY); when(orders.findById(3)).thenReturn(Optional.of(o));
        when(events.findByOrder_OrderIdOrderByOrderEventIdAsc(3)).thenReturn(List.of(event(o, OrderStatus.ACCEPTED)));
        assertThat(service.lifecycle(3).complete()).isFalse();
        when(events.findByOrder_OrderIdOrderByOrderEventIdAsc(3)).thenReturn(List.of(event(o, OrderStatus.FILLED)));
        assertThat(service.lifecycle(3).complete()).isFalse();
        var f = fill(o); when(fills.findByOrderId(3)).thenReturn(Optional.of(f)); assertThat(service.lifecycle(3).complete()).isFalse();
        when(movements.findByOrderId(3)).thenReturn(List.of(CashMovement.forTrade(f, usd("202"))));
        var result = service.lifecycle(3); assertThat(result.complete()).isTrue(); assertThat(result.execution().quantity()).isEqualByComparingTo("2");
        assertThat(result.events()).isEqualTo(service.lifecycle(3).events());
    }
    @Test void missingOrderOrLifecycleProducesNotFound() {
        assertThatThrownBy(() -> service.lifecycle(3)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("404");
        when(orders.findById(3)).thenReturn(Optional.of(order(OrderSide.BUY)));
        assertThatThrownBy(() -> service.lifecycle(3)).hasMessageContaining("404");
    }
    @Test void outboxEnqueueAndPublishingHandleSuccessFailureAndRetry() {
        AuditOutboxRepository repo = mock(AuditOutboxRepository.class);
        var e = audit(); new AuditOutboxService(repo).enqueue(e);
        ArgumentCaptor<AuditOutbox> capture = ArgumentCaptor.forClass(AuditOutbox.class); verify(repo).save(capture.capture());
        assertThat(capture.getValue().event()).isEqualTo(e);
        @SuppressWarnings("unchecked") KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        AuditOutbox first = new AuditOutbox(e), second = new AuditOutbox(audit());
        when(repo.findByPublishedAtIsNullOrderByOutboxIdAsc(any())).thenReturn(List.of(first, second));
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>(); failed.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafka.send(eq("trade.audit"), eq("3"), any())).thenReturn(failed, CompletableFuture.completedFuture(null), CompletableFuture.completedFuture(null), CompletableFuture.completedFuture(null));
        AuditOutboxPublisher publisher = new AuditOutboxPublisher(repo, kafka, CLOCK, "trade.audit"); publisher.publishPending();
        assertThat(first.getPublishedAt()).isNull(); assertThat(first.getAttemptCount()).isEqualTo(1); assertThat(first.getLastError()).contains("broker unavailable");
        assertThat(second.getPublishedAt()).isEqualTo(AT); publisher.publishPending();
        assertThat(first.getPublishedAt()).isEqualTo(AT); assertThat(first.getLastError()).isNull();
    }
}
