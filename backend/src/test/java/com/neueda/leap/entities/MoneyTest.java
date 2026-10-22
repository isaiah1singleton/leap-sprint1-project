package com.neueda.leap.entities;

import com.neueda.leap.enums.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class MoneyTest {
    @Test void arithmeticIsExactAndPreservesCurrency() {
        Money original = usd("0.10");
        assertThat(original.add(usd("0.20"))).isEqualTo(usd("0.30"));
        assertThat(original.subtract(usd("0.20"))).isEqualTo(usd("-0.10"));
        assertThat(original.multiply(bd("3"))).isEqualTo(usd("0.30"));
        assertThat(original).isEqualTo(usd("0.10"));
        assertThat(usd("0").amount()).isZero();
    }
    @Test void missingValuesAreRejected() {
        assertThatNullPointerException().isThrownBy(() -> new Money(null, Currency.USD));
        assertThatNullPointerException().isThrownBy(() -> new Money(bd("1"), null));
        assertThatNullPointerException().isThrownBy(() -> usd("1").multiply(null));
        assertThatNullPointerException().isThrownBy(() -> new QuoteSnapshot(null, AT));
        assertThatNullPointerException().isThrownBy(() -> new QuoteSnapshot(usd("1"), null));
    }
    @Test void incompatibleOperationsAreRejected() {
        Money eur = new Money(bd("1"), Currency.EUR);
        assertThatIllegalArgumentException().isThrownBy(() -> usd("1").add(eur));
        assertThatIllegalArgumentException().isThrownBy(() -> usd("1").subtract(eur));
        assertThatIllegalArgumentException().isThrownBy(() -> usd("1").add(null));
        assertThatIllegalArgumentException().isThrownBy(() -> usd("1").subtract(null));
    }
}
