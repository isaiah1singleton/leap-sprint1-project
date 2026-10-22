package com.neueda.leap.entities;

import com.neueda.leap.enums.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class CashBalanceEntityTest {
    @Test void depositsWithdrawalsAndExactBalanceAreSupported() {
        CashBalance cash = new CashBalance(account(), Currency.USD);
        assertThat(cash.getTotalBalance().amount()).isZero();
        assertThat(cash.getId()).isEqualTo(new CashBalanceId(1, Currency.USD));
        assertThat(cash.getUpdatedAt()).isNotNull();
        cash.depositCash(usd("0.1")); cash.depositCash(usd("0.20"));
        cash.withdrawCash(usd("0.10"));
        assertThat(cash.getTotalBalance().amount()).isEqualByComparingTo("0.20");
        cash.withdrawCash(usd("0.2")); assertThat(cash.getTotalBalance().amount()).isZero();
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"0", "-1", "0.000000001", "10000000000000000"})
    void invalidAmountsLeaveBalanceUnchanged(String amount) {
        CashBalance cash = new CashBalance(account(), Currency.USD); cash.depositCash(usd("10"));
        Money m = amount == null ? null : usd(amount);
        assertThatIllegalArgumentException().isThrownBy(() -> cash.depositCash(m));
        assertThatIllegalArgumentException().isThrownBy(() -> cash.withdrawCash(m));
        assertThat(cash.getTotalBalance().amount()).isEqualByComparingTo("10");
    }
    @Test void rejectsOverdraftCurrencyMismatchAndOverflowWithoutMutation() {
        CashBalance cash = new CashBalance(account(), Currency.USD); cash.depositCash(usd("10"));
        assertThatIllegalStateException().isThrownBy(() -> cash.withdrawCash(usd("10.00000001")));
        Money eur = new Money(bd("1"), Currency.EUR);
        assertThatIllegalArgumentException().isThrownBy(() -> cash.depositCash(eur));
        assertThatIllegalArgumentException().isThrownBy(() -> cash.withdrawCash(eur));
        assertThat(cash.getTotalBalance().amount()).isEqualByComparingTo("10");
        cash.depositCash(usd("9999999999999989.99999999"));
        assertThatIllegalStateException().isThrownBy(() -> cash.depositCash(usd("0.00000001")));
        assertThat(cash.getTotalBalance().amount()).isEqualByComparingTo("9999999999999999.99999999");
    }
    @Test void requiredRelationshipsAndTrailingZeros() {
        assertThatNullPointerException().isThrownBy(() -> new CashBalance(null, Currency.USD));
        assertThatNullPointerException().isThrownBy(() -> new CashBalance(account(), null));
        CashBalance cash = new CashBalance(account(), Currency.USD); cash.depositCash(usd("1.000000000"));
        assertThat(cash.getTotalBalance().amount()).isEqualByComparingTo("1");
    }
}
