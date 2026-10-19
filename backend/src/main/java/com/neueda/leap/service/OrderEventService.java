package com.neueda.leap.service;

import com.neueda.leap.entities.Order;
import com.neueda.leap.entities.OrderEvent;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.models.OrderEventResponse;
import com.neueda.leap.models.TradeAuditEvent;
import com.neueda.leap.repository.OrderEventRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class OrderEventService {

    private final OrderEventRepository repository;
    private final EntityManager entityManager;
    private final Clock clock;
        private final AuditOutboxService auditOutbox;

    public OrderEventService(
            OrderEventRepository repository,
            EntityManager entityManager,
                        Clock clock,
                        AuditOutboxService auditOutbox
    ) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.clock = clock;
                this.auditOutbox = auditOutbox;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OrderEvent appendEvent(
            Integer orderId,
            OrderStatus nextStatus,
            String reason,
            BigDecimal decisionQuotePrice,
            OffsetDateTime decisionQuoteAt
    ) {
        requirePositiveId(orderId, "Order");

        Order order = entityManager.find(
                Order.class,
                orderId,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (order == null) {
            throw new NoSuchElementException("Order not found.");
        }

        OrderStatus previousStatus = repository
                .findFirstByOrder_OrderIdOrderByOrderEventIdDesc(orderId)
                .map(OrderEvent::getStatus)
                .orElse(null);

        if (!OrderEvent.canFollow(previousStatus, nextStatus)) {
            throw new IllegalStateException(
                    "Invalid order transition: "
                            + previousStatus + " -> " + nextStatus
            );
        }

        OrderEvent event = new OrderEvent(
                order,
                nextStatus,
                reason,
                decisionQuotePrice,
                decisionQuoteAt,
                OffsetDateTime.now(clock)
        );

        OrderEvent saved = repository.save(event);
        auditOutbox.enqueue(TradeAuditEvent.from(order, saved));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<OrderEventResponse> getEvents(
            Integer orderId,
            Integer authenticatedClientId
    ) {
        requirePositiveId(orderId, "Order");
        requirePositiveId(authenticatedClientId, "Client");

        if (!repository.existsOwnedOrder(
                orderId,
                authenticatedClientId
        )) {
            throw new NoSuchElementException("Order not found.");
        }

        return repository
                .findByOrder_OrderIdOrderByOrderEventIdAsc(orderId)
                .stream()
                .map(OrderEventResponse::from)
                .toList();
    }

    private static void requirePositiveId(Integer id, String field) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    field + " ID must be positive."
            );
        }
    }
}
