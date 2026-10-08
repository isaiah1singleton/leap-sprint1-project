import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AccountResponse } from './account.service';
import { AccountSelectionService } from './account-selection.service';
import { AuthService } from './auth.service';
import { MarketQuote } from './market.service';
import { TradingService } from './trading.service';

describe('Quoted trading simulator', () => {
  let service: TradingService;
  const selected = signal<AccountResponse | null>(null);
  const account: AccountResponse = { accountId: 42, accountName: 'Test account', accountStatus: 'ACTIVE', openedAt: '2026-10-07T00:00:00Z' };
  const quote: MarketQuote = { symbol: 'AAPL', price: 100, bid: 99, ask: 101, currency: 'USD',
    change: 1, changePercent: 1, previousClose: 99, asOf: '2026-10-07T12:00:00Z', marketState: 'open', spreadBps: 200 };

  beforeEach(() => {
    sessionStorage.clear();
    selected.set(account);
    TestBed.configureTestingModule({ providers: [
      { provide: AuthService, useValue: { currentUserEmail: signal('alice@example.com') } },
      { provide: AccountSelectionService, useValue: { selectedAccount: selected } },
    ] });
    service = TestBed.inject(TradingService);
    TestBed.tick();
    service.cash.set(202);
    service.positions.set([]);
  });

  it('buys at ask, sells at bid, and prevents spending or selling too much', () => {
    expect(service.trade('buy', 'AAPL', '3', quote)).toHaveProperty('message');
    expect(service.trade('buy', 'AAPL', '2', quote, 'Apple')).toHaveProperty('head');
    expect(service.cash()).toBe(0);
    expect(service.positions()[0].qty).toBe(2);
    expect(service.positions()[0].avg).toBe(101);
    expect(service.trade('sell', 'AAPL', '3', quote)).toHaveProperty('message');
    expect(service.trade('sell', 'AAPL', '1', quote)).toHaveProperty('head');
    expect(service.cash()).toBe(99);
    expect(service.positions()[0].qty).toBe(1);
  });

  it('rejects malformed quantities, mismatched quotes, and absent accounts', () => {
    for (const quantity of ['Infinity', 'NaN', '-1', '1abc', '']) {
      expect(service.trade('buy', 'AAPL', quantity, quote)).toHaveProperty('message');
    }
    expect(service.trade('buy', 'MSFT', '1', quote)).toHaveProperty('message');
    selected.set(null);
    TestBed.tick();
    expect(service.trade('buy', 'AAPL', '1', quote)).toHaveProperty('message');
  });

  it('keeps simulation state separate across accounts and restores the original account', () => {
    service.trade('buy', 'AAPL', '1', quote);
    expect(service.cash()).toBe(101);
    selected.set({ ...account, accountId: 43 });
    TestBed.tick();
    expect(service.cash()).toBe(48250);
    selected.set(account);
    TestBed.tick();
    expect(service.cash()).toBe(101);
    expect(service.positions()[0].qty).toBe(1);
  });
});
