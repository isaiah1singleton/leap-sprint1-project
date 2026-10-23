import { Component, computed, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TradingService } from '../core/trading.service';
import { AuthService } from '../core/auth.service';
import { AccountSelectionService } from '../core/account-selection.service';
import { MarketExplorerComponent } from '../market/market-explorer.component';
import { PortfolioSummaryComponent } from './portfolio-summary.component';
import { CashTransferComponent } from './cash-transfer.component';

@Component({
  selector: 'app-overview',
  imports: [RouterLink, CommonModule, MarketExplorerComponent, PortfolioSummaryComponent, CashTransferComponent],
  templateUrl: './overview.component.html',
})
export class OverviewComponent {
  readonly auth = inject(AuthService);
  readonly trading = inject(TradingService);
  readonly selectedAccount = inject(AccountSelectionService).selectedAccount;

  readonly recent = computed(() => this.trading.orders().slice(0, 4));
  constructor() {
    effect(() => {
      for (const order of this.recent()) {
        if (order.status === 'FILLED' && !order.executionPrice) {
          this.trading.ensureFillExecutionPrice(order.orderId);
        }
      }
    });
  }

}
