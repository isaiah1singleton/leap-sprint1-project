import { Component, inject } from '@angular/core';
import { TradingService } from '../core/trading.service';

@Component({
  selector: 'app-orders',
  template: `
    <div class="card">
      <div style="display:flex;justify-content:space-between;align-items:baseline">
        <h2 class="card-title">All orders and transfers</h2><span class="stat-label">Last 30 days</span>
      </div>
      <table class="table">
        <thead><tr><th>Reference</th><th>Date</th><th>Type</th><th>Symbol</th><th class="num">Qty</th><th class="num">Price</th><th class="num">Status</th></tr></thead>
        <tbody>
          @for (order of trading.orders(); track order.id) {
            <tr><td class="sym">{{ order.id }}</td><td class="dim">{{ order.date }}</td><td>{{ order.type }}</td><td>{{ order.sym }}</td><td class="num">{{ order.qty }}</td><td class="num">{{ order.price }}</td><td class="num dim">{{ order.status }}</td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class OrdersComponent {
  readonly trading = inject(TradingService);
}
