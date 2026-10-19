package com.neueda.leap.service;

import com.neueda.leap.entities.AuditOutbox;
import com.neueda.leap.repository.AuditOutboxRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuditOutboxPublisher {
    private final AuditOutboxRepository repository;
    private final KafkaTemplate<String, Object> kafka;
    private final Clock clock;
    private final String topic;

    public AuditOutboxPublisher(
            AuditOutboxRepository repository,
            KafkaTemplate<String, Object> kafka,
            Clock clock,
            @Value("${audit.kafka.topic:trade.audit}") String topic) {
        this.repository = repository;
        this.kafka = kafka;
        this.clock = clock;
        this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${audit.outbox.poll-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        for (AuditOutbox row : repository.findByPublishedAtIsNullOrderByOutboxIdAsc(PageRequest.of(0, 100))) {
            try {
                kafka.send(topic, row.event().orderId().toString(), row.event()).get();
                row.markPublished(OffsetDateTime.now(clock));
            } catch (Exception failure) {
                row.markFailure(failure.getMessage());
            }
        }
    }
}
