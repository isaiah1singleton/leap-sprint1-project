import { Component, computed, inject } from '@angular/core';
import { TradingService } from '../core/trading.service';

@Component({
  selector: 'app-portfolio',
  template: `
    <div class="stack">
      <div class="row">
        <div class="card" style="flex:1"><p class="stat-label">Total value</p><p class="stat-value">{{ trading.money(trading.totalValue()) }}</p></div>
        <div class="card" style="flex:1"><p class="stat-label">Invested</p><p class="stat-value">{{ trading.money(trading.invested()) }}</p></div>
        <div class="card" style="flex:1"><p class="stat-label">Cash balance</p><p class="stat-value">{{ trading.money(trading.cash()) }}</p></div>
      </div>

      <div class="card">
        <h2 class="card-title">Positions</h2>
        <table class="table">
          <thead><tr><th>Instrument</th><th class="num">Qty</th><th class="num">Avg cost</th><th class="num">Last</th><th class="num">Value</th><th class="num">P&amp;L</th></tr></thead>
          <tbody>
            @for (row of rows(); track row.sym) {
              <tr>
                <td><span class="sym">{{ row.sym }}</span> <span class="dim">{{ row.name }}</span></td>
                <td class="num">{{ row.qty }}</td><td class="num">{{ row.avg }}</td><td class="num">{{ row.last }}</td><td class="num">{{ row.value }}</td>
                <td class="num" [class.gain]="row.positive" [class.loss]="!row.positive">{{ row.pnl }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </div>
  `,
})
export class PortfolioComponent {
  readonly trading = inject(TradingService);
  readonly rows = computed(() => this.trading.positions().map((position) => {
    const value = position.qty * position.last;
    const pnl = value - position.qty * position.avg;
    return {
      ...position,
      qty: position.qty.toLocaleString('en-US'),
      avg: this.trading.money(position.avg),
      last: this.trading.money(position.last),
      value: this.trading.money(value),
      pnl: (pnl >= 0 ? '+' : '−') + this.trading.money(Math.abs(pnl)),
      positive: pnl >= 0,
    };
  }));
}
