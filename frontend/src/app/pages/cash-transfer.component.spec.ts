import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AccountResponse } from '../core/account.service';
import { AccountSelectionService } from '../core/account-selection.service';
import { Receipt } from '../core/trading.service';
import { TradingService } from '../core/trading.service';
import { CashTransferComponent } from './cash-transfer.component';

describe('Shared cash transfer flow', () => {
  const account: AccountResponse = {
    accountId: 1, accountName: 'Primary', accountStatus: 'ACTIVE', openedAt: '2026-10-01T00:00:00Z',
  };

  function setup() {
    const selected = signal<AccountResponse | null>(account);
    const trading = {
      cash: signal(100), loading: signal(false), error: signal(''), pendingOrderCount: signal(0),
      money: (amount: number) => `$${amount.toFixed(2)}`,
      transfer: vi.fn<TradingService['transfer']>().mockResolvedValue({ head: 'Deposit completed', lines: ['Reference CASH-9'] }),
      refresh: vi.fn(),
    };
    TestBed.configureTestingModule({ providers: [
      provideRouter([]),
      { provide: AccountSelectionService, useValue: { selectedAccount: selected } },
      { provide: TradingService, useValue: trading },
    ] });
    const fixture = TestBed.createComponent(CashTransferComponent);
    fixture.detectChanges();
    const dialog = fixture.nativeElement.querySelector('dialog') as HTMLDialogElement;
    // jsdom does not implement native dialog focus management.
    dialog.showModal = vi.fn(() => dialog.setAttribute('open', ''));
    dialog.close = vi.fn(() => dialog.removeAttribute('open'));
    return { fixture, component: fixture.componentInstance, dialog, selected, trading };
  }

  it.each(['deposit', 'withdraw'] as const)('submits a %s for the visible account and shows its receipt', async mode => {
    const { fixture, component, dialog, trading } = setup();
    component.open(mode);
    component.amount = '25.00';
    fixture.detectChanges();
    expect(dialog.open).toBe(true);
    expect(dialog.textContent).toContain('Primary');
    expect(dialog.textContent).toContain('$100.00');
    await component.submit();
    fixture.detectChanges();
    expect(trading.transfer).toHaveBeenCalledExactlyOnceWith(mode, '25.00');
    expect(dialog.textContent).toContain('Reference CASH-9');
    await component.submit();
    expect(trading.transfer).toHaveBeenCalledTimes(1);
  });

  it('disables transfers for pending orders and explains how to recheck', () => {
    const { fixture, component, dialog, trading } = setup();
    trading.pendingOrderCount.set(1);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.cash-actions button').disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('An accepted order is awaiting execution');
    component.open('deposit');
    expect(dialog.open).toBe(false);
    expect(trading.transfer).not.toHaveBeenCalled();
    trading.pendingOrderCount.set(0);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.cash-actions button').disabled).toBe(false);
  });

  it('blocks unavailable account data and inactive or missing accounts', () => {
    const { component, trading, selected, dialog } = setup();
    trading.loading.set(true);
    component.open('deposit');
    expect(dialog.open).toBe(false);
    trading.loading.set(false);
    trading.error.set('Network failure');
    component.open('withdraw');
    expect(dialog.open).toBe(false);
    trading.error.set('');
    selected.set({ ...account, accountStatus: 'CLOSED' });
    component.open('deposit');
    expect(dialog.open).toBe(false);
    selected.set(null);
    expect(component.unavailableReason()).toContain('Select a trading account');
  });

  it('prevents duplicate submissions and ignores results after an account switch', async () => {
    const { fixture, component, trading, selected, dialog } = setup();
    let resolve!: (receipt: Receipt) => void;
    trading.transfer.mockReturnValue(new Promise<Receipt>(done => { resolve = done; }));
    component.open('withdraw');
    component.amount = '25';
    const pending = component.submit();
    await component.submit();
    component.close();
    expect(dialog.open).toBe(true);
    expect(trading.transfer).toHaveBeenCalledTimes(1);
    selected.set({ ...account, accountId: 2, accountName: 'Savings' });
    fixture.detectChanges();
    expect(dialog.open).toBe(false);
    resolve({ head: 'Withdrawal completed', lines: ['Primary receipt'] });
    await pending;
    expect(component.receipt()).toBeNull();
    expect(component.submitting()).toBe(false);
  });

  it('keeps failed transfers open and displays validation feedback', async () => {
    const { fixture, component, trading, dialog } = setup();
    trading.transfer.mockResolvedValue({ message: 'Insufficient cash.', fields: ['amount'] });
    component.open('withdraw');
    component.amount = '200';
    await component.submit();
    fixture.detectChanges();
    expect(dialog.open).toBe(true);
    expect(dialog.textContent).toContain('Insufficient cash.');
    expect(component.receipt()).toBeNull();
    expect(component.submitting()).toBe(false);
  });
});
