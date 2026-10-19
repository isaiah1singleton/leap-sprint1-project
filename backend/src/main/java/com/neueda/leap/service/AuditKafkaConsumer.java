package com.neueda.leap.service;

import com.neueda.leap.models.TradeAuditEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AuditKafkaConsumer {
    private final AuditRecordService records;

    public AuditKafkaConsumer(AuditRecordService records) {
        this.records = records;
    }

    @KafkaListener(topics = "${audit.kafka.topic:trade.audit}")
    public void consume(TradeAuditEvent event) {
        records.record(event);
    }
}
