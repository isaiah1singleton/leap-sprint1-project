package com.neueda.leap.service;

import com.neueda.leap.entities.AuditRecord;
import com.neueda.leap.repository.*;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditRecordServiceTest {
    @Mock AuditRecordRepository records;
    @Mock SuspiciousTradeFlagRepository flags;
    @Mock FillRepository fills;
    @Mock CashMovementRepository movements;
    @Mock OrderRepository orders;
    @Mock OrderEventRepository events;
    AuditRecordService service;

    @BeforeEach
    void setUp() { service = new AuditRecordService(records, flags, fills, movements, orders, events, Clock.systemUTC()); }

    @Test
    void recordsANewDelivery() {
        var event = AuditOutboxPublisherTest.event(1);
        service.record(event);
        verify(records).save(any(AuditRecord.class));
    }

    @Test
    void redeliveryDoesNotDuplicateAnAlreadyRecordedEvent() {
        var event = AuditOutboxPublisherTest.event(1);
        when(records.existsByEventId(event.eventId())).thenReturn(true);
        new AuditKafkaConsumer(service).consume(event);
        verify(records, never()).save(any());
    }

    @Test
    void persistenceFailuresPropagateSoTheKafkaContainerCanRetry() {
        var event = AuditOutboxPublisherTest.event(1);
        when(records.save(any())).thenThrow(new IllegalStateException("database unavailable"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new AuditKafkaConsumer(service).consume(event))
                .isInstanceOf(IllegalStateException.class).hasMessage("database unavailable");
    }
}
