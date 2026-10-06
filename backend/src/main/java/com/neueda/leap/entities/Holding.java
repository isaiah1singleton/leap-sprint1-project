package com.neueda.leap.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Entity
@Table(name = "account_holdings")
public class Holding {
    @EmbeddedId
    private HoldingId id;

    @MapsId("accountId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @MapsId("instrumentId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Column(name = "total_quantity", nullable = false, precision = 24, scale = 8)
    private BigDecimal totalQuantity;

    @Column(name = "reserved_quantity", nullable = false, precision = 24, scale = 8)
    private BigDecimal reservedQuantity;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Holding() {
    }

    public Holding(Account account, Instrument instrument) {
        this.account = Objects.requireNonNull(account, "Account is required.");
        this.instrument = Objects.requireNonNull(instrument, "Instrument is required.");
        this.id = new HoldingId(account.getAccountId(), instrument.getInstrumentId());
        this.totalQuantity = BigDecimal.ZERO;
        this.reservedQuantity = BigDecimal.ZERO;
        touch();
    }

    @PrePersist
    private void beforeInsert() {
        if (updatedAt == null) touch();
    }

    public HoldingId getId() { return id; }
    public Account getAccount() { return account; }
    public Instrument getInstrument() { return instrument; }
    public BigDecimal getTotalQuantity() { return totalQuantity; }
    public BigDecimal getReservedQuantity() { return reservedQuantity; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public BigDecimal availableQuantity() { return totalQuantity.subtract(reservedQuantity); }

    private void touch() { updatedAt = OffsetDateTime.now(ZoneOffset.UTC); }
}
