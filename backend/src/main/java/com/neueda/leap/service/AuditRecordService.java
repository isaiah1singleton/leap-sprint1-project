package com.neueda.leap.service;

import com.neueda.leap.entities.AuditRecord;
import com.neueda.leap.models.AuditRecordResponse;
import com.neueda.leap.models.TradeAuditEvent;
import com.neueda.leap.repository.AuditRecordRepository;
import com.neueda.leap.repository.SuspiciousTradeFlagRepository;
import com.neueda.leap.repository.FillRepository;
import com.neueda.leap.repository.CashMovementRepository;
import com.neueda.leap.models.TradeLifecycleResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
    private final FillRepository fills;
    private final CashMovementRepository cashMovements;

    public AuditRecordService(AuditRecordRepository repository, SuspiciousTradeFlagRepository suspiciousFlags,
                              FillRepository fills, CashMovementRepository cashMovements, Clock clock) {
        this.repository = repository;
        this.suspiciousFlags = suspiciousFlags;
        this.clock = clock;
        this.fills = fills;
        this.cashMovements = cashMovements;
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

        @Transactional(readOnly = true)
        public TradeLifecycleResponse lifecycle(Integer orderId) {
        var records = repository.findAllByOrderIdOrderByOccurredAtAsc(orderId);
        if (records.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trade audit record not found.");
        }
        AuditRecord first = records.get(0);
        var events = records.stream().map(record -> new TradeLifecycleResponse.LifecycleEvent(
            record.getEventId(), record.getEventType(), record.getStatus(), record.getReason(),
            record.getPrice(), record.getOccurredAt())).toList();
        var fill = fills.findByOrderId(orderId);
        var execution = fill.map(value -> new TradeLifecycleResponse.Execution(
            value.getExecutionPrice(), value.getFillQuantity(), value.getExecutionTime())).orElse(null);
        var settlements = cashMovements.findByOrderId(orderId).stream()
            .map(movement -> new TradeLifecycleResponse.Settlement(
                movement.getCashMovementId(), movement.getAmount(), movement.getCashMovementType().name(),
                movement.getCurrency().name(), movement.getReason(), movement.getOccurredAt())).toList();
        boolean complete = events.stream().anyMatch(event -> "FILLED".equals(event.status()))
            && execution != null && !settlements.isEmpty();
        return new TradeLifecycleResponse(orderId, "ORD-" + orderId, first.getAccountId(), first.getClientId(),
            first.getClientName(), first.getClientEmail(), first.getInstrument(), first.getSide(),
            first.getQuantity(), events, execution, settlements, complete);
        }
}
