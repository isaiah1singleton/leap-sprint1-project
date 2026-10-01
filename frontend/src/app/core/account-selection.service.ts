import { Injectable, signal } from '@angular/core';
import { AccountResponse } from './account.service';

@Injectable({ providedIn: 'root' })
export class AccountSelectionService {
  private readonly STORAGE_KEY = 'selectedAccount';
  
  readonly selectedAccount = signal<AccountResponse | null>(
    this.loadFromStorage()
  );

  selectAccount(account: AccountResponse): void {
    this.selectedAccount.set(account);
    sessionStorage.setItem(this.STORAGE_KEY, JSON.stringify(account));
  }

  clearSelectedAccount(): void {
    this.selectedAccount.set(null);
    sessionStorage.removeItem(this.STORAGE_KEY);
  }

  private loadFromStorage(): AccountResponse | null {
    const stored = sessionStorage.getItem(this.STORAGE_KEY);
    if (stored) {
      try {
        return JSON.parse(stored);
      } catch {
        return null;
      }
    }
    return null;
  }
}
