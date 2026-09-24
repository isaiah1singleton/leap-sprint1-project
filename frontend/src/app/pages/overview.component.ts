import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TradingService } from '../core/trading.service';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-overview',
  imports: [RouterLink],
  template: `
    <div class="stack">
      <div>
        <h2 style="margin:0">Welcome back, {{ auth.currentUser() }}</h2>
        <p class="sub" style="margin-top:6px">Here is your account overview.</p>
      </div>
      <div class="row">
        <div class="card" style="flex:1">
          <p class="stat-label">Total account value</p>
          <p class="stat-value">{{ trading.money(trading.totalValue()) }}</p>
          <p class="stat-note gain">{{ dayChange }} today</p>
        </div>
        <div class="card" style="flex:1">
          <p class="stat-label">Cash available</p>
          <p class="stat-value">{{ trading.money(trading.cash()) }}</p>
          <p class="stat-note">Settled and withdrawable</p>
        </div>
        <div class="card" style="flex:1">
          <p class="stat-label">Open positions</p>
          <p class="stat-value">{{ trading.positions().length }}</p>
          <p class="stat-note">{{ trading.pendingOrderCount() }} order pending</p>
        </div>
      </div>

      <div class="row">
        <div class="card" style="flex:1.5">
          <div style="display:flex;justify-content:space-between;align-items:baseline">
            <h2 class="card-title">Recent activity</h2>
            <a routerLink="/app/orders" style="font-size:12px;color:var(--ink)">View all</a>
          </div>
          @for (order of recent(); track order.id) {
            <div class="list-row">
              <div><strong>{{ order.type }} {{ order.sym }}</strong><div class="sub">{{ order.date }}</div></div>
              <div style="text-align:right">{{ order.price }}<div class="sub">{{ order.status }}</div></div>
            </div>
          }
        </div>

        <div class="card" style="flex:1;display:flex;flex-direction:column">
          <h2 class="card-title">Quick actions</h2>
          <button class="btn btn-primary btn-sm" routerLink="/app/transact">Place an order</button>
          <button class="btn btn-secondary btn-sm" routerLink="/app/transact" [queryParams]="{ mode: 'deposit' }">Deposit funds</button>
          <button class="btn btn-secondary btn-sm" routerLink="/app/portfolio">Review portfolio</button>
          <p class="note" style="margin-top:auto">Market data delayed 15 minutes on this account tier.</p>
        </div>
      </div>
    </div>
  `,
})
export class OverviewComponent {
  readonly auth = inject(AuthService);
  readonly trading = inject(TradingService);
  readonly dayChange = '+$1,284.22 (+0.41%)';
  readonly recent = computed(() => this.trading.orders().slice(0, 4));
}
