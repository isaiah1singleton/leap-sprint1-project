package com.neueda.leap.entities;

import com.neueda.leap.enums.OrderStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "order_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_events_order_status",
                columnNames = {"order_id", "status"}
        )
)
public class OrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Integer orderEventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "order_id",
            nullable = false,
            updatable = false
    )
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            updatable = false,
            columnDefinition = "text"
    )
    private OrderStatus status;

    @Column(name = "reason", updatable = false, columnDefinition = "text")
    private String reasonForStatusChange;

    @Column(
            name = "decision_quote_price",
            updatable = false,
            columnDefinition = "numeric"
    )
    private BigDecimal decisionQuotePrice;

    @Column(name = "decision_quote_at", updatable = false)
    private OffsetDateTime decisionQuoteAt;

    @Column(
            name = "occurred_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime occurredAt;

    protected OrderEvent() { }

    public OrderEvent(
            Order order,
            OrderStatus status,
            String reason,
            BigDecimal decisionQuotePrice,
            OffsetDateTime decisionQuoteAt,
            OffsetDateTime occurredAt
    ) {
        if (order == null) {
            throw new IllegalArgumentException("Order is required.");
        }

        if (status == null || status == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException(
                    "Unsupported order event status."
            );
        }

        if (occurredAt == null) {
            throw new IllegalArgumentException(
                    "Event time is required."
            );
        }

        String normalizedReason =
                reason == null || reason.isBlank() ? null : reason.strip();

        if (status == OrderStatus.REJECTED && normalizedReason == null) {
            throw new IllegalArgumentException(
                    "A rejection reason is required."
            );
        }

        boolean hasPrice = decisionQuotePrice != null;
        boolean hasQuoteTime = decisionQuoteAt != null;

        if (hasPrice != hasQuoteTime) {
            throw new IllegalArgumentException(
                    "Quote price and timestamp must be supplied together."
            );
        }

        if (hasPrice && decisionQuotePrice.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Decision quote price must be positive."
            );
        }

        if ((status == OrderStatus.ACCEPTED
                || status == OrderStatus.FILLED) && !hasPrice) {
            throw new IllegalArgumentException(
                    "Accepted and filled events require a decision quote."
            );
        }

        if (hasQuoteTime && decisionQuoteAt.isAfter(occurredAt)) {
            throw new IllegalArgumentException(
                    "Decision quote cannot be later than the event."
            );
        }

        this.order = order;
        this.status = status;
        this.reasonForStatusChange = normalizedReason;
        this.decisionQuotePrice = decisionQuotePrice;
        this.decisionQuoteAt = decisionQuoteAt;
        this.occurredAt = occurredAt;
    }

    public static boolean canFollow(
            OrderStatus previous,
            OrderStatus next
    ) {
        if (next == null) {
            return false;
        }

        if (previous == null) {
            return next == OrderStatus.SUBMITTED;
        }

        return switch (previous) {
            case SUBMITTED ->
                    next == OrderStatus.ACCEPTED
                            || next == OrderStatus.REJECTED;
            case ACCEPTED ->
                    next == OrderStatus.FILLED
                            || next == OrderStatus.REJECTED;
            case FILLED, REJECTED, CANCELLED -> false;
        };
    }

    public Integer getOrderEventId() {
        return orderEventId;
    }

    public Order getOrder() {
        return order;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getReasonForStatusChange() {
        return reasonForStatusChange;
    }

    public BigDecimal getDecisionQuotePrice() {
        return decisionQuotePrice;
    }

    public OffsetDateTime getDecisionQuoteAt() {
        return decisionQuoteAt;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}
