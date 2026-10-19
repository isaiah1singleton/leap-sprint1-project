import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AccountSelectionService } from '../core/account-selection.service';
import { OrderActivity, TradingService } from '../core/trading.service';
import { OrdersComponent } from './orders.component';

describe('Orders history pagination', () => {
  const activities: OrderActivity[] = Array.from({ length: 12 }, (_, index) => ({
    id: `ORD-${12 - index}`,
    orderId: 12 - index,
    date: '16/10/2026 12:00:00 UTC',
    timestamp: '2026-10-16T12:00:00Z',
    type: 'Buy',
    sym: 'AAPL',
    qty: '1',
    indicativePrice: '$100.00',
    executionPrice: '$101.00',
    status: 'FILLED',
    reason: null,
  }));

  it('shows ten rows, navigates to the remainder, and resets for another account', () => {
    const trading = {
      orders: signal(activities),
      fillExecutionPrices: signal<Record<number, string>>({}),
      loading: signal(false),
      error: signal(''),
      refresh: vi.fn(),
      ensureFillExecutionPrice: vi.fn(),
    };
    const account = { selectedAccount: signal({ accountId: 1 }) };
    TestBed.configureTestingModule({ providers: [
      provideRouter([]),
      { provide: TradingService, useValue: trading },
      { provide: AccountSelectionService, useValue: account },
    ] });
    const fixture = TestBed.createComponent(OrdersComponent);
    fixture.detectChanges();
    const rows = () => fixture.nativeElement.querySelectorAll('tbody tr') as NodeListOf<HTMLTableRowElement>;
    const button = (label: string) => Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find(item => item.textContent?.trim() === label)!;

    expect(rows()).toHaveLength(10);
    expect(rows()[0].textContent).toContain('ORD-12');
    expect(rows()[0].textContent).toContain('$101.00');
    expect(button('Previous').disabled).toBe(true);
    button('Next').click();
    fixture.detectChanges();
    expect(rows()).toHaveLength(2);
    expect(rows()[0].textContent).toContain('ORD-2');
    expect(fixture.nativeElement.textContent).toContain('Showing 11–12 of 12');
    expect(button('Next').disabled).toBe(true);

    account.selectedAccount.set({ accountId: 2 });
    fixture.detectChanges();
    expect(rows()).toHaveLength(10);
    expect(fixture.nativeElement.textContent).toContain('Page 1 of 2');
  });

  it('clamps the displayed page when the history becomes shorter', () => {
    const trading = {
      orders: signal(activities),
      fillExecutionPrices: signal<Record<number, string>>({}),
      loading: signal(false),
      error: signal(''),
      refresh: vi.fn(),
      ensureFillExecutionPrice: vi.fn(),
    };
    TestBed.configureTestingModule({ providers: [
      provideRouter([]),
      { provide: TradingService, useValue: trading },
      { provide: AccountSelectionService, useValue: { selectedAccount: signal({ accountId: 1 }) } },
    ] });
    const fixture = TestBed.createComponent(OrdersComponent);
    fixture.detectChanges();
    fixture.componentInstance.nextPage();
    trading.orders.set(activities.slice(0, 3));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(3);
    expect(fixture.nativeElement.textContent).toContain('Showing 1–3 of 3');
    expect(fixture.nativeElement.textContent).not.toContain('Page 2');
  });

  it('shows the shared fill price when the orders list omits it', () => {
    const fillExecutionPrices = signal<Record<number, string>>({});
    const trading = {
      orders: signal([{ ...activities[0], executionPrice: null }]),
      fillExecutionPrices,
      loading: signal(false),
      error: signal(''),
      refresh: vi.fn(),
      ensureFillExecutionPrice: vi.fn((orderId: number) =>
        fillExecutionPrices.set({ [orderId]: '$102.25' })),
    };
    TestBed.configureTestingModule({ providers: [
      provideRouter([]),
      { provide: TradingService, useValue: trading },
      { provide: AccountSelectionService, useValue: { selectedAccount: signal({ accountId: 1 }) } },
    ] });
    const fixture = TestBed.createComponent(OrdersComponent);
    fixture.detectChanges();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('tbody tr').textContent).toContain('$102.25');
    expect(trading.ensureFillExecutionPrice).toHaveBeenCalledWith(12);
  });
});
