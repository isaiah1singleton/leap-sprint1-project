package com.neueda.leap.entities;

import com.neueda.leap.enums.OrderSide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Integer orderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "account_id",
            nullable = false,
            updatable = false
    )
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "instrument_id",
            nullable = false,
            updatable = false
    )
    private Instrument instrument;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "side",
            nullable = false,
            updatable = false,
            columnDefinition = "text"
    )
    private OrderSide side;

    @Column(
            name = "requested_quantity",
            nullable = false,
            updatable = false,
            precision = 24,
            scale = 8
    )
    private BigDecimal requestedQuantity;

    @Column(
            name = "submitted_quote_price",
            nullable = false,
            updatable = false,
            precision = 24,
            scale = 8
    )
    private BigDecimal submittedQuotePrice;

    @Column(
            name = "submitted_quote_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime submittedQuoteAt;

    @Column(
            name = "submitted_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime submittedAt;

    protected Order() {}

    public Order(
            Account account,
            Instrument instrument,
            OrderSide side,
            BigDecimal requestedQuantity,
            BigDecimal submittedQuotePrice,
            OffsetDateTime submittedQuoteAt,
            OffsetDateTime submittedAt
    ) {
        if (account == null) {
            throw new IllegalArgumentException("Account is required.");
        }

        if (instrument == null) {
            throw new IllegalArgumentException("Instrument is required.");
        }

        if (side == null) {
            throw new IllegalArgumentException("Order side is required.");
        }

        requirePositiveNumeric(requestedQuantity, "Requested quantity");
        requirePositiveNumeric(submittedQuotePrice, "Submitted quote price");

        if (submittedQuoteAt == null) {
            throw new IllegalArgumentException(
                    "Submitted quote time is required."
            );
        }

        if (submittedAt == null) {
            throw new IllegalArgumentException(
                    "Submission time is required."
            );
        }

        if (submittedQuoteAt.isAfter(submittedAt)) {
            throw new IllegalArgumentException(
                    "Submitted quote time cannot be after submission time."
            );
        }

        this.account = account;
        this.instrument = instrument;
        this.side = side;
        this.requestedQuantity = requestedQuantity;
        this.submittedQuotePrice = submittedQuotePrice;
        this.submittedQuoteAt = submittedQuoteAt;
        this.submittedAt = submittedAt;
    }

    private static void requirePositiveNumeric(
            BigDecimal value,
            String field
    ) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(
                    field + " must be positive."
            );
        }

        BigDecimal normalized = value.stripTrailingZeros();

        int fractionalDigits = Math.max(0, normalized.scale());
        int integerDigits = Math.max(
                0,
                normalized.precision() - normalized.scale()
        );

        if (fractionalDigits > 8 || integerDigits > 16) {
            throw new IllegalArgumentException(
                    field + " must fit NUMERIC(24, 8): "
                            + "at most 16 integer digits and "
                            + "8 decimal places."
            );
        }
    }

    public Integer getOrderId() {
        return orderId;
    }

    public Account getAccount() {
        return account;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public OrderSide getSide() {
        return side;
    }

    public BigDecimal getRequestedQuantity() {
        return requestedQuantity;
    }

    public BigDecimal getSubmittedQuotePrice() {
        return submittedQuotePrice;
    }

    public OffsetDateTime getSubmittedQuoteAt() {
        return submittedQuoteAt;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }
}
