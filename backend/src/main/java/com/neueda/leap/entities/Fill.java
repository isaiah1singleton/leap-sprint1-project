package com.neueda.leap.entities;

import com.neueda.leap.enums.Currency;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "fills", uniqueConstraints = @UniqueConstraint(name = "uk_fills_order",columnNames = "order_id"))
public class Fill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fill_id")
    private Integer fillId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "order_id",
        nullable = false,
        unique = true,
        updatable = false
    )
    private Order order;

    @Column(name = "execution_price", nullable = false, updatable = false, columnDefinition = "numeric")
    private BigDecimal executionPrice;

    @Column(name = "execution_time", nullable = false, updatable = false)
    private OffsetDateTime executionTime;

    protected Fill() {

    }

    public Fill(
        Order order,
        BigDecimal executionPrice,
        OffsetDateTime executionTime
    ) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        requirePositive(executionPrice, "Execution price");

        if (executionTime == null) {
            throw new IllegalArgumentException("Execution time cannot be null");
        }
        this.order = order;
        this.executionPrice = executionPrice;
        this.executionTime = executionTime;
    }

    public Integer getFillId() { return fillId; }
    public Order getOrder() { return order; }
    public BigDecimal getExecutionPrice() { return executionPrice; }
    public OffsetDateTime getExecutionTime() { return executionTime; }
    public BigDecimal getFillQuantity() { return this.order.getRequestedQuantity(); }
    public Currency getExecutionCurrency() { return this.order.getInstrument().getQuoteCurrency(); }

    private static void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}