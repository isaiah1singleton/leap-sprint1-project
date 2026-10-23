import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, output, signal } from '@angular/core';
import { AccountSelectionService } from '../core/account-selection.service';
import { TradingService } from '../core/trading.service';

export interface PortfolioSummary {
  accountId: number;
  currency: string;
  cashBalance: number;
  estimatedPortfolioValue: number | null;
  buyingPower: number | null;
  investedAssetsValue: number | null;
  lifetimeProfitLoss: number | null;
  buyingPowerUnavailableReason: string | null;
  profitLossUnavailableReason: string | null;
  accountAsOf: string;
  oldestQuoteAt: string | null;
  valuationWarnings: string[];
  holdings: PortfolioHoldingValue[];
}

export interface PortfolioHoldingValue {
  instrumentId: number;
  symbol: string;
  name: string;
  quantity: number;
  reservedQuantity: number;
  availableQuantity: number;
  quotePrice: number | null;
  estimatedValue: number | null;
  quoteAsOf: string | null;
}

@Component({
  selector: 'app-portfolio-summary',
  imports: [CommonModule],
  template: `
    <section aria-label="Portfolio valuation" [attr.aria-busy]="loading()">
      <div class="section-heading valuation-heading">
        <div><h2 class="card-title">Portfolio snapshot</h2><p class="sub">USD · Estimated using the latest available prices</p></div>
        <button type="button" class="btn-chip" [disabled]="loading()" (click)="refresh()">{{ loading() ? 'Updating…' : 'Refresh values' }}</button>
      </div>
      @if (error()) { <p class="error-banner" role="alert">{{ error() }}</p> }
      <div class="valuation-grid">
        <div class="card stat-primary"><p class="stat-label">Estimated portfolio value</p><p class="stat-value">{{ money(summary()?.estimatedPortfolioValue) }}</p><p class="stat-note">Cash + invested assets</p></div>
        <div class="card"><p class="stat-label">Buying power</p><p class="stat-value" [class.metric-unavailable]="summary()?.buyingPowerUnavailableReason">{{ summary()?.buyingPowerUnavailableReason ? 'Unavailable' : money(summary()?.buyingPower) }}</p><p class="stat-note">{{ summary()?.buyingPowerUnavailableReason || 'Cash available for new orders' }}</p></div>
        <div class="card"><p class="stat-label">Invested assets value</p><p class="stat-value">{{ money(summary()?.investedAssetsValue) }}</p><p class="stat-note">Value of all held units, including reserved sells</p></div>
        <div class="card"><p class="stat-label">Lifetime profit/loss ($)</p><p class="stat-value" [class.gain]="(summary()?.lifetimeProfitLoss ?? 0) > 0" [class.loss]="(summary()?.lifetimeProfitLoss ?? 0) < 0">{{ money(summary()?.lifetimeProfitLoss, true) }}</p><p class="stat-note">{{ summary()?.profitLossUnavailableReason || 'Portfolio value − net deposits' }}</p></div>
      </div>
      @if (summary(); as data) {
        <div class="valuation-notes">
          <p class="note">Account snapshot {{ data.accountAsOf | date:'MMM d, y, HH:mm:ss':'UTC' }} UTC.
          </p>
          @for (warning of data.valuationWarnings; track warning) {
            @if (warning !== 'Valuation includes delayed provider quotes.') {
              <p class="valuation-warning" role="status">{{ warning }}</p>
            }
          }
        </div>
      }
    </section>
  `,
})
export class PortfolioSummaryComponent {
  private readonly http = inject(HttpClient);
  private readonly account = inject(AccountSelectionService);
  private readonly trading = inject(TradingService);
  private readonly manualRefresh = signal(0);
  readonly summary = signal<PortfolioSummary | null>(null);
  readonly summaryChange = output<PortfolioSummary | null>();
  readonly loading = signal(false);
  readonly error = signal('');

  constructor() {
    effect(onCleanup => {
      const accountId = this.account.selectedAccount()?.accountId;
      this.trading.refreshRevision();
      this.manualRefresh();
      this.summary.set(null);
      this.summaryChange.emit(null);
      this.error.set('');
      this.loading.set(accountId != null);
      if (accountId == null) return;
      const request = this.http.get<PortfolioSummary>(`/api/accounts/${accountId}/portfolio-summary`).subscribe({
        next: data => { this.summary.set(data); this.summaryChange.emit(data); this.loading.set(false); },
        error: (failure: HttpErrorResponse) => {
          this.error.set(failure.status === 401 ? 'Your session expired. Sign in again to view this account.'
            : 'Portfolio values could not be loaded. Refresh values to try again.');
          this.loading.set(false);
        },
      });
      onCleanup(() => request.unsubscribe());
    });
  }

  refresh(): void { this.manualRefresh.update(value => value + 1); }

  money(value: number | null | undefined, signed = false): string {
    if (value == null) return '—';
    return new Intl.NumberFormat('en-US', {
      style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: 2,
      signDisplay: signed ? 'exceptZero' : 'auto',
    }).format(value);
  }
}
