package com.neueda.leap.service;

import com.neueda.leap.entities.AuditOutbox;
import com.neueda.leap.models.TradeAuditEvent;
import com.neueda.leap.repository.AuditOutboxRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditOutboxPublisherTest {
    @Mock AuditOutboxRepository repository;
    @Mock KafkaTemplate<String, Object> kafka;
    private AuditOutboxPublisher publisher;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() { publisher = new AuditOutboxPublisher(repository, kafka, clock, "test.audit"); }

    static TradeAuditEvent event(int id) {
        return new TradeAuditEvent(UUID.randomUUID(), "ORDER_STATUS_CHANGED", OffsetDateTime.now(),
                id, 1, 1, "test@example.com", "Trader", "AAPL", "BUY", BigDecimal.ONE,
                BigDecimal.TEN, "FILLED", null);
    }

    @Test
    void marksPublishedOnlyAfterBrokerAcknowledgesAndUsesOrderAsPartitionKey() {
        var event = event(7);
        var row = new AuditOutbox(event);
        when(repository.findByPublishedAtIsNullOrderByOutboxIdAsc(PageRequest.of(0, 100))).thenReturn(List.of(row));
        when(kafka.send("test.audit", "7", event)).thenAnswer(call -> {
            assertThat(row.getPublishedAt()).isNull();
            return CompletableFuture.<SendResult<String, Object>>completedFuture(null);
        });
        publisher.publishPending();
        assertThat(row.getPublishedAt()).isEqualTo(OffsetDateTime.now(clock));
        assertThat(row.getAttemptCount()).isZero();
        verify(kafka).send("test.audit", "7", event);
    }

    @Test
    void failedSendRemainsRetryableAndDoesNotPreventTheNextEvent() {
        var first = new AuditOutbox(event(1));
        var second = new AuditOutbox(event(2));
        when(repository.findByPublishedAtIsNullOrderByOutboxIdAsc(any())).thenReturn(List.of(first, second));
        when(kafka.send(eq("test.audit"), eq("1"), any())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
        when(kafka.send(eq("test.audit"), eq("2"), any())).thenReturn(CompletableFuture.completedFuture(null));
        publisher.publishPending();
        assertThat(first.getPublishedAt()).isNull();
        assertThat(first.getAttemptCount()).isOne();
        assertThat(first.getLastError()).contains("broker down");
        assertThat(second.getPublishedAt()).isNotNull();
        when(repository.findByPublishedAtIsNullOrderByOutboxIdAsc(any())).thenReturn(List.of(first));
        when(kafka.send(eq("test.audit"), eq("1"), any())).thenReturn(CompletableFuture.completedFuture(null));
        publisher.publishPending();
        assertThat(first.getPublishedAt()).isNotNull();
        assertThat(first.getLastError()).isNull();
        assertThat(first.getAttemptCount()).isOne();
    }

    @Test
    void noPendingRowsMakesNoBrokerCalls() {
        when(repository.findByPublishedAtIsNullOrderByOutboxIdAsc(any())).thenReturn(List.of());
        publisher.publishPending();
        verifyNoInteractions(kafka);
    }

    @Test
    void boundsStoredFailureDetails() {
        var row = new AuditOutbox(event(1));
        row.markFailure("x".repeat(2000));
        assertThat(row.getLastError()).hasSize(1000);
        row.markFailure(null);
        assertThat(row.getLastError()).isEqualTo("Kafka publish failed.");
        assertThat(row.getAttemptCount()).isEqualTo(2);
    }

}
