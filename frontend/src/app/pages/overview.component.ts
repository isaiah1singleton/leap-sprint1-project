import { Component, computed, effect, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, Router } from '@angular/router';
import { TradingService } from '../core/trading.service';
import { AuthService } from '../core/auth.service';
import { AccountSelectionService } from '../core/account-selection.service';
import { AccountsCacheService } from '../core/accounts-cache.service';
import { MarketExplorerComponent } from '../market/market-explorer.component';
import { PortfolioSummaryComponent } from './portfolio-summary.component';

@Component({
  selector: 'app-overview',
  imports: [RouterLink, CommonModule, MarketExplorerComponent, PortfolioSummaryComponent],
  templateUrl: './overview.component.html',
})
export class OverviewComponent implements OnInit {
  readonly auth = inject(AuthService);
  readonly trading = inject(TradingService);
  readonly selectedAccount = inject(AccountSelectionService).selectedAccount;
  private readonly router = inject(Router);
  private readonly accountSelection = inject(AccountSelectionService);
  readonly accountsCache = inject(AccountsCacheService);

  readonly accounts = this.accountsCache.accounts;
  readonly activeAccounts = this.accountsCache.activeAccounts;
  readonly isLoadingAccounts = this.accountsCache.isLoading;
  readonly accountsError = this.accountsCache.error;
  
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

  ngOnInit(): void {
    void this.loadAccounts();
  }

  async loadAccounts(): Promise<void> {
    await this.accountsCache.loadAccounts();
  }

  selectAccountById(value: string): void {
    const account = this.activeAccounts().find(item => item.accountId === Number(value));
    if (account) this.accountSelection.selectAccount(account);
  }

  createAccount(): void {
    this.router.navigate(['/app/account']);
  }
}
