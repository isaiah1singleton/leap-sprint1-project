package com.neueda.leap.entities;

import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.enums.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class AccountEntityTest {
    @Test void normalizesNamesAndChangesStatus() {
        Account a = new Account(client(), "  Savings  ");
        assertThat(a.getAccountName()).isEqualTo("Savings"); assertThat(a.isActive()).isTrue();
        a.changeAccountName("  Main  "); assertThat(a.getAccountName()).isEqualTo("Main");
        a.changeStatus(AccountStatus.INACTIVE); assertThat(a.isActive()).isFalse();
        assertThatNullPointerException().isThrownBy(() -> a.changeStatus(null));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ", "\t"})
    void rejectsMissingNameOnConstructionAndRename(String name) {
        assertThatThrownBy(() -> new Account(client(), name)).isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
        Account a = account();
        assertThatThrownBy(() -> a.changeAccountName(name)).isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
        assertThat(a.getAccountName()).isEqualTo("Trading");
    }
    @Test void ownershipUsesStableClientId() {
        Account a = account(); assertThat(a.isOwnedBy(client())).isTrue();
        assertThat(a.isOwnedBy(id(client(), "clientId", 8))).isFalse();
        assertThat(a.isOwnedBy(id(client(), "clientId", null))).isFalse();
        assertThat(a.isOwnedBy(null)).isFalse();
        assertThatNullPointerException().isThrownBy(() -> new Account(null, "Main"));
    }
    @Test void clientNormalizesEmailAndSetsBusinessDefaults() {
        Client c = new Client("Alice", "ALICE@EXAMPLE.COM", "hash");
        assertThat(c.getEmail()).isEqualTo("alice@example.com");
        assertThat(c.getClientName()).isEqualTo("Alice"); assertThat(c.getClientSegment()).isEqualTo("RETAIL");
        assertThat(c.getClientStatus().name()).isEqualTo("ACTIVE"); assertThat(c.getPasswordHash()).isEqualTo("hash");
    }
    @Test void compositeIdsHaveValueEquality() {
        HoldingId original = new HoldingId(1, 2); assertThat(original.equals(original)).isTrue();
        assertThat(original.getAccountId()).isEqualTo(1); assertThat(original.getInstrumentId()).isEqualTo(2);
        CashBalanceId cashId = new CashBalanceId(1, Currency.USD); assertThat(cashId.equals(cashId)).isTrue();
        assertThat(cashId.getAccountId()).isEqualTo(1);
        for (Integer invalid : java.util.Arrays.asList(null, 0, -1)) {
            assertThatIllegalArgumentException().isThrownBy(() -> new HoldingId(invalid, 2));
            assertThatIllegalArgumentException().isThrownBy(() -> new HoldingId(1, invalid));
        }
        assertThatNullPointerException().isThrownBy(() -> new CashBalanceId(1, null));
        assertThat(new HoldingId(1, 2)).isEqualTo(new HoldingId(1, 2)).hasSameHashCodeAs(new HoldingId(1, 2));
        assertThat(new HoldingId(1, 2)).isNotEqualTo(new HoldingId(1, 3)).isNotEqualTo(new HoldingId(2, 2)).isNotEqualTo(null).isNotEqualTo("id");
        assertThat(new CashBalanceId(1, Currency.USD)).isEqualTo(new CashBalanceId(1, Currency.USD)).hasSameHashCodeAs(new CashBalanceId(1, Currency.USD));
        assertThat(new CashBalanceId(1, Currency.USD)).isNotEqualTo(new CashBalanceId(2, Currency.USD)).isNotEqualTo(new CashBalanceId(1, Currency.EUR)).isNotEqualTo(null).isNotEqualTo("id");
    }
}
