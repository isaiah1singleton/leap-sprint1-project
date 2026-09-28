import { Component, computed, inject } from '@angular/core';
import { TradingService } from '../core/trading.service';

@Component({
  selector: 'app-portfolio',
  templateUrl: './portfolio.component.html',
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
