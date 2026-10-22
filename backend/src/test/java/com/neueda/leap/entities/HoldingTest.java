package com.neueda.leap.entities;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class HoldingTest {
    @Test void completeReservationAndSettlementLifecycle() {
        Holding h = new Holding(account(), instrument());
        assertThat(h.getTotalQuantity()).isZero();
        assertThat(h.getReservedQuantity()).isZero();
        assertThat(h.availableQuantity()).isZero();
        assertThat(h.getId()).isEqualTo(new HoldingId(1, 2));
        assertThat(h.getUpdatedAt()).isNotNull();
        h.addQuantity(bd("10.5")); h.reserveQuantity(bd("4")); h.addQuantity(bd("1"));
        assertThat(h.availableQuantity()).isEqualByComparingTo("7.5");
        h.releaseReservedQuantity(bd("1")); h.settleReservedSale(bd("3"));
        assertThat(h.getTotalQuantity()).isEqualByComparingTo("8.5");
        assertThat(h.getReservedQuantity()).isZero();
        h.reserveQuantity(bd("8.5")); h.settleReservedSale(bd("8.5"));
        assertThat(h.getTotalQuantity()).isZero();
        assertThat(h.availableQuantity()).isZero();
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"0", "-1", "0.000000001", "10000000000000000"})
    void invalidQuantitiesNeverMutateState(String value) {
        BigDecimal q = value == null ? null : bd(value);
        Holding h = new Holding(account(), instrument()); h.addQuantity(bd("10")); h.reserveQuantity(bd("2"));
        assertThatIllegalArgumentException().isThrownBy(() -> h.addQuantity(q));
        assertThatIllegalArgumentException().isThrownBy(() -> h.reserveQuantity(q));
        assertThatIllegalArgumentException().isThrownBy(() -> h.releaseReservedQuantity(q));
        assertThatIllegalArgumentException().isThrownBy(() -> h.settleReservedSale(q));
        assertThat(h.getTotalQuantity()).isEqualByComparingTo("10");
        assertThat(h.getReservedQuantity()).isEqualByComparingTo("2");
    }
    @Test void insufficientResourcesDoNotMutateState() {
        Holding h = new Holding(account(), instrument()); h.addQuantity(bd("5")); h.reserveQuantity(bd("2"));
        assertThatIllegalStateException().isThrownBy(() -> h.reserveQuantity(bd("4")));
        assertThatIllegalStateException().isThrownBy(() -> h.releaseReservedQuantity(bd("3")));
        assertThatIllegalStateException().isThrownBy(() -> h.settleReservedSale(bd("3")));
        assertThat(h.getTotalQuantity()).isEqualByComparingTo("5");
        assertThat(h.getReservedQuantity()).isEqualByComparingTo("2");
    }
    @Test void overflowAndMissingRelationshipsAreRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Holding(null, instrument()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Holding(account(), null));
        Holding h = new Holding(account(), instrument()); h.addQuantity(bd("9999999999999999.99999999"));
        assertThatIllegalArgumentException().isThrownBy(() -> h.addQuantity(bd("0.00000001")));
        assertThat(h.getTotalQuantity()).isEqualByComparingTo("9999999999999999.99999999");
    }
    @Test void trailingZerosDoNotConsumePrecision() {
        Holding h = new Holding(account(), instrument()); h.addQuantity(bd("1.000000000"));
        h.reserveQuantity(bd("1")); h.releaseReservedQuantity(bd("1"));
        assertThat(h.availableQuantity()).isEqualByComparingTo("1");
    }
    @Test void inconsistentPersistedHoldingCannotSettleBeyondOwnedUnits() {
        Holding h = new Holding(account(), instrument()); h.addQuantity(bd("1"));
        id(h, "reservedQuantity", bd("2"));
        assertThatIllegalStateException().isThrownBy(() -> h.settleReservedSale(bd("2"))).withMessageContaining("owned");
        assertThat(h.getTotalQuantity()).isEqualByComparingTo("1"); assertThat(h.getReservedQuantity()).isEqualByComparingTo("2");
    }
}
