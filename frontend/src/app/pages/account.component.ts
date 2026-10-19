import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { AccountService, AccountResponse } from '../core/account.service';
import { AccountSelectionService } from '../core/account-selection.service';
import { AccountsCacheService } from '../core/accounts-cache.service';
import { Receipt, TradingService, TransferMode } from '../core/trading.service';

@Component({
  selector: 'app-account',
  templateUrl: './account.component.html',
  styleUrl: './account.component.css',
  imports: [CommonModule, FormsModule, RouterLink],
})
export class AccountComponent implements OnInit {
  readonly auth = inject(AuthService);
  readonly trading = inject(TradingService);
  readonly selectedAccount = inject(AccountSelectionService).selectedAccount;
  private readonly accountService = inject(AccountService);
  private readonly accountSelection = inject(AccountSelectionService);
  private readonly route = inject(ActivatedRoute);
  private readonly accountsCache = inject(AccountsCacheService);

  accounts: AccountResponse[] = [];
  newAccountName = '';
  isCreating = false;
  error = '';
  success = '';
  private errorFields: string[] = [];
  transferMode: TransferMode = 'deposit';
  transferAmount = '';
  transferError = '';
  transferReceipt: Receipt | null = null;
  transferSubmitting = false;

  constructor() {
    if (this.route.snapshot.queryParamMap.get('mode') === 'withdraw') {
      this.transferMode = 'withdraw';
    }
  }

  ngOnInit(): void {
    this.loadAccounts();
  }

  async loadAccounts(): Promise<void> {
    this.accounts = await this.accountsCache.loadAccounts();
  }

  invalid(field: string): boolean {
    return this.errorFields.includes(field);
  }

  selectAccount(account: AccountResponse, mode?: TransferMode): void {
    this.accountSelection.selectAccount(account);
    this.transferMode = mode ?? this.transferMode;
    this.clearTransfer();
  }

  setTransferMode(mode: TransferMode): void {
    this.transferMode = mode;
    this.clearTransfer();
  }

  async submitTransfer(): Promise<void> {
    const account = this.selectedAccount();
    if (!account || account.accountStatus !== 'ACTIVE' || this.transferSubmitting) return;
    this.transferSubmitting = true;
    this.transferError = '';
    try {
      const result = await this.trading.transfer(this.transferMode, this.transferAmount);
      if (this.selectedAccount()?.accountId !== account.accountId) return;
      if ('message' in result) {
        this.transferError = result.message;
        return;
      }
      this.transferReceipt = result;
      this.transferAmount = '';
    } finally {
      this.transferSubmitting = false;
    }
  }

  clearTransfer(): void {
    this.transferAmount = '';
    this.transferError = '';
    this.transferReceipt = null;
  }

  async submit(): Promise<void> {
    this.error = '';
    this.success = '';
    this.errorFields = [];
    this.isCreating = true;

    const result = await this.accountService.createAccount(this.newAccountName);

    if ('fields' in result) {
      // It's an AccountFailure
      this.error = result.message;
      this.errorFields = result.fields;
      this.isCreating = false;
      return;
    }

    // Success - result is AccountResponse
    this.success = `Account "${result.accountName}" created successfully!`;
    this.newAccountName = '';
    this.isCreating = false;
    
    // Add to cache immediately
    this.accountsCache.addAccount(result);
    
    // Also reload from backend to stay in sync
    await this.loadAccounts();

    // Show the new account's details and cash controls.
    this.accountSelection.selectAccount(result);
    this.clearTransfer();
  }
}
