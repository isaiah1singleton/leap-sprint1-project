// package com.neueda.leap;

// import static org.assertj.core.api.Assertions.assertThat;
// import static org.assertj.core.api.Assertions.assertThatThrownBy;

// import com.neueda.leap.entities.Account;
// import com.neueda.leap.entities.CashBalance;
// import com.neueda.leap.entities.Client;
// import com.neueda.leap.entities.Money;
// import com.neueda.leap.enums.Currency;
// import java.math.BigDecimal;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;

// class CashBalanceTest {
//     private CashBalance balance;

//     @BeforeEach
//     void setUp() {
//         Client client = new Client("Client", "client@example.com", "hash");
//         balance = new CashBalance(new Account(client, "Main"), Currency.USD);
//     }

//     @Test
//     void reservesReleasesDepositsAndWithdrawsWithoutSpendingReservedCash() {
//         balance.depositCash(usd("100"));
//         balance.reserveCash(usd("70"));
//         assertThat(balance.availableBalance().amount()).isEqualByComparingTo("30");

//         balance.releaseReservedCash(usd("20"));
//         balance.withdrawCash(usd("50"));

//         assertThat(balance.getTotalBalance().amount()).isEqualByComparingTo("50");
//         assertThat(balance.getReservedBalance().amount()).isEqualByComparingTo("50");
//         assertThat(balance.availableBalance().amount()).isEqualByComparingTo("0");
//         assertThat(balance.getUpdatedAt()).isNotNull();
//     }

//     @Test
//     void refusesToReserveOrWithdrawMoreThanAvailable() {
//         balance.depositCash(usd("100"));
//         balance.reserveCash(usd("80"));

//         assertThatThrownBy(() -> balance.reserveCash(usd("21")))
//                 .isInstanceOf(IllegalStateException.class);
//         assertThatThrownBy(() -> balance.withdrawCash(usd("21")))
//                 .isInstanceOf(IllegalStateException.class);

//         assertThat(balance.getTotalBalance().amount()).isEqualByComparingTo("100");
//         assertThat(balance.getReservedBalance().amount()).isEqualByComparingTo("80");
//     }

//     @Test
//     void refusesToReleaseMoreThanReserved() {
//         balance.depositCash(usd("100"));
//         balance.reserveCash(usd("10"));

//         assertThatThrownBy(() -> balance.releaseReservedCash(usd("11")))
//                 .isInstanceOf(IllegalStateException.class);

//         assertThat(balance.getReservedBalance().amount()).isEqualByComparingTo("10");
//     }

//     @Test
//     void requiresPositiveSameCurrencyAmountsWithinStoredPrecision() {
//         assertThatThrownBy(() -> balance.depositCash(null)).isInstanceOf(IllegalArgumentException.class);
//         assertThatThrownBy(() -> balance.depositCash(usd("0"))).isInstanceOf(IllegalArgumentException.class);
//         assertThatThrownBy(() -> balance.reserveCash(usd("-1"))).isInstanceOf(IllegalArgumentException.class);
//         assertThatThrownBy(() -> balance.withdrawCash(new Money(BigDecimal.ONE, Currency.EUR)))
//                 .isInstanceOf(IllegalArgumentException.class);
//         assertThatThrownBy(() -> balance.depositCash(usd("0.000000001")))
//                 .isInstanceOf(IllegalArgumentException.class);

//         assertThat(balance.getTotalBalance().amount()).isEqualByComparingTo("0");
//         assertThat(balance.getReservedBalance().amount()).isEqualByComparingTo("0");
//     }

//     private static Money usd(String amount) {
//         return new Money(new BigDecimal(amount), Currency.USD);
//     }
// }
