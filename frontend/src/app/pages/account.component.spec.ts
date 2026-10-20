import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AccountResponse, AccountService } from '../core/account.service';
import { AccountSelectionService } from '../core/account-selection.service';
import { AccountsCacheService } from '../core/accounts-cache.service';
import { AuthService } from '../core/auth.service';
import { AccountComponent } from './account.component';

describe('Account management', () => {
  it('selects accounts without displaying the relocated cash transfer controls', async () => {
    const primary: AccountResponse = {
      accountId: 1, accountName: 'Primary', accountStatus: 'ACTIVE', openedAt: '2026-10-01T00:00:00Z',
    };
    const savings: AccountResponse = {
      accountId: 2, accountName: 'Savings', accountStatus: 'ACTIVE', openedAt: '2026-10-02T00:00:00Z',
    };
    const selectedAccount = signal<AccountResponse | null>(primary);
    TestBed.configureTestingModule({ providers: [
      provideRouter([]),
      { provide: AuthService, useValue: { currentUser: signal('Test User'), currentUserEmail: signal('test@example.com') } },
      { provide: AccountService, useValue: { createAccount: vi.fn() } },
      { provide: AccountSelectionService, useValue: {
        selectedAccount, selectAccount: (account: AccountResponse) => selectedAccount.set(account),
      } },
      { provide: AccountsCacheService, useValue: {
        loadAccounts: vi.fn().mockResolvedValue([primary, savings]), addAccount: vi.fn(),
      } },
    ] });
    const fixture = TestBed.createComponent(AccountComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    fixture.componentInstance.selectAccount(savings);
    fixture.detectChanges();
    expect(selectedAccount()?.accountId).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('Selected account: Savings');
    expect(fixture.nativeElement.textContent).not.toContain('Deposit');
    expect(fixture.nativeElement.textContent).not.toContain('Withdraw');
  });
});
