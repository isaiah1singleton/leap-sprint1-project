import { computed, effect, inject, Injectable, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { AccountSelectionService } from './account-selection.service';

export interface Position {
  instrumentId: number;
  sym: string;
  name: string;
  qty: number;
  reserved: number;
  available: number;
}

export interface Activity {
  id: string;
  date: string;
  type: 'Buy' | 'Sell' | 'Deposit' | 'Withdraw';
  sym: string;
  qty: string;
  price: string;
  status: string;
  reason: string | null;
  orderId: number | null;
  timestamp: string;
}

export interface Receipt { head: string; lines: string[]; }
export interface TxFailure { message: string; fields: string[]; }
export type TradeMode = 'buy' | 'sell';
export type TransferMode = 'withdraw' | 'deposit';

interface BalanceDto { totalBalance: number; currency: string; }
interface HoldingDto {
  instrumentId: number; symbol: string; name: string;
  totalQuantity: number; reservedQuantity: number; availableQuantity: number;
}
interface OrderDto {
  orderId: number; symbol: string; side: 'BUY' | 'SELL'; quantity: number;
  indicativePrice: number; submittedAt: string;
  status: 'SUBMITTED' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED';
  reason: string | null;
}
interface MovementDto {
  cashMovementId: number; amount: { amount: number; currency: string };
  movementType: string; occurredAt: string; reason: string | null;
}
interface TransferDto { movement: MovementDto; balance: BalanceDto; }

@Injectable({ providedIn: 'root' })
export class TradingService {
  private readonly http = inject(HttpClient);
  private readonly account = inject(AccountSelectionService);
  private loadVersion = 0;
  readonly cash = signal(0);
  readonly positions = signal<Position[]>([]);
  readonly orders = signal<Activity[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly pendingOrderCount = computed(
    () => this.orders().filter(item => item.status === 'ACCEPTED').length,
  );

  constructor() {
    effect(() => {
      const selected = this.account.selectedAccount();
      void this.refresh(selected?.accountId ?? null);
    });
  }

  async refresh(accountId = this.account.selectedAccount()?.accountId ?? null): Promise<void> {
    if (accountId !== (this.account.selectedAccount()?.accountId ?? null)) return;
    const version = ++this.loadVersion;
    this.cash.set(0);
    this.positions.set([]);
    this.orders.set([]);
    this.error.set('');
    if (accountId == null) { this.loading.set(false); return; }
    this.loading.set(true);
    try {
      const base = `/api/accounts/${accountId}`;
      const [balance, holdings, orders, movements] = await Promise.all([
        firstValueFrom(this.http.get<BalanceDto>(`${base}/balance`)),
        firstValueFrom(this.http.get<HoldingDto[]>(`${base}/holdings`)),
        firstValueFrom(this.http.get<OrderDto[]>('/api/orders', { params: { accountId } })),
        firstValueFrom(this.http.get<MovementDto[]>(`${base}/cash-movements`)),
      ]);
      if (version !== this.loadVersion) return;
      this.cash.set(Number(balance.totalBalance));
      this.positions.set(holdings.map(item => ({
        instrumentId: item.instrumentId, sym: item.symbol, name: item.name,
        qty: Number(item.totalQuantity), reserved: Number(item.reservedQuantity),
        available: Number(item.availableQuantity),
      })));
      const activity: Activity[] = [
        ...orders.map(item => ({
          id: `ORD-${item.orderId}`, orderId: item.orderId, timestamp: item.submittedAt,
          date: this.date(item.submittedAt), type: item.side === 'BUY' ? 'Buy' as const : 'Sell' as const,
          sym: item.symbol, qty: String(item.quantity),
          price: this.money(Number(item.indicativePrice)),
          status: item.status, reason: item.reason,
        })),
        ...movements.map(item => ({
          id: `CASH-${item.cashMovementId}`, orderId: null, timestamp: item.occurredAt,
          date: this.date(item.occurredAt),
          type: item.movementType === 'DEPOSIT' ? 'Deposit' as const : 'Withdraw' as const,
          sym: '—', qty: '—', price: this.money(Number(item.amount.amount)),
          status: 'COMPLETED', reason: item.reason,
        })),
      ];
      this.orders.set(activity.sort((a, b) => Date.parse(b.timestamp) - Date.parse(a.timestamp)));
    } catch (error) {
      if (version === this.loadVersion) this.error.set(this.errorMessage(error));
    } finally {
      if (version === this.loadVersion) this.loading.set(false);
    }
  }

  money(value: number): string {
    return '$' + value.toLocaleString('en-US', {
      minimumFractionDigits: 2, maximumFractionDigits: 2,
    });
  }

  async trade(mode: TradeMode, instrumentId: number, qtyInput: string): Promise<TxFailure | Receipt> {
    const accountId = this.account.selectedAccount()?.accountId;
    const qty = Number(qtyInput);
    if (!accountId) return { message: 'Select a trading account first.', fields: ['account'] };
    if (!Number.isInteger(instrumentId) || instrumentId <= 0) return { message: 'Select an instrument.', fields: ['symbol'] };
    if (!Number.isFinite(qty) || qty <= 0) return { message: 'Enter a quantity greater than zero.', fields: ['qty'] };
    try {
      const order = await firstValueFrom(this.http.post<OrderDto>('/api/orders', {
        accountId, instrumentId, side: mode.toUpperCase(), quantity: qty,
      }));
      await this.refresh(accountId);
      return {
        head: `Order ${order.status.toLowerCase()}`,
        lines: [
          `${order.side} ${order.quantity} ${order.symbol}`,
          `Indicative price ${this.money(Number(order.indicativePrice))}; no execution yet`,
          ...(order.reason ? [order.reason] : []),
          `Reference ORD-${order.orderId}`,
        ],
      };
    } catch (error) {
      return { message: this.errorMessage(error), fields: [] };
    }
  }

  async transfer(mode: TransferMode, amountInput: string): Promise<TxFailure | Receipt> {
    const accountId = this.account.selectedAccount()?.accountId;
    const amount = Number(amountInput);
    if (!accountId) return { message: 'Select a trading account first.', fields: ['account'] };
    if (!Number.isFinite(amount) || amount <= 0 || !/^\d+(\.\d{1,2})?$/.test(amountInput.trim())) {
      return { message: 'Enter a positive amount with at most two decimal places.', fields: ['amount'] };
    }
    try {
      const result = await firstValueFrom(this.http.post<TransferDto>(
        `/api/accounts/${accountId}/transfers`, { type: mode.toUpperCase(), amount },
      ));
      await this.refresh(accountId);
      return {
        head: `${mode === 'deposit' ? 'Deposit' : 'Withdrawal'} completed`,
        lines: [
          `Amount ${this.money(Math.abs(Number(result.movement.amount.amount)))}`,
          `Cash balance ${this.money(Number(result.balance.totalBalance))}`,
          `Reference CASH-${result.movement.cashMovementId}`,
        ],
      };
    } catch (error) {
      return { message: this.errorMessage(error), fields: ['amount'] };
    }
  }

  async cancel(orderId: number): Promise<string | null> {
    try {
      await firstValueFrom(this.http.post<OrderDto>(`/api/orders/${orderId}/cancel`, {}));
      await this.refresh();
      return null;
    } catch (error) {
      return this.errorMessage(error);
    }
  }

  private date(timestamp: string): string {
    return new Date(timestamp).toLocaleString('en-GB', { timeZone: 'UTC' }) + ' UTC';
  }

  private errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) return 'Your session has expired. Please sign out and sign in again.';
      if (error.status === 0) return 'Cannot reach the backend. Check its address and CORS configuration.';
      return error.error?.detail || error.error?.message || 'The request failed. Please try again.';
    }
    return 'The request failed. Please try again.';
  }
}
