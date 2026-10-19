package com.neueda.leap.service;

import com.neueda.leap.entities.AuditRecord;
import com.neueda.leap.models.AuditRecordResponse;
import com.neueda.leap.models.TradeAuditEvent;
import com.neueda.leap.repository.AuditRecordRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditRecordService {
    private final AuditRecordRepository repository;
    private final Clock clock;

    public AuditRecordService(AuditRecordRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void record(TradeAuditEvent event) {
        if (!repository.existsByEventId(event.eventId())) {
            repository.save(new AuditRecord(event, OffsetDateTime.now(clock)));
        }
    }

    @Transactional(readOnly = true)
    public java.util.List<AuditRecordResponse> latest(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 500));
        return repository.findAllByOrderByOccurredAtDesc(PageRequest.of(0, boundedLimit))
                .stream().map(AuditRecordResponse::from).toList();
    }
}
