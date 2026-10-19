import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountSelectionService } from '../core/account-selection.service';
import { TradingService } from '../core/trading.service';
import { PortfolioSummary, PortfolioSummaryComponent } from './portfolio-summary.component';

describe('Portfolio summary', () => {
  const selected = signal<{ accountId: number } | null>(null);
  const revision = signal(0);
  const summary: PortfolioSummary = {
    accountId: 1, currency: 'USD', cashBalance: 800, estimatedPortfolioValue: 1100, buyingPower: 800,
    investedAssetsValue: 300, lifetimeProfitLoss: 100, buyingPowerUnavailableReason: null,
    profitLossUnavailableReason: null, accountAsOf: '2026-10-15T18:00:00Z',
    oldestQuoteAt: '2026-10-15T17:50:00Z', valuationWarnings: [],
    holdings: [{ instrumentId: 1, symbol: 'AAPL', name: 'Apple', quantity: 2,
      reservedQuantity: 0, availableQuantity: 2, quotePrice: 150,
      estimatedValue: 300, quoteAsOf: '2026-10-15T17:50:00Z' }],
  };
  let http: HttpTestingController;

  beforeEach(() => {
    selected.set({ accountId: 1 }); revision.set(0);
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(),
      { provide: AccountSelectionService, useValue: { selectedAccount: selected } },
      { provide: TradingService, useValue: { refreshRevision: revision } },
    ] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  function create() {
    const fixture = TestBed.createComponent(PortfolioSummaryComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('shows all four values and the quote time', () => {
    const fixture = create();
    expect(fixture.nativeElement.textContent).not.toContain('$0.00');
    http.expectOne('/api/accounts/1/portfolio-summary').flush(summary);
    fixture.detectChanges();
    for (const amount of ['$1,100.00', '$800.00', '$300.00', '+$100.00', '17:50:00']) {
      expect(fixture.nativeElement.textContent).toContain(amount);
    }
  });

  it('explains unavailable amounts without displaying invented zeros', () => {
    const fixture = create();
    http.expectOne('/api/accounts/1/portfolio-summary').flush({ ...summary,
      estimatedPortfolioValue: null, investedAssetsValue: null, lifetimeProfitLoss: null,
      buyingPower: null, buyingPowerUnavailableReason: 'Pending order',
      profitLossUnavailableReason: 'History incomplete', valuationWarnings: ['Price unavailable for AAPL'],
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Pending order');
    expect(fixture.nativeElement.textContent).toContain('History incomplete');
    expect(fixture.nativeElement.textContent).toContain('Price unavailable for AAPL');
    expect(fixture.nativeElement.textContent).not.toContain('$0.00');
  });

  it('hides the delayed quote notice while retaining actionable valuation warnings', () => {
    const fixture = create();
    http.expectOne('/api/accounts/1/portfolio-summary').flush({ ...summary,
      valuationWarnings: ['Valuation includes delayed provider quotes.', 'Price unavailable for AAPL'],
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Valuation includes delayed provider quotes.');
    expect(fixture.nativeElement.textContent).toContain('Price unavailable for AAPL');
  });

  it('clears previous figures and cancels obsolete requests on account switch', () => {
    const fixture = create();
    const old = http.expectOne('/api/accounts/1/portfolio-summary');
    selected.set({ accountId: 2 }); fixture.detectChanges();
    expect(old.cancelled).toBe(true);
    http.expectOne('/api/accounts/2/portfolio-summary').flush({ ...summary, accountId: 2, lifetimeProfitLoss: -50 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('-$50.00');
    selected.set(null); fixture.detectChanges();
    expect(fixture.componentInstance.summary()).toBeNull();
  });

  it('refreshes after account activity and retries after a network failure', () => {
    const fixture = create();
    http.expectOne('/api/accounts/1/portfolio-summary').flush(summary);
    revision.update(value => value + 1); fixture.detectChanges();
    http.expectOne('/api/accounts/1/portfolio-summary').error(new ProgressEvent('error'));
    fixture.detectChanges();
    expect(fixture.componentInstance.error()).toContain('could not be loaded');
    expect(fixture.componentInstance.summary()).toBeNull();
    fixture.componentInstance.refresh(); fixture.detectChanges();
    http.expectOne('/api/accounts/1/portfolio-summary').flush(summary);
    fixture.detectChanges();
    expect(fixture.componentInstance.error()).toBe('');
  });
});
