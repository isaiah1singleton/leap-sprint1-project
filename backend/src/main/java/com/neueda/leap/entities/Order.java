package com.neueda.leap.entities;

import com.neueda.leap.enums.OrderSide;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class Order {

    private Integer orderId;
    private Account account;
    private Instrument instrument;
    private OrderSide side;
    private BigDecimal requestedQuantity;
    private BigDecimal submittedQuotePrice;
    private OffsetDateTime submittedAt;

    public Order(Account account, Instrument instrument, OrderSide side, BigDecimal requestedQuantity, BigDecimal submittedQuotePrice) {
        if (account == null) {
            throw new IllegalArgumentException("Account cannot be null");
        }
        if (instrument == null) {
            throw new IllegalArgumentException("Instrument cannot be null");
        }
        if (side == null) {
            throw new IllegalArgumentException("Order side cannot be null");
        }
        if (requestedQuantity == null || requestedQuantity.signum() <= 0) {
            throw new IllegalArgumentException("Requested quantity must be positive");
        }
        if (submittedQuotePrice == null || submittedQuotePrice.signum() <= 0) {
            throw new IllegalArgumentException("Submitted quote price must be positive");
        }
        this.account = account;
        this.instrument = instrument;
        this.side = side;
        this.requestedQuantity = requestedQuantity;
        this.submittedQuotePrice = submittedQuotePrice;
        this.submittedAt = OffsetDateTime.now();
    }

    public Integer getOrderId() { return orderId; }
    public Account getAccount() { return account; }
    public Instrument getInstrument() { return instrument; }
    public OrderSide getSide() { return side; }
    public BigDecimal getRequestedQuantity() { return requestedQuantity; }
    public BigDecimal getSubmittedQuotePrice() { return submittedQuotePrice; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
}
