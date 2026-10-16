import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AccountSelectionService } from '../core/account-selection.service';
import { TradingService } from '../core/trading.service';
import { PortfolioComponent } from './portfolio.component';
import { PortfolioSummary } from './portfolio-summary.component';

describe('Portfolio page', () => {
  const selected = signal<{ accountId: number } | null>({ accountId: 1 });
  const summary: PortfolioSummary = {
    accountId: 1, currency: 'USD', cashBalance: 700,
    estimatedPortfolioValue: 1000, buyingPower: 700, investedAssetsValue: 300,
    lifetimeProfitLoss: 100, buyingPowerUnavailableReason: null,
    profitLossUnavailableReason: null, accountAsOf: '2026-10-15T18:00:00Z',
    oldestQuoteAt: '2026-10-15T17:50:00Z', valuationWarnings: [],
    holdings: [{ instrumentId: 1, symbol: 'AAPL', name: 'Apple', quantity: 2,
      reservedQuantity: 1, availableQuantity: 1, quotePrice: 150,
      estimatedValue: 300, quoteAsOf: '2026-10-15T17:50:00Z' }],
  };
  const trading = {
    refreshRevision: signal(0), loading: signal(false), error: signal(''),
    cash: signal(700), pendingOrderCount: signal(0),
    positions: signal([{ instrumentId: 1, sym: 'AAPL', name: 'Apple', qty: 2,
      reserved: 1, available: 1 }]),
    money: (value: number) => `$${value.toFixed(2)}`,
    refresh: () => undefined,
  };

  beforeEach(() => {
    selected.set({ accountId: 1 });
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(),
      provideRouter([]),
      { provide: AccountSelectionService, useValue: { selectedAccount: selected } },
      { provide: TradingService, useValue: trading },
    ] });
  });

  it('shows the shared summary and per-holding value without hiding reserved shares', () => {
    const fixture = TestBed.createComponent(PortfolioComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('/api/accounts/1/portfolio-summary').flush(summary);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Estimated portfolio value');
    expect(text).toContain('Portfolio allocation');
    expect(text).toContain('70.0%');
    expect(text).toContain('30.0%');
    expect(text).toContain('AAPL');
    expect(text).toContain('Order history');
    expect(fixture.nativeElement.querySelector('.portfolio-holdings tbody tr').textContent)
      .toContain('$300.00');
    expect(fixture.nativeElement.querySelector('.portfolio-holdings tbody tr').textContent)
      .toContain('1');
    http.verify();
  });
});
