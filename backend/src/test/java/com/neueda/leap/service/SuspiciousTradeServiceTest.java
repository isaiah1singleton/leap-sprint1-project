package com.neueda.leap.service;

import com.neueda.leap.entities.*;
import com.neueda.leap.enums.*;
import com.neueda.leap.repository.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class SuspiciousTradeServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final SuspiciousTradeFlagRepository flags = mock(SuspiciousTradeFlagRepository.class);
    private final Account account = account();
    private final SuspiciousTradeService service = new SuspiciousTradeService(orders, accounts, flags, CLOCK);
    @Test void flagFreezesAccountAndRemembersItsOriginalStatus() {
        when(orders.findByIdForUpdate(3)).thenReturn(Optional.of(order(account, instrument(), OrderSide.BUY)));
        when(accounts.findByAccountId(1)).thenReturn(Optional.of(account)); service.flag(3, "admin");
        assertThat(account.isActive()).isFalse(); ArgumentCaptor<SuspiciousTradeFlag> saved = ArgumentCaptor.forClass(SuspiciousTradeFlag.class);
        verify(flags).save(saved.capture()); assertThat(saved.getValue().getPreviousAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.getValue().getOrderId()).isEqualTo(3);
        when(flags.findByOrderId(3)).thenReturn(Optional.of(saved.getValue())); service.flag(3, "admin"); verify(flags, times(1)).save(any());
    }
    @Test void finalFlagRestoresOnlyAnOriginallyActiveStillInactiveAccount() {
        account.changeStatus(AccountStatus.INACTIVE); when(accounts.findByAccountId(1)).thenReturn(Optional.of(account));
        when(flags.findByOrderId(3)).thenReturn(Optional.of(new SuspiciousTradeFlag(3, 1, "admin", AT, AccountStatus.ACTIVE)));
        when(flags.existsByAccountId(1)).thenReturn(true); service.removeFlag(3); assertThat(account.isActive()).isFalse();
        when(flags.existsByAccountId(1)).thenReturn(false); service.removeFlag(3); assertThat(account.isActive()).isTrue();
        account.changeStatus(AccountStatus.INACTIVE);
        when(flags.findByOrderId(3)).thenReturn(Optional.of(new SuspiciousTradeFlag(3, 1, "admin", AT, AccountStatus.INACTIVE)));
        service.removeFlag(3); assertThat(account.isActive()).isFalse();
        account.changeStatus(AccountStatus.ACTIVE); service.removeFlag(3); assertThat(account.isActive()).isTrue();
    }
    @Test void missingTradesAccountsAndFlagsFailWithoutInventingState() {
        assertThatThrownBy(() -> service.flag(3, "admin")).hasMessageContaining("404");
        when(orders.findByIdForUpdate(3)).thenReturn(Optional.of(order(OrderSide.BUY)));
        assertThatThrownBy(() -> service.flag(3, "admin")).hasMessageContaining("404");
        assertThatThrownBy(() -> service.removeFlag(3)).hasMessageContaining("404");
        when(flags.findByOrderId(3)).thenReturn(Optional.of(new SuspiciousTradeFlag(3, 1, "admin", AT, AccountStatus.ACTIVE)));
        assertThatThrownBy(() -> service.removeFlag(3)).hasMessageContaining("404"); verify(flags, never()).save(any()); verify(flags, never()).delete(any());
    }
}
