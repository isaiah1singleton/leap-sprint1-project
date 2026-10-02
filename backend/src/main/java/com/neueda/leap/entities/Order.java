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
    @JoinColumn(name = "instrument_instrument_id")
    private Instrument instrument;
    private OrderSide side;
    private BigDecimal RequestedQuantity;
    private OffsetDateTime submittedAt;
    private OrderStatus currentOrderStatus;

    protected Order() { }

    public Order(
            Instrument instrument,
            OrderSide side,
            BigDecimal requestedQuantity,
            OffsetDateTime submittedAt
    ) {
        if (instrument == null){
            throw new IllegalArgumentException("Instrument is required.");
        }
        this.instrument = instrument;

        if (side == null){
            throw new IllegalArgumentException("OrderSide is required.");
        }
        this.side = side;

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
        this.submittedAt = submittedAt;
    }
}
