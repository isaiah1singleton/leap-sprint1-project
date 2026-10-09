import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountResponse } from './account.service';
import { AccountSelectionService } from './account-selection.service';
import { TradingService } from './trading.service';
import { vi } from 'vitest';

describe('TradingService account data', () => {
  let service: TradingService;
  let http: HttpTestingController;
  const selected = signal<AccountResponse | null>(null);
  const account: AccountResponse = {
    accountId: 42, accountName: 'Test account', accountStatus: 'ACTIVE',
    openedAt: '2026-10-07T00:00:00Z',
  };

  beforeEach(() => {
    selected.set(account);
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(), provideHttpClientTesting(),
      { provide: AccountSelectionService, useValue: { selectedAccount: selected } },
    ] });
    service = TestBed.inject(TradingService);
    http = TestBed.inject(HttpTestingController);
    TestBed.tick();
  });

  afterEach(() => http.verify());

  function flushAccount(id: number, cash: number): void {
    http.expectOne(`/api/accounts/${id}/balance`).flush({ totalBalance: cash, currency: 'USD' });
    http.expectOne(`/api/accounts/${id}/holdings`).flush([]);
    http.expectOne(request => request.url === '/api/orders' && request.params.get('accountId') === String(id)).flush([]);
    http.expectOne(`/api/accounts/${id}/cash-movements`).flush([]);
  }

  it('loads the selected account from the backend and clears data on account change', async () => {
    expect(service.cash()).toBe(0);
    flushAccount(42, 125);
    await vi.waitFor(() => expect(service.cash()).toBe(125));
    selected.set({ ...account, accountId: 43 });
    TestBed.tick();
    expect(service.cash()).toBe(0);
    flushAccount(43, 0);
    await vi.waitFor(() => expect(service.loading()).toBe(false));
  });

  it('submits an order and presents the persisted rejection without changing cash', async () => {
    flushAccount(42, 100);
    await Promise.resolve();
    const result = service.trade('buy', 7, '2');
    http.expectOne('/api/orders').flush({
      orderId: 5, symbol: 'AAPL', side: 'BUY', quantity: 2, indicativePrice: 101,
      submittedAt: '2026-10-07T12:00:00Z', status: 'REJECTED', reason: 'Insufficient cash',
    });
    await Promise.resolve();
    flushAccount(42, 100);
    const receipt = await result;
    expect('head' in receipt && receipt.head === 'Order rejected').toBe(true);
    expect(service.cash()).toBe(100);
  });
});
