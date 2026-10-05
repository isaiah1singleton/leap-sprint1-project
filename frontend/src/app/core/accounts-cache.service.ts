import { Injectable, inject, signal } from '@angular/core';
import { AccountService, AccountResponse } from './account.service';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class AccountsCacheService {
  private readonly accountService = inject(AccountService);
  private readonly auth = inject(AuthService);
  
  readonly accounts = signal<AccountResponse[]>([]);
  readonly activeAccounts = signal<AccountResponse[]>([]);
  readonly isLoading = signal(false);
  readonly error = signal<string | null>(null);
  private cachedForUser: string | null = null;

  async loadAccounts(forceRefresh = false): Promise<AccountResponse[]> {
    const currentUser = this.auth.currentUser();
    
    // If user has changed, clear the cache
    if (currentUser !== this.cachedForUser) {
      console.log(`👤 User changed from ${this.cachedForUser} to ${currentUser} | Clearing cache`);
      this.clearCache();
      this.cachedForUser = currentUser;
    }
    
    // If already loaded and not forcing refresh, return cached accounts
    if (!forceRefresh && this.accounts().length > 0) {
      console.log(`📦 Using cached accounts for ${currentUser} | Count: ${this.accounts().length}`);
      return this.accounts();
    }

    console.log(`🔄 Loading accounts from backend for user: ${currentUser}`);
    this.isLoading.set(true);
    this.error.set(null);

    try {
      const fetchedAccounts = await this.accountService.getAccounts();
      console.log(`✅ Accounts cached for ${currentUser}:`, fetchedAccounts);
      
      this.accounts.set(fetchedAccounts);
      this.activeAccounts.set(fetchedAccounts.filter(acc => acc.accountStatus === 'ACTIVE'));
      console.log(`🟢 Active accounts for ${currentUser}:`, this.activeAccounts());
      console.log(`📊 Total: ${this.accounts().length} | Active: ${this.activeAccounts().length}`);
      
      return fetchedAccounts;
    } catch (err) {
      console.error(`❌ Error loading accounts for ${currentUser}:`, err);
      this.error.set('Failed to load accounts');
      return [];
    } finally {
      this.isLoading.set(false);
    }
  }

  clearCache(): void {
    console.log(`🗑️ Clearing accounts cache for ${this.auth.currentUser()}`);
    this.accounts.set([]);
    this.activeAccounts.set([]);
    this.cachedForUser = null;
  }

  addAccount(account: AccountResponse): void {
    console.log(`➕ Adding account to cache for ${this.auth.currentUser()}:`, account);
    const updated = [...this.accounts(), account];
    this.accounts.set(updated);
    
    if (account.accountStatus === 'ACTIVE') {
      const activeUpdated = [...this.activeAccounts(), account];
      this.activeAccounts.set(activeUpdated);
      console.log(`✅ Account added. Active count now: ${this.activeAccounts().length}`);
    }
  }
}
