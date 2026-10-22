package com.neueda.leap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neueda.leap.entities.Account;
import com.neueda.leap.entities.CashBalance;
import com.neueda.leap.entities.Client;
import com.neueda.leap.entities.Money;
import com.neueda.leap.enums.Currency;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("unit")
class CashBalanceTest {
    @Test
    void withdrawsFromTotalBalanceAndRejectsOverdrafts() {
        CashBalance balance = new CashBalance(
                new Account(new Client("Client", "client@example.com", "hash"), "Main"),
                Currency.USD);
        balance.depositCash(usd("100"));
        balance.withdrawCash(usd("40"));

        assertThat(balance.getTotalBalance().amount()).isEqualByComparingTo("60");
        assertThatThrownBy(() -> balance.withdrawCash(usd("61")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Insufficient cash to withdraw.");
        assertThat(balance.getTotalBalance().amount()).isEqualByComparingTo("60");
    }

    private static Money usd(String amount) {
        return new Money(new BigDecimal(amount), Currency.USD);
    }
}
