import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, effect, inject, signal } from '@angular/core';
import { timer, filter, switchMap, catchError, of } from 'rxjs';
import { AccountSelectionService } from '../core/account-selection.service';
import { TradingService } from '../core/trading.service';
import { PortfolioChartComponent } from './portfolio-chart.component';
import { PortfolioHistory } from './portfolio-history.model';

@Component({
  selector: 'app-portfolio-history',
  imports: [PortfolioChartComponent, DatePipe],
  templateUrl: './portfolio-history.component.html',
  styleUrl: './portfolio-history.component.css',
})
export class PortfolioHistoryComponent {
  private readonly http = inject(HttpClient);
  private readonly account = inject(AccountSelectionService);
  private readonly trading = inject(TradingService);
  private readonly revision = signal(0);
  readonly range = signal('ALL');
  readonly ranges = ['1D', '1W', '1M', 'ALL'];
  readonly history = signal<PortfolioHistory | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');

  constructor() {
    effect(onCleanup => {
      const accountId = this.account.selectedAccount()?.accountId;
      const range = this.range();
      this.trading.refreshRevision();
      this.revision();
      this.history.set(null);
      this.error.set('');
      this.loading.set(accountId != null);
      if (accountId == null) return;
      const subscription = timer(0, 60000).pipe(
        filter(() => !document.hidden),
        switchMap(() => this.http.get<PortfolioHistory>(`/api/accounts/${accountId}/portfolio-history`, { params: { range } }).pipe(
          catchError((error: HttpErrorResponse) => {
            this.error.set(error.status === 401 ? 'Your session expired. Sign in again to view performance history.'
              : 'Performance history could not be loaded. Please try again.');
            return of(null);
          }),
        )),
      ).subscribe(history => {
        this.loading.set(false);
        if (history) { this.history.set(history); this.error.set(''); }
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  refresh(): void { this.revision.update(value => value + 1); }
}
