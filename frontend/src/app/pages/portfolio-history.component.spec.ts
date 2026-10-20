import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { vi } from 'vitest';
import { AccountSelectionService } from '../core/account-selection.service';
import { TradingService } from '../core/trading.service';
import { PortfolioHistoryComponent } from './portfolio-history.component';

describe('Recorded portfolio history', () => {
  const selected = signal<{ accountId: number } | null>({ accountId: 1 });
  const revision = signal(0);
  let http: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(false);
    selected.set({ accountId: 1 }); revision.set(0);
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(),
      { provide: AccountSelectionService, useValue: { selectedAccount: selected } },
      { provide: TradingService, useValue: { refreshRevision: revision } },
    ] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { http.verify(); vi.useRealTimers(); vi.restoreAllMocks(); });

  function request(id = 1, range = 'ALL') {
    return http.expectOne(req => req.url === `/api/accounts/${id}/portfolio-history` && req.params.get('range') === range);
  }

  it('loads both graphs and polls for actual new observations', () => {
    const fixture = TestBed.createComponent(PortfolioHistoryComponent);
    fixture.detectChanges(); vi.advanceTimersByTime(0);
    request().flush({ accountId: 1, currency: 'USD', recordingStartedAt: null, points: [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('app-portfolio-chart')).toHaveLength(2);
    vi.advanceTimersByTime(60000);
    request().flush({ accountId: 1, currency: 'USD', points: [] });
    fixture.destroy();
  });

  it('cancels obsolete account requests and clears old figures when changing periods', () => {
    const fixture = TestBed.createComponent(PortfolioHistoryComponent);
    fixture.detectChanges(); vi.advanceTimersByTime(0);
    const old = request();
    selected.set({ accountId: 2 }); fixture.detectChanges(); vi.advanceTimersByTime(0);
    expect(old.cancelled).toBe(true);
    request(2).flush({ accountId: 2, currency: 'USD', points: [] });
    fixture.componentInstance.range.set('1D'); fixture.detectChanges(); vi.advanceTimersByTime(0);
    expect(fixture.componentInstance.history()).toBeNull();
    request(2, '1D').flush({ accountId: 2, currency: 'USD', points: [] });
    fixture.destroy();
  });

  it('recovers after a request failure without stopping automatic refresh', () => {
    const fixture = TestBed.createComponent(PortfolioHistoryComponent);
    fixture.detectChanges(); vi.advanceTimersByTime(0);
    request().flush('Failure', { status: 500, statusText: 'Server error' });
    expect(fixture.componentInstance.error()).toContain('could not be loaded');
    vi.advanceTimersByTime(60000);
    request().flush({ accountId: 1, currency: 'USD', points: [] });
    expect(fixture.componentInstance.error()).toBe('');
    fixture.destroy();
  });
});
