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

  it('splits allocation by ticker and reveals each holding value on hover and focus', () => {
    const fixture = TestBed.createComponent(PortfolioComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('/api/accounts/1/portfolio-summary').flush({
      ...summary,
      cashBalance: 500,
      buyingPower: 500,
      investedAssetsValue: 500,
      holdings: [summary.holdings[0], {
        instrumentId: 2, symbol: 'MSFT', name: 'Microsoft', quantity: 1,
        reservedQuantity: 0, availableQuantity: 1, quotePrice: 200,
        estimatedValue: 200, quoteAsOf: '2026-10-15T17:50:00Z',
      }],
    });
    fixture.detectChanges();

    const segments = fixture.nativeElement.querySelectorAll('.allocation-segment') as NodeListOf<HTMLButtonElement>;
    expect(segments.length).toBe(3);
    expect(Array.from(segments).map(segment => segment.style.width)).toEqual(['50%', '30%', '20%']);
    expect(segments[1].getAttribute('aria-label')).toBe('AAPL: $300.00, 30.0% of portfolio');
    expect(fixture.nativeElement.querySelector('.allocation-legend').textContent).toContain('MSFT');

    segments[1].dispatchEvent(new Event('mouseenter'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.allocation-detail').textContent).toContain('AAPL');
    expect(fixture.nativeElement.querySelector('.allocation-detail').textContent).toContain('$300.00 · 30.0%');

    segments[2].dispatchEvent(new Event('focus'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.allocation-detail').textContent).toContain('MSFT');
    expect(fixture.nativeElement.querySelector('.allocation-detail').textContent).toContain('$200.00 · 20.0%');
    http.verify();
  });
});
