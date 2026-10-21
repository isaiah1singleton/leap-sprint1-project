package com.neueda.leap.service;

import com.neueda.leap.entities.AuditRecord;
import com.neueda.leap.models.AuditRecordResponse;
import com.neueda.leap.models.TradeAuditEvent;
import com.neueda.leap.repository.AuditRecordRepository;
import com.neueda.leap.repository.SuspiciousTradeFlagRepository;
import com.neueda.leap.repository.FillRepository;
import com.neueda.leap.repository.CashMovementRepository;
import com.neueda.leap.repository.OrderEventRepository;
import com.neueda.leap.repository.OrderRepository;
import com.neueda.leap.models.TradeLifecycleResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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
    private final OrderRepository orders;
    private final OrderEventRepository orderEvents;

    public AuditRecordService(AuditRecordRepository repository, SuspiciousTradeFlagRepository suspiciousFlags,
                              FillRepository fills, CashMovementRepository cashMovements,
                              OrderRepository orders, OrderEventRepository orderEvents, Clock clock) {
        this.repository = repository;
        this.suspiciousFlags = suspiciousFlags;
        this.clock = clock;
        this.fills = fills;
        this.cashMovements = cashMovements;
        this.orders = orders;
        this.orderEvents = orderEvents;
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
        var order = orders.findById(orderId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));
        var orderEventsForOrder = orderEvents.findByOrder_OrderIdOrderByOrderEventIdAsc(orderId);
        if (orderEventsForOrder.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order lifecycle not found.");
        }
        var events = orderEventsForOrder.stream().map(event -> new TradeLifecycleResponse.LifecycleEvent(
            UUID.nameUUIDFromBytes(("order-event:" + event.getOrderEventId()).getBytes(StandardCharsets.UTF_8)),
            event.getStatus().name(), event.getStatus().name(), event.getReasonForStatusChange(),
            event.getDecisionQuotePrice(), event.getOccurredAt())).toList();
        var fill = fills.findByOrderId(orderId);
        var execution = fill.map(value -> new TradeLifecycleResponse.Execution(
            value.getExecutionPrice(), value.getFillQuantity(), value.getExecutionTime())).orElse(null);
        var settlements = cashMovements.findByOrderId(orderId).stream()
            .map(movement -> new TradeLifecycleResponse.Settlement(
                movement.getCashMovementId(), movement.getAmount(), movement.getCashMovementType().name(),
                movement.getCurrency().name(), movement.getReason(), movement.getOccurredAt())).toList();
        boolean complete = events.stream().anyMatch(event -> "FILLED".equals(event.status()))
            && execution != null && !settlements.isEmpty();
        var account = order.getAccount();
        var client = account.getClient();
        return new TradeLifecycleResponse(orderId, "ORD-" + orderId, account.getAccountId(), client.getClientId(),
            client.getClientName(), client.getEmail(), order.getInstrument().getSymbol(), order.getSide().name(),
            order.getRequestedQuantity(), order.getSubmittedAt(), events, execution, settlements, complete);
        }
}
