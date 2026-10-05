import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_CONFIG } from './api.config';
import { AuthService } from './auth.service';

export interface AccountResponse {
  accountId: number;
  accountName: string;
  accountStatus: string;
  openedAt: string;
}

export interface AccountFailure {
  message: string;
  fields: string[];
}

@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly apiUrl = API_CONFIG.endpoints.accounts.list;

  async getAccounts(): Promise<AccountResponse[]> {
    const currentUser = this.auth.currentUser();
    try {
      console.log(`🌐 [AccountService] Fetching accounts for ${currentUser} from: ${this.apiUrl}`);
      const accounts = await firstValueFrom(this.http.get<AccountResponse[]>(this.apiUrl));
      console.log(`✅ [AccountService] Received ${accounts?.length || 0} accounts for ${currentUser}`);
      return accounts || [];
    } catch (error) {
      console.error(`❌ [AccountService] Error fetching accounts for ${currentUser}:`, error);
      if (error instanceof HttpErrorResponse) {
        console.error('HTTP Error:', error.status, error.message, error.error);
      }
      return [];
    }
  }

  async createAccount(accountName: string): Promise<AccountResponse | AccountFailure> {
    const name = accountName.trim();
    if (!name) {
      return { message: 'Account name is required.', fields: ['accountName'] };
    }

    const currentUser = this.auth.currentUser();
    try {
      console.log(`🆕 [AccountService] Creating account "${name}" for ${currentUser}`);
      const response = await firstValueFrom(
        this.http.post<AccountResponse>(this.apiUrl, { accountName: name }),
      );
      console.log(`✅ [AccountService] Account created for ${currentUser}:`, response);
      return response;
    } catch (error) {
      console.error(`❌ [AccountService] Error creating account for ${currentUser}:`, error);
      return this.apiFailure(error);
    }
  }

  private apiFailure(error: unknown): AccountFailure {
    if (error instanceof HttpErrorResponse) {
      const detail = error.error?.detail || error.message;
      return { message: detail, fields: [] };
    }
    return { message: 'An error occurred. Please try again.', fields: [] };
  }
}
