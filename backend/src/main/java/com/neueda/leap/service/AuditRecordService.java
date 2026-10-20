package com.neueda.leap.service;

import com.neueda.leap.entities.AuditRecord;
import com.neueda.leap.models.AuditRecordResponse;
import com.neueda.leap.models.TradeAuditEvent;
import com.neueda.leap.repository.AuditRecordRepository;
import com.neueda.leap.repository.SuspiciousTradeFlagRepository;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditRecordService {
    private final AuditRecordRepository repository;
    private final SuspiciousTradeFlagRepository suspiciousFlags;
    private final Clock clock;

    public AuditRecordService(AuditRecordRepository repository, SuspiciousTradeFlagRepository suspiciousFlags,
                              Clock clock) {
        this.repository = repository;
        this.suspiciousFlags = suspiciousFlags;
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
        var records = repository.findAllByOrderByOccurredAtDesc(PageRequest.of(0, boundedLimit));
        Set<Integer> orderIds = records.stream().map(AuditRecord::getOrderId).collect(Collectors.toSet());
        Map<Integer, Boolean> flagged = suspiciousFlags.findAllByOrderIdIn(orderIds).stream()
            .collect(Collectors.toMap(flag -> flag.getOrderId(), flag -> true));
        return records.stream().map(record -> {
            AuditRecordResponse response = AuditRecordResponse.from(record);
            return new AuditRecordResponse(response.auditId(), response.eventId(), response.orderRef(),
                response.eventType(), response.orderId(), response.accountId(), response.clientId(),
                response.clientName(), response.clientEmail(), response.instrument(), response.side(),
                response.quantity(), response.price(), response.status(), response.reason(),
                response.occurredAt(), flagged.getOrDefault(record.getOrderId(), false));
        }).toList();
    }
}
