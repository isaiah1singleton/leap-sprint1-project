package com.neueda.leap.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class HoldingId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "account_id", nullable = false)
    private Integer accountId;

    @Column(name = "instrument_id", nullable = false)
    private Integer instrumentId;

    protected HoldingId() {
        // Required by JPA.
    }

    public HoldingId(Integer accountId, Integer instrumentId) {
        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException(
                    "A positive account ID is required."
            );
        }

        if (instrumentId == null || instrumentId <= 0) {
            throw new IllegalArgumentException(
                    "A positive instrument ID is required."
            );
        }

        this.accountId = accountId;
        this.instrumentId = instrumentId;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public Integer getInstrumentId() {
        return instrumentId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof HoldingId that)) {
            return false;
        }

        return Objects.equals(accountId, that.accountId)
                && Objects.equals(instrumentId, that.instrumentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, instrumentId);
    }
}