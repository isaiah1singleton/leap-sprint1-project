import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { AccountService, AccountResponse, AccountFailure } from '../core/account.service';
import { AccountSelectionService } from '../core/account-selection.service';
import { AccountsCacheService } from '../core/accounts-cache.service';

@Component({
  selector: 'app-account',
  templateUrl: './account.component.html',
  imports: [CommonModule, FormsModule, RouterLink],
})
export class AccountComponent implements OnInit {
  readonly auth = inject(AuthService);
  readonly selectedAccount = inject(AccountSelectionService).selectedAccount;
  private readonly accountService = inject(AccountService);
  private readonly accountSelection = inject(AccountSelectionService);
  private readonly router = inject(Router);
  private readonly accountsCache = inject(AccountsCacheService);

  accounts: AccountResponse[] = [];
  newAccountName = '';
  isCreating = false;
  error = '';
  success = '';
  private errorFields: string[] = [];

  ngOnInit(): void {
    this.loadAccounts();
  }

  async loadAccounts(): Promise<void> {
    this.accounts = await this.accountsCache.loadAccounts();
  }

  invalid(field: string): boolean {
    return this.errorFields.includes(field);
  }

  async selectAccount(account: AccountResponse): Promise<void> {
    this.accountSelection.selectAccount(account);
    await this.router.navigate(['/app/overview']);
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

    // Select the newly created account and navigate to overview
    this.accountSelection.selectAccount(result);
    await this.router.navigate(['/app/overview']);
  }
}
