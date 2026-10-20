import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AccountSelectionService } from '../core/account-selection.service';
import { TradingService } from '../core/trading.service';
import { PortfolioSummary, PortfolioSummaryComponent } from './portfolio-summary.component';
import { CashTransferComponent } from './cash-transfer.component';
import { PortfolioHistoryComponent } from './portfolio-history.component';

@Component({
  selector: 'app-portfolio',
  imports: [RouterLink, PortfolioSummaryComponent, CashTransferComponent, PortfolioHistoryComponent],
  templateUrl: './portfolio.component.html',
})
export class PortfolioComponent {
  readonly trading = inject(TradingService);
  readonly selectedAccount = inject(AccountSelectionService).selectedAccount;
  readonly snapshot = signal<PortfolioSummary | null>(null);
  readonly activeAllocation = signal<{ label: string; amount: number } | null>(null);

  setSnapshot(summary: PortfolioSummary | null): void {
    this.snapshot.set(summary);
    this.activeAllocation.set(null);
  }

  showAllocation(label: string, amount: number): void {
    this.activeAllocation.set({ label, amount });
  }

  clearAllocation(): void {
    this.activeAllocation.set(null);
  }

  holdingColor(index: number): string {
    return `hsl(${(220 + index * 137.5) % 360} 58% 47%)`;
  }

  allocationLabel(label: string, amount: number): string {
    return `${label}: ${this.money(amount)}, ${this.allocation(amount)} of portfolio`;
  }

  holdingValue(instrumentId: number) {
    return this.snapshot()?.holdings?.find(holding => holding.instrumentId === instrumentId);
  }

  allocation(value: number | null | undefined): string {
    const total = this.snapshot()?.estimatedPortfolioValue;
    if (value == null || total == null || total <= 0) return '—';
    return `${(value / total * 100).toFixed(1)}%`;
  }

  share(value: number | null | undefined): number {
    const total = this.snapshot()?.estimatedPortfolioValue;
    if (value == null || total == null || total <= 0) return 0;
    return Math.min(100, Math.max(0, value / total * 100));
  }

  money(value: number | null | undefined): string {
    return value == null ? '—' : this.trading.money(value);
  }

  quoteMoney(value: number | null | undefined): string {
    if (value == null) return '—';
    return new Intl.NumberFormat('en-US', {
      style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: 8,
    }).format(value);
  }
}
