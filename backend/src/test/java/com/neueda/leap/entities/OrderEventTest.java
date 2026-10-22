package com.neueda.leap.entities;

import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class OrderEventTest {
    static Stream<Arguments> transitions() {
        List<OrderStatus> statuses = new ArrayList<>(Arrays.asList(OrderStatus.values())); statuses.add(null);
        return statuses.stream().flatMap(previous -> statuses.stream().map(next -> {
            boolean allowed = previous == null ? next == OrderStatus.SUBMITTED
                : previous == OrderStatus.SUBMITTED ? next == OrderStatus.ACCEPTED || next == OrderStatus.REJECTED
                : previous == OrderStatus.ACCEPTED && (next == OrderStatus.FILLED || next == OrderStatus.REJECTED || next == OrderStatus.EXPIRED);
            return Arguments.of(previous, next, allowed);
        }));
    }
    @ParameterizedTest @MethodSource("transitions")
    void exhaustiveTransitionMatrix(OrderStatus previous, OrderStatus next, boolean allowed) {
        assertThat(OrderEvent.canFollow(previous, next)).isEqualTo(allowed);
    }
    @Test void quotesAndReasonsAreValidatedAndNormalized() {
        Order o = order(OrderSide.BUY);
        assertThat(new OrderEvent(o, OrderStatus.REJECTED, "  insufficient cash  ", null, null, AT).getReasonForStatusChange()).isEqualTo("insufficient cash");
        assertThat(new OrderEvent(o, OrderStatus.SUBMITTED, " ", null, null, AT).getReasonForStatusChange()).isNull();
        for (String reason : new String[]{null, "", " "})
            assertThatIllegalArgumentException().isThrownBy(() -> new OrderEvent(o, OrderStatus.REJECTED, reason, null, null, AT));
        for (OrderStatus status : new OrderStatus[]{OrderStatus.ACCEPTED, OrderStatus.FILLED}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new OrderEvent(o, status, null, null, null, AT));
            assertThat(new OrderEvent(o, status, null, bd("1"), AT, AT).getDecisionQuoteAt()).isEqualTo(AT);
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new OrderEvent(null, OrderStatus.SUBMITTED, null, null, null, AT));
        assertThatIllegalArgumentException().isThrownBy(() -> new OrderEvent(o, null, null, null, null, AT));
        assertThatIllegalArgumentException().isThrownBy(() -> new OrderEvent(o, OrderStatus.SUBMITTED, null, null, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new OrderEvent(o, OrderStatus.SUBMITTED, null, bd("1"), null, AT));
        assertThatIllegalArgumentException().isThrownBy(() -> new OrderEvent(o, OrderStatus.SUBMITTED, null, null, AT, AT));
        for (String price : new String[]{"0", "-1"})
            assertThatIllegalArgumentException().isThrownBy(() -> new OrderEvent(o, OrderStatus.ACCEPTED, null, bd(price), AT, AT));
        assertThatIllegalArgumentException().isThrownBy(() -> new OrderEvent(o, OrderStatus.ACCEPTED, null, bd("1"), AT.plusNanos(1), AT));
    }
}
