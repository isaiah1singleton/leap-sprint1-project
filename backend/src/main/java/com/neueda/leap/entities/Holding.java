package com.neueda.leap.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "account_holdings")
public class Holding {

    @EmbeddedId
    private HoldingId id;

    @MapsId("accountId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "account_id",
            nullable = false,
            updatable = false
    )
    private Account account;

    @MapsId("instrumentId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "instrument_id",
            nullable = false,
            updatable = false
    )
    private Instrument instrument;

    @Column(
            name = "total_quantity",
            nullable = false,
            precision = 24,
            scale = 8
    )
    private BigDecimal totalQuantity;

    @Column(
            name = "reserved_quantity",
            nullable = false,
            precision = 24,
            scale = 8
    )
    private BigDecimal reservedQuantity;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Holding() {
        // Required by JPA.
    }

    public Holding(Account account, Instrument instrument) {
        if (account == null) {
            throw new IllegalArgumentException("Account is required.");
        }

        if (instrument == null) {
            throw new IllegalArgumentException("Instrument is required.");
        }

        this.id = new HoldingId(
                account.getAccountId(),
                instrument.getInstrumentId()
        );

        this.account = account;
        this.instrument = instrument;
        this.totalQuantity = BigDecimal.ZERO;
        this.reservedQuantity = BigDecimal.ZERO;

        touch();
    }

    public void reserveQuantity(BigDecimal quantity) {
        requirePositiveQuantity(quantity);

        if (availableQuantity().compareTo(quantity) < 0) {
            throw new IllegalStateException(
                    "Insufficient available quantity to reserve."
            );
        }

        reservedQuantity = reservedQuantity.add(quantity);
        touch();
    }

    public void releaseReservedQuantity(BigDecimal quantity) {
        requirePositiveQuantity(quantity);

        if (reservedQuantity.compareTo(quantity) < 0) {
            throw new IllegalStateException(
                    "Cannot release more quantity than is reserved."
            );
        }

        reservedQuantity = reservedQuantity.subtract(quantity);
        touch();
    }

    public void addQuantity(BigDecimal quantity) {
        requirePositiveQuantity(quantity);

        BigDecimal newTotal = totalQuantity.add(quantity);

        requireFitsDatabase(newTotal);

        totalQuantity = newTotal;
        touch();
    }

    public void settleReservedSale(BigDecimal quantity) {
        requirePositiveQuantity(quantity);

        if (reservedQuantity.compareTo(quantity) < 0) {
            throw new IllegalStateException(
                    "Cannot sell more quantity than is reserved."
            );
        }

        if (totalQuantity.compareTo(quantity) < 0) {
            throw new IllegalStateException(
                    "Cannot sell more quantity than is owned."
            );
        }

        totalQuantity = totalQuantity.subtract(quantity);
        reservedQuantity = reservedQuantity.subtract(quantity);

        touch();
    }

    public BigDecimal availableQuantity() {
        return totalQuantity.subtract(reservedQuantity);
    }

    private static void requirePositiveQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be positive."
            );
        }

        requireFitsDatabase(quantity);
    }

    private static void requireFitsDatabase(BigDecimal quantity) {
        BigDecimal normalized = quantity.stripTrailingZeros();

        int fractionalDigits = Math.max(0, normalized.scale());

        int integerDigits = Math.max(
                0,
                normalized.precision() - normalized.scale()
        );

        if (fractionalDigits > 8 || integerDigits > 16) {
            throw new IllegalArgumentException(
                    "Quantity must fit NUMERIC(24, 8): "
                            + "at most 16 integer digits and "
                            + "8 decimal places."
            );
        }
    }

    private void touch() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public HoldingId getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public BigDecimal getTotalQuantity() {
        return totalQuantity;
    }

    public BigDecimal getReservedQuantity() {
        return reservedQuantity;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
