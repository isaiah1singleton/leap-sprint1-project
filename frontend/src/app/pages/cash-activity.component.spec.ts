import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AccountSelectionService } from '../core/account-selection.service';
import { CashActivity, TradingService } from '../core/trading.service';
import { CashActivityComponent } from './cash-activity.component';

describe('Cash activity page', () => {
  it('shows cash movements separately with pagination and resets on account change', () => {
    const movements: CashActivity[] = Array.from({ length: 12 }, (_, index) => ({
      id: `CASH-${12 - index}`,
      date: '16/10/2026 12:00:00 UTC',
      timestamp: '2026-10-16T12:00:00Z',
      type: index % 2 ? 'Withdraw' : 'Deposit',
      amount: index % 2 ? '−$10.00' : '+$10.00',
      reason: null,
    }));
    const trading = {
      cashActivity: signal(movements), loading: signal(false),
      error: signal(''), refresh: vi.fn(),
    };
    const account = { selectedAccount: signal({ accountId: 1 }) };
    TestBed.configureTestingModule({ providers: [
      provideRouter([]),
      { provide: TradingService, useValue: trading },
      { provide: AccountSelectionService, useValue: account },
    ] });
    const fixture = TestBed.createComponent(CashActivityComponent);
    fixture.detectChanges();
    const rows = () => fixture.nativeElement.querySelectorAll('tbody tr') as NodeListOf<HTMLTableRowElement>;
    expect(rows()).toHaveLength(10);
    expect(rows()[0].textContent).toContain('CASH-12');
    fixture.componentInstance.nextPage();
    fixture.detectChanges();
    expect(rows()).toHaveLength(2);
    expect(fixture.nativeElement.textContent).toContain('Showing 11–12 of 12');

    account.selectedAccount.set({ accountId: 2 });
    fixture.detectChanges();
    expect(rows()).toHaveLength(10);
    expect(fixture.nativeElement.textContent).toContain('Page 1 of 2');
  });
});
