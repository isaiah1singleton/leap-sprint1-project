import { Component, computed, inject } from '@angular/core';
import { TradingService } from '../core/trading.service';
import { MarketExplorerComponent } from '../market/market-explorer.component';

@Component({
  selector: 'app-transact',
  imports: [MarketExplorerComponent],
  templateUrl: './transact.component.html',
})
export class TransactComponent {
  readonly trading = inject(TradingService);

  readonly holdings = computed(() =>
    this.trading.positions().map((position) => ({
      sym: position.sym,
      qty: position.qty.toLocaleString('en-US'),
      available: position.available.toLocaleString('en-US'),
    })),
  );
}

