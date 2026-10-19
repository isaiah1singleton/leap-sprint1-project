package com.neueda.leap.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Id;


@Entity
@Table(name = "fifo_allocations", uniqueConstraints = @UniqueConstraint(
        name = "uk_fifo_allocations_sell_buy", columnNames = {"sell_fill_id", "buy_fill_id"}))
public class FifoAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fifo_allocation_id")
    private Long fifoAllocationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sell_fill_id", nullable = false, updatable = false)
    private Fill sellFillId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buy_fill_id", nullable = false, updatable = false)
    private Fill buyFillId;

    @Column(name = "allocated_quantity",
        nullable = false,
        updatable = false,
        precision = 24,
        scale = 8
    )
    private BigDecimal allocatedQuantity;

    protected FifoAllocation() {}

    public FifoAllocation(Fill sellFill, Fill buyFill, BigDecimal allocatedQuantity) {
        this.sellFillId = sellFill;
        this.buyFillId = buyFill;
        this.allocatedQuantity = allocatedQuantity;
    }

    public BigDecimal getAllocatedQuantity() {
        return allocatedQuantity;
    }
}
