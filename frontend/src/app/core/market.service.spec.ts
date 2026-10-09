import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { MarketService, MarketSymbol } from './market.service';

describe('MarketService watchlist', () => {
  const email = signal<string | null>('alice@example.com');
  let service: MarketService;
  let http: HttpTestingController;
  const stocks: MarketSymbol[] = Array.from({ length: 26 }, (_, i) => ({
    instrumentId: i + 1, symbol: `STOCK${i}`, name: `Company ${i}`, type: 'equity', exchange: 'US', currency: 'USD',
  }));

  beforeEach(() => {
    localStorage.clear();
    email.set('alice@example.com');
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(),
      { provide: AuthService, useValue: { currentUserEmail: email } }] });
    service = TestBed.inject(MarketService);
    http = TestBed.inject(HttpTestingController);
    TestBed.tick();
    service.catalogue.set(stocks);
  });

  afterEach(() => http.verify());

  it('enforces 25 unique equity pins and permits replacement after unpinning', () => {
    for (const item of stocks.slice(0, 25)) expect(service.pin(item)).toBeNull();
    expect(service.pin(stocks[0])).toBeNull();
    expect(service.pins().length).toBe(25);
    expect(service.pin(stocks[25])).toContain('25');
    service.unpin(stocks[0].symbol);
    expect(service.pin(stocks[25])).toBeNull();
    expect(service.pin({ ...stocks[0], type: 'crypto' })).toContain('Only stocks');
  });

  it('saves pins per login and does not expose another user watchlist', () => {
    service.pin(stocks[0]);
    email.set('bob@example.com');
    TestBed.tick();
    expect(service.pins()).toEqual([]);
    service.pin(stocks[1]);
    email.set('alice@example.com');
    TestBed.tick();
    expect(service.pins()).toEqual([stocks[0].symbol]);
  });

  it('sends a single batch request to the backend', async () => {
    const request = service.quotes(['AAPL', 'MSFT']);
    http.expectOne('/api/market/quotes?symbols=AAPL,MSFT').flush({ quotes: [], disclaimer: 'Educational data.' });
    expect((await request).quotes).toEqual([]);
  });

  it('encodes canonical crypto symbols for the backend detail route', async () => {
    const request = service.quote('X:BTC-USD');
    http.expectOne('/api/market/quotes/X%3ABTC-USD').flush({ symbol: 'X:BTC-USD', quote: null, error: null });
    expect((await request).symbol).toBe('X:BTC-USD');
  });
});

