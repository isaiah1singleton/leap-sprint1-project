package com.neueda.leap.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class HoldingId implements Serializable {
    @Column(name = "account_id")
    private Integer accountId;

    @Column(name = "instrument_id")
    private Integer instrumentId;

    protected HoldingId() {
    }

    public HoldingId(Integer accountId, Integer instrumentId) {
        this.accountId = accountId;
        this.instrumentId = instrumentId;
    }

    public Integer getAccountId() { return accountId; }
    public Integer getInstrumentId() { return instrumentId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof HoldingId that)) return false;
        return Objects.equals(accountId, that.accountId)
                && Objects.equals(instrumentId, that.instrumentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, instrumentId);
    }
}
