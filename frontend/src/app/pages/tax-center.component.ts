import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TaxDocument, TaxReport, TaxService } from '../core/tax.service';

@Component({
  selector: 'app-tax-center',
  imports: [FormsModule],
  templateUrl: './tax-center.component.html',
  styles: [`
    .tax-controls, .tax-downloads, .tax-summary { display: flex; flex-wrap: wrap; gap: 16px; margin-bottom: 20px; }
    .tax-controls label { display: grid; gap: 6px; }
    .tax-controls input, .tax-controls select { padding: 9px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); color: inherit; }
    .tax-summary > div { flex: 1; min-width: 180px; }
    .tax-summary strong { display: block; font-size: 22px; margin-top: 8px; overflow-wrap: anywhere; }
    .tax-warning { padding: 12px; border-left: 3px solid var(--accent); }
    td small { display: block; margin-top: 4px; }
  `],
})
export class TaxCenterComponent {
  private readonly tax = inject(TaxService);
  private readonly destroyRef = inject(DestroyRef);
  private request = 0;
  readonly currentYear = new Date().getUTCFullYear();
  year = this.currentYear;
  document: TaxDocument = '1099-B';
  readonly report = signal<TaxReport | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly downloadError = signal('');
  readonly downloading = signal<TaxDocument | null>(null);

  constructor() { void this.load(); }

  async load(): Promise<void> {
    const request = ++this.request;
    this.report.set(null);
    this.error.set('');
    if (!Number.isInteger(this.year) || this.year < 1900 || this.year > this.currentYear) {
      this.error.set(`Choose a year from 1900 through ${this.currentYear}.`);
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    try {
      const report = await this.tax.report(this.year, this.document);
      if (request === this.request && !this.destroyRef.destroyed) this.report.set(report);
    } catch {
      if (request === this.request) this.error.set('Could not load your tax report. Please retry.');
    } finally {
      if (request === this.request) this.loading.set(false);
    }
  }

  async download(document: TaxDocument): Promise<void> {
    if (this.downloading() || !this.report()) return;
    const year = this.report()!.year;
    this.downloading.set(document);
    this.downloadError.set('');
    try {
      const blob = await this.tax.download(year, document);
      if (this.destroyRef.destroyed) return;
      const url = URL.createObjectURL(blob);
      const anchor = window.document.createElement('a');
      anchor.href = url;
      anchor.download = `${year}-Simulated-${document}.pdf`;
      window.document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      // Allow the browser to start reading the blob before releasing it.
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch {
      this.downloadError.set('Download failed. Please try again.');
    } finally {
      this.downloading.set(null);
    }
  }

  // Keep decimal strings exact instead of coercing financial values through JS floating point.
  decimal(value: string | null): string {
    return value === null ? 'Unavailable' : value.replace(/(\.\d*?)0+$/, '$1').replace(/\.$/, '');
  }
}
