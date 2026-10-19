import { Component, computed, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AccountSelectionService } from '../core/account-selection.service';
import { TradingService } from '../core/trading.service';

@Component({
  selector: 'app-orders',
  imports: [RouterLink],
  templateUrl: './orders.component.html',
  styles: [`
    .table { min-width: 780px; }
    .history-pagination { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-top: 18px; }
    .history-pagination .sub { margin: 0; }
    .page-controls { display: flex; align-items: center; gap: 12px; }
    .page-controls span { font-size: 13px; color: var(--ink-soft); white-space: nowrap; }
  `],
})
export class OrdersComponent {
  readonly trading = inject(TradingService);
  private readonly selectedAccount = inject(AccountSelectionService).selectedAccount;
  readonly pageSize = 10;
  readonly page = signal(1);
  readonly pageCount = computed(() =>
    Math.max(1, Math.ceil(this.trading.orders().length / this.pageSize)));
  readonly currentPage = computed(() => Math.min(this.page(), this.pageCount()));
  readonly visibleOrders = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.trading.orders().slice(start, start + this.pageSize);
  });
  readonly firstItem = computed(() =>
    this.trading.orders().length ? (this.currentPage() - 1) * this.pageSize + 1 : 0);
  readonly lastItem = computed(() =>
    Math.min(this.currentPage() * this.pageSize, this.trading.orders().length));

  constructor() {
    effect(() => {
      this.selectedAccount();
      this.page.set(1);
    });
    effect(() => {
      for (const order of this.visibleOrders()) {
        if (order.status === 'FILLED' && !order.executionPrice) {
          this.trading.ensureFillExecutionPrice(order.orderId);
        }
      }
    });
  }

  previousPage(): void {
    this.page.set(Math.max(1, this.currentPage() - 1));
  }

  nextPage(): void {
    this.page.set(Math.min(this.pageCount(), this.currentPage() + 1));
  }
}
