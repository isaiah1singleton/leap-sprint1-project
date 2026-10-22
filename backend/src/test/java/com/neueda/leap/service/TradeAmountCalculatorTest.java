package com.neueda.leap.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class TradeAmountCalculatorTest {
    private final TradeAmountCalculator calculator = new TradeAmountCalculator();
    @ParameterizedTest @CsvSource({"2,101,202", "0.5,0.01,0.005", "1,0.123456785,0.12345678", "1,0.123456795,0.12345680", "1,9999999999999999.99999999,9999999999999999.99999999"})
    void calculatesWithHalfEvenRounding(String quantity, String price, String expected) {
        var result = calculator.calculate(bd(quantity), usd(price));
        assertThat(result.amount()).isEqualByComparingTo(expected);
        assertThat(result.currency()).isEqualTo(com.neueda.leap.enums.Currency.USD);
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"0", "-1", "0.000000001", "10000000000000000"})
    void invalidQuantity(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> calculator.calculate(value == null ? null : bd(value), usd("1")));
    }
    @Test void invalidPriceUnderflowAndOverflow() {
        assertThatIllegalArgumentException().isThrownBy(() -> calculator.calculate(bd("1"), null));
        for (String p : new String[]{"0", "-1", "0.000000001", "10000000000000000"})
            assertThatIllegalArgumentException().isThrownBy(() -> calculator.calculate(bd("1"), usd(p)));
        assertThat(calculator.calculate(bd("1.000000000"), usd("1")).amount()).isEqualByComparingTo("1");
    }
    @Test void settlementPreservesForeignCurrencyWithoutConversion() {
        var price = new com.neueda.leap.entities.Money(bd("3.25"), com.neueda.leap.enums.Currency.EUR);
        var settlement = calculator.calculate(bd("2"), price);
        assertThat(settlement.currency()).isEqualTo(com.neueda.leap.enums.Currency.EUR);
        assertThat(settlement.amount()).isEqualByComparingTo("6.50");
    }
}
