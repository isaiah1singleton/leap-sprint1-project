import { TestBed } from '@angular/core/testing';
import { PortfolioChartComponent } from './portfolio-chart.component';
import { PortfolioHistoryPoint } from './portfolio-history.model';

describe('Portfolio charts', () => {
  function point(time: string, value: number | null, profit: number | null): PortfolioHistoryPoint {
    return { observedAt: time, cashBalance: 100, investedValue: value == null ? null : value - 100,
      portfolioValue: value, lifetimeProfitLoss: profit, oldestQuoteAt: time,
      valueUnavailableReason: value == null ? 'Price unavailable' : null,
      profitLossUnavailableReason: profit == null ? 'History incomplete' : null };
  }
  function create(points: PortfolioHistoryPoint[], metric = 'portfolioValue') {
    const fixture = TestBed.createComponent(PortfolioChartComponent);
    fixture.componentRef.setInput('title', 'Performance');
    fixture.componentRef.setInput('metric', metric);
    fixture.componentRef.setInput('points', points);
    fixture.detectChanges();
    return fixture;
  }

  it('shows a single real observation without inventing a trend', () => {
    const fixture = create([point('2026-10-20T12:00:00Z', 200, 0)]);
    expect(fixture.nativeElement.textContent).toContain('First observation saved');
    expect(fixture.nativeElement.querySelectorAll('.point').length).toBe(1);
    expect(fixture.componentInstance.paths()[0].single).toBe(true);
  });

  it('breaks the line across unavailable values and preserves zero and negative profit', () => {
    const fixture = create([
      point('2026-10-20T12:00:00Z', 100, -20),
      point('2026-10-20T12:05:00Z', null, null),
      point('2026-10-20T12:10:00Z', 120, 0),
    ], 'lifetimeProfitLoss');
    expect(fixture.componentInstance.paths()).toHaveLength(2);
    expect(fixture.nativeElement.textContent).toContain('$0.00');
    expect(fixture.componentInstance.domain().low).toBeLessThan(-20);
    expect(fixture.nativeElement.querySelector('.zero-line')).not.toBeNull();
  });

  it('supports keyboard inspection with account and quote details', () => {
    const fixture = create([
      point('2026-10-20T12:00:00Z', 200, -20),
      point('2026-10-20T12:05:00Z', 220, 0),
    ]);
    const chart = fixture.nativeElement.querySelector('.chart-interaction');
    chart.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true }));
    fixture.detectChanges();
    expect(fixture.componentInstance.selected()).toBe(0);
    expect(fixture.nativeElement.querySelector('.chart-tooltip').textContent).toContain('Cash');
    expect(fixture.nativeElement.querySelector('.chart-tooltip').textContent).toContain('Oldest quote');
    chart.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges();
    expect(fixture.componentInstance.selected()).toBe(1);
    chart.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.chart-tooltip')).toBeNull();
  });

  it('explains unavailable history without rendering a zero line', () => {
    const fixture = create([point('2026-10-20T12:00:00Z', null, null)]);
    expect(fixture.nativeElement.textContent).toContain('Price unavailable');
    expect(fixture.nativeElement.querySelector('svg')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Unavailable');
  });
});
