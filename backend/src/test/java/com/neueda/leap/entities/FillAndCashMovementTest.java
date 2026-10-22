package com.neueda.leap.entities;

import com.neueda.leap.enums.*;
import com.neueda.leap.enums.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class FillAndCashMovementTest {
    @Test void fillDerivesQuantityCurrencyAndValidatesRequiredFields() {
        Order o = order(OrderSide.BUY); Fill f = fill(o);
        assertThat(f.getOrder()).isSameAs(o); assertThat(f.getFillQuantity()).isEqualByComparingTo("2");
        assertThat(f.getExecutionCurrency()).isEqualTo(Currency.USD); assertThat(f.getExecutionPrice()).isEqualByComparingTo("101");
        assertThat(f.getExecutionTime()).isEqualTo(AT);
        assertThatIllegalArgumentException().isThrownBy(() -> new Fill(null, bd("1"), AT));
        assertThatIllegalArgumentException().isThrownBy(() -> new Fill(o, bd("1"), null));
        for (String p : new String[]{null, "0", "-1"})
            assertThatIllegalArgumentException().isThrownBy(() -> new Fill(o, p == null ? null : bd(p), AT));
    }
    @Test void movementSignsAndLineageAreCorrect() {
        Account a = account();
        CashMovement deposit = CashMovement.deposit(a, bd("20"), Currency.USD, AT);
        assertThat(deposit.getAmount()).isEqualByComparingTo("20"); assertThat(deposit.getFill()).isNull();
        assertThat(deposit.getAccount()).isSameAs(a); assertThat(deposit.getOccurredAt()).isEqualTo(AT);
        assertThat(deposit.getCashMovementType()).isEqualTo(CashMovementType.DEPOSIT);
        assertThat(CashMovement.withdrawal(a, bd("5"), Currency.USD, AT).getAmount()).isEqualByComparingTo("-5");
        for (OrderSide side : OrderSide.values()) {
            Fill f = fill(order(side)); CashMovement m = CashMovement.forTrade(f, usd("202"));
            assertThat(m.getAmount()).isEqualByComparingTo(side == OrderSide.BUY ? "-202" : "202");
            assertThat(m.getFill()).isSameAs(f); assertThat(m.getCurrency()).isEqualTo(Currency.USD);
            assertThat(m.getCashMovementType()).isEqualTo(CashMovementType.TRADE);
        }
        for (String amount : new String[]{"1", "-1"})
            assertThat(CashMovement.adjustment(a, bd(amount), Currency.USD, "  reconciliation  ", AT).getReason()).isEqualTo("reconciliation");
    }
    @Test void invalidMovementsAndSettlementAreRejected() {
        for (String value : new String[]{null, "0", "-1"}) {
            var n = value == null ? null : bd(value);
            assertThatIllegalArgumentException().isThrownBy(() -> CashMovement.deposit(account(), n, Currency.USD, AT));
            assertThatIllegalArgumentException().isThrownBy(() -> CashMovement.withdrawal(account(), n, Currency.USD, AT));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> CashMovement.deposit(null, bd("1"), Currency.USD, AT));
        assertThatIllegalArgumentException().isThrownBy(() -> CashMovement.deposit(account(), bd("1"), null, AT));
        assertThatIllegalArgumentException().isThrownBy(() -> CashMovement.deposit(account(), bd("1"), Currency.USD, null));
        assertThatIllegalArgumentException().isThrownBy(() -> CashMovement.adjustment(account(), null, Currency.USD, "reason", AT));
        assertThatIllegalArgumentException().isThrownBy(() -> CashMovement.adjustment(account(), bd("0"), Currency.USD, "reason", AT));
        for (String reason : new String[]{null, "", " "})
            assertThatIllegalArgumentException().isThrownBy(() -> CashMovement.adjustment(account(), bd("1"), Currency.USD, reason, AT));
        Fill f = fill(order(OrderSide.BUY));
        assertThatIllegalArgumentException().isThrownBy(() -> CashMovement.forTrade(null, usd("1")));
        assertThatIllegalArgumentException().isThrownBy(() -> CashMovement.forTrade(f, null));
        assertThatIllegalArgumentException().isThrownBy(() -> CashMovement.forTrade(f, new Money(bd("1"), Currency.EUR)));
        for (String value : new String[]{"0", "-1", "0.000000001", "10000000000000000"})
            assertThatIllegalArgumentException().isThrownBy(() -> CashMovement.forTrade(f, usd(value)));
    }
    @Test void tradeMovementRejectsBrokenPersistedFillRelationships() {
        Fill f = org.mockito.Mockito.mock(Fill.class);
        assertThatIllegalArgumentException().isThrownBy(() -> CashMovement.forTrade(f, usd("1")));
        Order o = org.mockito.Mockito.mock(Order.class); org.mockito.Mockito.when(f.getOrder()).thenReturn(o);
        assertThatIllegalArgumentException().isThrownBy(() -> CashMovement.forTrade(f, usd("1")));
        org.mockito.Mockito.when(o.getSide()).thenReturn(OrderSide.BUY);
        assertThatIllegalArgumentException().isThrownBy(() -> CashMovement.forTrade(f, usd("1")));
        org.mockito.Mockito.when(f.getFillQuantity()).thenReturn(bd("1"));
        assertThatIllegalArgumentException().isThrownBy(() -> CashMovement.forTrade(f, usd("1")));
    }
}
