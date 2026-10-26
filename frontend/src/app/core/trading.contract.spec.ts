import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountResponse } from './account.service';
import { AccountSelectionService } from './account-selection.service';
import { TradingService } from './trading.service';

describe('TradingService HTTP and account isolation', () => {
  let trading: TradingService;
  let http: HttpTestingController;
  const account: AccountResponse = {
    accountId: 42,
    accountName: 'Trading',
    accountStatus: 'ACTIVE',
    openedAt: '2026-10-01T00:00:00Z',
  };
  const selected = signal<AccountResponse | null>(null);

  beforeEach(() => {
    selected.set(account);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AccountSelectionService, useValue: { selectedAccount: selected } },
      ],
    });
    trading = TestBed.inject(TradingService);
    http = TestBed.inject(HttpTestingController);
    TestBed.tick();
  });
  afterEach(() => http.verify());

  function flushAccount(id: number, cash = 100, holdings: object[] = [], orders: object[] = []) {
    http.expectOne(`/api/accounts/${id}/balance`).flush({ totalBalance: cash, currency: 'USD' });
    http.expectOne(`/api/accounts/${id}/holdings`).flush(holdings);
    http
      .expectOne((req) => req.url === '/api/orders' && req.params.get('accountId') === String(id))
      .flush(orders);
    http.expectOne(`/api/accounts/${id}/cash-movements`).flush([]);
  }

  it('publishes no partial snapshot if one of the account requests fails', async () => {
    http.expectOne('/api/accounts/42/balance').flush({ totalBalance: 1000, currency: 'USD' });
    http
      .expectOne('/api/accounts/42/holdings')
      .flush([], { status: 503, statusText: 'Unavailable' });
    http.expectOne((req) => req.url === '/api/orders').flush([]);
    http.expectOne('/api/accounts/42/cash-movements').flush([]);
    await vi.waitFor(() => expect(trading.loading()).toBe(false));
    expect(trading.cash()).toBe(0);
    expect(trading.positions()).toEqual([]);
    expect(trading.orders()).toEqual([]);
    expect(trading.error()).not.toBe('');
  });

  it('ignores a late response from a previously selected account', async () => {
    const old = http.match(() => true);
    expect(old).toHaveLength(4);
    selected.set({ ...account, accountId: 43 });
    TestBed.tick();
    flushAccount(43, 200);
    await vi.waitFor(() => expect(trading.cash()).toBe(200));
    for (const req of old)
      req.flush(req.request.url.endsWith('/balance') ? { totalBalance: 9999 } : []);
    await Promise.resolve();
    expect(trading.cash()).toBe(200);
    expect(trading.error()).toBe('');
  });

  it('clears account data and cached fills when selection is removed', async () => {
    flushAccount(42);
    await vi.waitFor(() => expect(trading.cash()).toBe(100));
    selected.set(null);
    TestBed.tick();
    expect(trading.cash()).toBe(0);
    expect(trading.positions()).toEqual([]);
    expect(trading.orders()).toEqual([]);
    expect(trading.fillExecutionPrices()).toEqual({});
    expect(trading.loading()).toBe(false);
    http.expectNone(() => true);
  });

  it.each(['', '0', '-1', 'NaN', 'Infinity', '1abc'])(
    'rejects invalid trade quantity %j without an HTTP write',
    async (qty) => {
      flushAccount(42);
      expect(await trading.trade('buy', 7, qty, 10, '2026-10-20T12:00:00Z', 'USD')).toMatchObject({
        fields: ['qty'],
      });
      http.expectNone((req) => req.method === 'POST');
    },
  );

  it('sends the reviewed quote and reloads authoritative state after submission', async () => {
    flushAccount(42);
    const result = trading.trade('buy', 7, '2', 101.25, '2026-10-20T12:00:00Z', 'USD');
    const request = http.expectOne('/api/orders');
    expect(request.request.body).toEqual({
      accountId: 42,
      instrumentId: 7,
      side: 'BUY',
      quantity: 2,
      indicativePrice: 101.25,
      quoteAsOf: '2026-10-20T12:00:00Z',
      quoteCurrency: 'USD',
    });
    request.flush({
      accountId: 42,
      orderId: 5,
      side: 'BUY',
      symbol: 'AAPL',
      quantity: 2,
      indicativePrice: 101.25,
      status: 'REJECTED',
      reason: 'Insufficient cash',
    });
    await Promise.resolve();
    flushAccount(42);
    expect(await result).toMatchObject({ head: 'Order rejected' });
    expect(trading.cash()).toBe(100);
  });

  it('keeps state unchanged on a failed submission and exposes the backend reason', async () => {
    flushAccount(42);
    await vi.waitFor(() => expect(trading.cash()).toBe(100));
    const result = trading.trade('sell', 7, '2', 10, '2026-10-20T12:00:00Z', 'USD');
    http
      .expectOne('/api/orders')
      .flush({ detail: 'Resolve accepted order first' }, { status: 409, statusText: 'Conflict' });
    expect(await result).toHaveProperty('message', 'Resolve accepted order first');
    expect(trading.cash()).toBe(100);
    expect(trading.orders()).toEqual([]);
  });

  it('deduplicates fill requests and clears the cached price on account change', async () => {
    flushAccount(42);
    trading.ensureFillExecutionPrice(17);
    trading.ensureFillExecutionPrice(17);
    http
      .expectOne('/api/orders/17/fill')
      .flush({ executionPrice: { amount: 102.25, currency: 'USD' } });
    await vi.waitFor(() => expect(trading.fillExecutionPrices()[17]).toBe('$102.25'));
    selected.set({ ...account, accountId: 43 });
    TestBed.tick();
    expect(trading.fillExecutionPrices()).toEqual({});
    flushAccount(43, 0);
  });
});
