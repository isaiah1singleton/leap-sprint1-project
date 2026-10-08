import { Component, computed, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, Router } from '@angular/router';
import { TradingService } from '../core/trading.service';
import { AuthService } from '../core/auth.service';
import { AccountSelectionService } from '../core/account-selection.service';
import { AccountsCacheService } from '../core/accounts-cache.service';
import { AccountResponse } from '../core/account.service';
import { MarketExplorerComponent } from '../market/market-explorer.component';

@Component({
  selector: 'app-overview',
  imports: [RouterLink, CommonModule, MarketExplorerComponent],
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
  
  readonly dayChange = '+$1,284.22 (+0.41%)';
  readonly recent = computed(() => this.trading.orders().slice(0, 4));

  ngOnInit(): void {
    const currentUser = this.auth.currentUser();
    console.log('=====================================================');
    console.log('📄 OverviewComponent initialized');
    console.log(`👤 Logged in user: ${currentUser}`);
    console.log(`📊 Selected account: ${this.selectedAccount()?.accountName || 'NONE'}`);
    console.log('=====================================================');
    this.loadAccounts();
  }

  async loadAccounts(): Promise<void> {
    const currentUser = this.auth.currentUser();
    console.log(`🔄 Loading accounts for user: ${currentUser}`);
    await this.accountsCache.loadAccounts();
    console.log(`✅ Accounts available for ${currentUser}:`, this.accounts());
    console.log(`🟢 Active accounts for ${currentUser}:`, this.activeAccounts());
    this.accounts().forEach((acc, idx) => {
      console.log(`   ${idx + 1}. ${acc.accountName} (ID: ${acc.accountId}, Status: ${acc.accountStatus})`);
    });
  }

  async selectAccount(account: AccountResponse): Promise<void> {
    const currentUser = this.auth.currentUser();
    console.log(`🎯 User ${currentUser} selecting account:`, account.accountName);
    this.accountSelection.selectAccount(account);
  }

  createAccount(): void {
    console.log(`➕ User ${this.auth.currentUser()} creating new account`);
    this.router.navigate(['/app/account']);
  }
}
