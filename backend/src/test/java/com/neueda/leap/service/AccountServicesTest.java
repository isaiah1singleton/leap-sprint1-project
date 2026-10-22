package com.neueda.leap.service;

import com.neueda.leap.entities.*;
import com.neueda.leap.enums.*;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.models.TransferRequest;
import com.neueda.leap.repository.*;
import jakarta.persistence.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class AccountServicesTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final ClientRepository clients = mock(ClientRepository.class);
    private final CashBalanceRepository balances = mock(CashBalanceRepository.class);
    private final HoldingRepository holdings = mock(HoldingRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final CashMovementService movements = mock(CashMovementService.class);
    private final EntityManager em = mock(EntityManager.class);
    private final Account account = account();
    private final AccountService service = new AccountService(accounts, clients);
    private final AccountDataService data = new AccountDataService(accounts, balances, holdings, orders, movements, em);
    private void owned() { when(accounts.findByAccountIdAndClient_ClientId(1, 7)).thenReturn(Optional.of(account)); }
    @Test void opensTrimmedAccountForActiveClient() {
        when(clients.findById(7)).thenReturn(Optional.of(client()));
        when(accounts.save(any())).thenAnswer(c -> id(c.getArgument(0), "accountId", 1));
        assertThat(service.openAccount(7, "  Savings  ").accountName()).isEqualTo("Savings");
        verify(accounts).existsByClient_ClientIdAndAccountNameIgnoreCase(7, "Savings");
    }
    @Test void invalidDuplicateMissingAndInactiveClientsCannotOpenAccounts() {
        for (String name : new String[]{null, "", " "}) assertThatIllegalArgumentException().isThrownBy(() -> service.openAccount(7, name));
        when(accounts.existsByClient_ClientIdAndAccountNameIgnoreCase(7, "Main")).thenReturn(true);
        assertThatIllegalStateException().isThrownBy(() -> service.openAccount(7, "Main"));
        assertThatThrownBy(() -> service.openAccount(7, "Other")).isInstanceOf(NoSuchElementException.class);
        Client inactive = id(client(), "clientStatus", ClientStatus.INACTIVE); when(clients.findById(7)).thenReturn(Optional.of(inactive));
        assertThatIllegalStateException().isThrownBy(() -> service.openAccount(7, "Other")); verify(accounts, never()).save(any());
    }
    @Test void listAndDetailUseClientScope() {
        assertThat(service.getAccounts(7)).isEmpty();
        when(accounts.findByClient_ClientId(7)).thenReturn(List.of(account));
        assertThat(service.getAccounts(7)).extracting("accountId").containsExactly(1);
        owned(); assertThat(service.getAccount(1, 7).accountName()).isEqualTo("Trading");
        assertThatThrownBy(() -> service.getAccount(1, 8)).isInstanceOf(NoSuchElementException.class);
    }
    @Test void missingBalanceIsZeroAndHoldingsIncludeReservations() {
        owned(); assertThat(data.balance(1, 7).totalBalance()).isZero();
        CashBalance cash = new CashBalance(account, Currency.USD); cash.depositCash(usd("25"));
        when(balances.findById(new CashBalanceId(1, Currency.USD))).thenReturn(Optional.of(cash));
        assertThat(data.balance(1, 7).totalBalance()).isEqualByComparingTo("25");
        Holding h = new Holding(account, instrument()); h.addQuantity(bd("3")); h.reserveQuantity(bd("1"));
        when(holdings.findOwnedHoldings(1, 7)).thenReturn(List.of(h));
        assertThat(data.holdings(1, 7).get(0).availableQuantity()).isEqualByComparingTo("2");
        assertThatThrownBy(() -> data.balance(1, 8)).isInstanceOf(NoSuchElementException.class);
        verify(balances, never()).findById(new CashBalanceId(8, Currency.USD));
    }
    @Test void depositCreatesBalanceAndRecordsMovement() {
        owned(); when(balances.saveAndFlush(any())).thenAnswer(c -> c.getArgument(0));
        when(movements.recordDeposit(account, bd("12.34"), Currency.USD)).thenReturn(CashMovement.deposit(account, bd("12.34"), Currency.USD, AT));
        var result = data.transfer(1, 7, new TransferRequest(CashMovementType.DEPOSIT, bd("12.34")));
        assertThat(result.balance().totalBalance()).isEqualByComparingTo("12.34");
        assertThat(result.movement().amount().amount()).isEqualByComparingTo("12.34");
    }
    @Test void withdrawalChangesExistingBalanceAndRecordsSignedMovement() {
        owned(); CashBalance cash = new CashBalance(account, Currency.USD); cash.depositCash(usd("10"));
        when(em.find(CashBalance.class, new CashBalanceId(1, Currency.USD), LockModeType.PESSIMISTIC_WRITE)).thenReturn(cash);
        when(movements.recordWithdrawal(account, bd("10"), Currency.USD)).thenReturn(CashMovement.withdrawal(account, bd("10"), Currency.USD, AT));
        assertThat(data.transfer(1, 7, new TransferRequest(CashMovementType.WITHDRAW, bd("10"))).balance().totalBalance()).isZero();
        assertThatIllegalStateException().isThrownBy(() -> data.transfer(1, 7, new TransferRequest(CashMovementType.WITHDRAW, bd("1"))));
        verify(movements, times(1)).recordWithdrawal(any(), any(), any());
    }
    @Test void rejectsInvalidTransfersBeforeAnyStorageAccess() {
        List<TransferRequest> invalid = new ArrayList<>(); invalid.add(null);
        invalid.add(new TransferRequest(null, bd("1"))); invalid.add(new TransferRequest(CashMovementType.TRADE, bd("1")));
        for (String n : new String[]{null, "0", "-1", "1.001", "10000000000000000"})
            invalid.add(new TransferRequest(CashMovementType.DEPOSIT, n == null ? null : bd(n)));
        invalid.forEach(r -> assertThatIllegalArgumentException().isThrownBy(() -> data.transfer(1, 7, r)));
        verifyNoInteractions(accounts, balances, em, movements);
    }
    @Test void inactivePendingOrNonOwnedAccountCannotTransfer() {
        var r = new TransferRequest(CashMovementType.DEPOSIT, bd("1"));
        assertThatThrownBy(() -> data.transfer(1, 8, r)).isInstanceOf(NoSuchElementException.class);
        owned(); account.changeStatus(AccountStatus.INACTIVE);
        assertThatIllegalStateException().isThrownBy(() -> data.transfer(1, 7, r));
        account.changeStatus(AccountStatus.ACTIVE); when(orders.existsPendingAcceptedOrder(1)).thenReturn(true);
        assertThatIllegalStateException().isThrownBy(() -> data.transfer(1, 7, r)); verifyNoInteractions(movements);
        for (Integer id : Arrays.asList(null, 0, -1)) assertThatIllegalArgumentException().isThrownBy(() -> data.balance(id, 7));
    }
    @Test void transferOverflowDoesNotCreateMovement() {
        owned(); CashBalance cash = new CashBalance(account, Currency.USD); cash.depositCash(usd("9999999999999999"));
        when(em.find(CashBalance.class, new CashBalanceId(1, Currency.USD), LockModeType.PESSIMISTIC_WRITE)).thenReturn(cash);
        assertThatIllegalStateException().isThrownBy(() -> data.transfer(1, 7, new TransferRequest(CashMovementType.DEPOSIT, bd("1"))));
        verifyNoInteractions(movements);
    }
}
