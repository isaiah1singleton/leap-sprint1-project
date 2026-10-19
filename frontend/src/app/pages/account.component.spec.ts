import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AccountResponse, AccountService } from '../core/account.service';
import { AccountSelectionService } from '../core/account-selection.service';
import { AccountsCacheService } from '../core/accounts-cache.service';
import { AuthService } from '../core/auth.service';
import { TradingService } from '../core/trading.service';
import { AccountComponent } from './account.component';

describe('Account cash transfers', () => {
  it('selects the requested account and submits the chosen transfer from its details', async () => {
    const primary: AccountResponse = {
      accountId: 1, accountName: 'Primary', accountStatus: 'ACTIVE', openedAt: '2026-10-01T00:00:00Z',
    };
    const savings: AccountResponse = {
      accountId: 2, accountName: 'Savings', accountStatus: 'ACTIVE', openedAt: '2026-10-02T00:00:00Z',
    };
    const selectedAccount = signal<AccountResponse | null>(primary);
    const transfer = vi.fn().mockResolvedValue({
      head: 'Deposit completed', lines: ['Amount $25.00', 'Reference CASH-9'],
    });
    TestBed.configureTestingModule({ providers: [
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ mode: 'withdraw' }) } } },
      { provide: AuthService, useValue: { currentUser: signal('Test User'), currentUserEmail: signal('test@example.com') } },
      { provide: AccountService, useValue: { createAccount: vi.fn() } },
      { provide: AccountSelectionService, useValue: {
        selectedAccount, selectAccount: (account: AccountResponse) => selectedAccount.set(account),
      } },
      { provide: AccountsCacheService, useValue: {
        loadAccounts: vi.fn().mockResolvedValue([primary, savings]), addAccount: vi.fn(),
      } },
      { provide: TradingService, useValue: {
        cash: signal(100), loading: signal(false), error: signal(''),
        money: (amount: number) => `$${amount.toFixed(2)}`, transfer,
      } },
    ] });
    const fixture = TestBed.createComponent(AccountComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.componentInstance.transferMode).toBe('withdraw');
    fixture.componentInstance.selectAccount(savings, 'deposit');
    fixture.componentInstance.transferAmount = '25.00';
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Manage cash · Savings');

    await fixture.componentInstance.submitTransfer();
    fixture.detectChanges();
    expect(selectedAccount()?.accountId).toBe(2);
    expect(transfer).toHaveBeenCalledExactlyOnceWith('deposit', '25.00');
    expect(fixture.nativeElement.textContent).toContain('Deposit completed');
  });
});
