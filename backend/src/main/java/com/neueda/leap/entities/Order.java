package com.neueda.leap.entities;

import com.neueda.leap.enums.AssetClass;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Locale;


@Entity
@Table(
        name = "orders"
)


public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Integer orderId;

    @ManyToOne
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne
    @JoinColumn(name = "instrument_id")
    private Instrument instrument;

    @Enumerated(EnumType.STRING)
    private OrderSide side;

    @Column(name = "requested_quantity", nullable = false, columnDefinition = "numeric")
    private BigDecimal RequestedQuantity;

    @Column(name = "submitted_quote_price", nullable = false, columnDefinition = "numeric")
    private BigDecimal submittedQuotePrice;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;


    protected Order() { }

    public Order(
            Account account,
            Instrument instrument,
            OrderSide side,
            BigDecimal requestedQuantity,
            BigDecimal submittedQuotePrice,
            OffsetDateTime submittedAt
    ) {
        if (account == null) {
            throw new IllegalArgumentException("account cannot be null");
        }
        this.account = account;

        if (instrument == null){
            throw new IllegalArgumentException("Instrument is required.");
        }
        this.instrument = instrument;

        if (side == null){
            throw new IllegalArgumentException("OrderSide is required.");
        }
        this.side = side;

        if (submittedQuotePrice == null){
            throw new IllegalArgumentException("SubmittedQuotePrice is required.");
        }
        else if (submittedQuotePrice.compareTo(requestedQuantity) <= 0){
            throw new IllegalArgumentException("SubmittedQuotePrice must be greater than 0.");
        }
        this.submittedQuotePrice = submittedQuotePrice;

        if (requestedQuantity == null){
            throw new IllegalArgumentException("RequestedQuantity is required.");
        }
        else if (requestedQuantity.compareTo(BigDecimal.ZERO) <= 0){
            throw new IllegalArgumentException("RequestedQuantity must be greater than to 0.");
        }
        this.RequestedQuantity = requestedQuantity;

        if (submittedAt == null){
            throw new IllegalArgumentException("SubmittedAt timestamp is required.");
        }
        this.submittedAt = OffsetDateTime.now();
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
        return RequestedQuantity;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }
}
