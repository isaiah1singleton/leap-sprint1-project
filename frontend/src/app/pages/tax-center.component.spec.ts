import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { TaxReport, TaxService } from '../core/tax.service';
import { TaxCenterComponent } from './tax-center.component';

const emptyReport: TaxReport = {
  year: 2026, document: '1099-B', clientReference: 'Client 1', currency: 'USD', sales: [],
  gains: '0.00000000', losses: '0.00000000', net: '0.00000000', unavailableBasisCount: 0,
};

describe('Tax Center', () => {
  it('shows an empty state and downloads the selected form and year', async () => {
    const tax = { report: vi.fn().mockResolvedValue(emptyReport), download: vi.fn().mockResolvedValue(new Blob(['pdf'])) };
    TestBed.configureTestingModule({ providers: [{ provide: TaxService, useValue: tax }] });
    const fixture = TestBed.createComponent(TaxCenterComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No completed sales for this year');
    expect(fixture.nativeElement.textContent).toContain('Download Simulated 1099-B');
    expect(fixture.nativeElement.textContent).toContain('Download Simulated 1099-DA');
    expect(fixture.nativeElement.textContent).toContain('SIMULATED / NOT FOR TAX FILING');
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    const create = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:tax');
    const revoke = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
    vi.useFakeTimers();
    await fixture.componentInstance.download('1099-DA');
    expect(tax.download).toHaveBeenCalledWith(2026, '1099-DA');
    expect(click).toHaveBeenCalledOnce();
    vi.runAllTimers();
    expect(revoke).toHaveBeenCalledWith('blob:tax');
    vi.useRealTimers();
    click.mockRestore(); create.mockRestore(); revoke.mockRestore();
  });

  it('does not show stale results after a year change and supports retry', async () => {
    let finishOld!: (report: TaxReport) => void;
    const tax = {
      report: vi.fn().mockImplementationOnce(() => new Promise<TaxReport>(resolve => { finishOld = resolve; }))
        .mockResolvedValueOnce({ ...emptyReport, year: 2025 })
        .mockRejectedValueOnce(new Error('offline'))
        .mockResolvedValueOnce(emptyReport),
    };
    TestBed.configureTestingModule({ providers: [{ provide: TaxService, useValue: tax }] });
    const fixture = TestBed.createComponent(TaxCenterComponent);
    fixture.componentInstance.year = 2025;
    await fixture.componentInstance.load();
    finishOld(emptyReport);
    await Promise.resolve();
    expect(fixture.componentInstance.report()?.year).toBe(2025);
    await fixture.componentInstance.load();
    expect(fixture.componentInstance.report()).toBeNull();
    expect(fixture.componentInstance.error()).toContain('Please retry');
    await fixture.componentInstance.load();
    expect(fixture.componentInstance.error()).toBe('');
  });

  it('retains decimal precision and explicitly marks unavailable basis', async () => {
    const tax = { report: vi.fn().mockResolvedValue({ ...emptyReport, unavailableBasisCount: 1 }) };
    TestBed.configureTestingModule({ providers: [{ provide: TaxService, useValue: tax }] });
    const fixture = TestBed.createComponent(TaxCenterComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Partial totals');
    expect(fixture.componentInstance.decimal('9999999999999999.12345678')).toBe('9999999999999999.12345678');
    expect(fixture.componentInstance.decimal('0.00000001')).toBe('0.00000001');
    expect(fixture.componentInstance.decimal(null)).toBe('Unavailable');
    fixture.componentInstance.year = 1800;
    await fixture.componentInstance.load();
    expect(tax.report).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.report()).toBeNull();
  });
});
