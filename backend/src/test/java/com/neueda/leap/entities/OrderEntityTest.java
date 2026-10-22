package com.neueda.leap.entities;

import com.neueda.leap.enums.OrderSide;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class OrderEntityTest {
    @Test void acceptsBothSidesAndPreservesSubmissionQuote() {
        for (OrderSide side : OrderSide.values()) {
            Order o = order(side);
            assertThat(o.getSide()).isEqualTo(side); assertThat(o.getRequestedQuantity()).isEqualByComparingTo("2");
            assertThat(o.getSubmittedQuotePrice()).isEqualByComparingTo("100");
            assertThat(o.getSubmittedQuoteAt()).isEqualTo(AT.minusMinutes(10));
            assertThat(o.getSubmittedAt()).isEqualTo(AT.minusSeconds(30));
        }
        assertThatCode(() -> create(bd("9999999999999999.99999999"), bd("0.00000001"))).doesNotThrowAnyException();
        assertThatCode(() -> create(bd("1.000000000"), bd("1.000000000"))).doesNotThrowAnyException();
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"0", "-1", "0.000000001", "10000000000000000"})
    void rejectsInvalidQuantityAndPrice(String value) {
        BigDecimal n = value == null ? null : bd(value);
        assertThatIllegalArgumentException().isThrownBy(() -> create(n, bd("1")));
        assertThatIllegalArgumentException().isThrownBy(() -> create(bd("1"), n));
    }
    @Test void rejectsMissingRelationshipsAndInvalidTimes() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Order(null, instrument(), OrderSide.BUY, bd("1"), bd("1"), AT, AT));
        assertThatIllegalArgumentException().isThrownBy(() -> new Order(account(), null, OrderSide.BUY, bd("1"), bd("1"), AT, AT));
        assertThatIllegalArgumentException().isThrownBy(() -> new Order(account(), instrument(), null, bd("1"), bd("1"), AT, AT));
        assertThatIllegalArgumentException().isThrownBy(() -> new Order(account(), instrument(), OrderSide.BUY, bd("1"), bd("1"), null, AT));
        assertThatIllegalArgumentException().isThrownBy(() -> new Order(account(), instrument(), OrderSide.BUY, bd("1"), bd("1"), AT, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Order(account(), instrument(), OrderSide.BUY, bd("1"), bd("1"), AT.plusNanos(1), AT));
    }
    private Order create(BigDecimal quantity, BigDecimal price) { return new Order(account(), instrument(), OrderSide.BUY, quantity, price, AT, AT); }
}
