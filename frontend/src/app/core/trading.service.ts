import { computed, effect, inject, Injectable, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom, Observable } from 'rxjs';
import { AccountSelectionService } from './account-selection.service';

export interface Position {
  instrumentId: number;
  sym: string;
  name: string;
  qty: number;
  reserved: number;
  available: number;
}

export interface OrderActivity {
  id: string;
  date: string;
  type: 'Buy' | 'Sell';
  sym: string;
  qty: string;
  indicativePrice: string;
  executionPrice: string | null;
  status: string;
  reason: string | null;
  orderId: number;
  timestamp: string;
}

export interface CashActivity {
  id: string;
  date: string;
  type: 'Deposit' | 'Withdraw' | 'Adjustment';
  amount: string;
  reason: string | null;
  timestamp: string;
}

export interface Receipt {
  head: string;
  lines: string[];
}
export interface TradeReceipt extends Receipt {
  order: OrderDto;
}
export interface TxFailure {
  message: string;
  fields: string[];
}
export type TradeMode = 'buy' | 'sell';
export type TransferMode = 'withdraw' | 'deposit';

interface BalanceDto {
  totalBalance: number;
  currency: string;
}
interface HoldingDto {
  instrumentId: number;
  symbol: string;
  name: string;
  totalQuantity: number;
  reservedQuantity: number;
  availableQuantity: number;
}
export interface OrderDto {
  accountId: number;
  orderId: number;
  symbol: string;
  side: 'BUY' | 'SELL';
  quantity: number;
  indicativePrice: number;
  executionPrice?: number | null;
  currency?: string;
  submittedAt: string;
  status: 'SUBMITTED' | 'ACCEPTED' | 'REJECTED' | 'FILLED' | 'EXPIRED';
  reason: string | null;
}
interface MovementDto {
  cashMovementId: number;
  amount: { amount: number; currency: string };
  movementType: 'DEPOSIT' | 'WITHDRAW' | 'TRADE' | 'ADJUSTMENT';
  occurredAt: string;
  reason: string | null;
}
interface TransferDto {
  movement: MovementDto;
  balance: BalanceDto;
}
interface FillDto {
  executionPrice: { amount: number; currency: string };
}

@Injectable({ providedIn: 'root' })
export class TradingService {
  private readonly http = inject(HttpClient);
  private readonly account = inject(AccountSelectionService);
  private loadVersion = 0;
  private fillPriceAccountId: number | null = null;
  private readonly fillRequests = new Map<number, Promise<string>>();
  readonly refreshRevision = signal(0);
  readonly cash = signal(0);
  readonly positions = signal<Position[]>([]);
  readonly orders = signal<OrderActivity[]>([]);
  readonly fillExecutionPrices = signal<Record<number, string>>({});
  readonly cashActivity = signal<CashActivity[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly pendingOrderCount = computed(
    () => this.orders().filter((item) => item.status === 'ACCEPTED').length,
  );

  constructor() {
    effect(() => {
      const selected = this.account.selectedAccount();
      void this.refresh(selected?.accountId ?? null);
    });
  }

  async refresh(accountId = this.account.selectedAccount()?.accountId ?? null): Promise<void> {
    if (accountId !== (this.account.selectedAccount()?.accountId ?? null)) {
      return;
    }
    const version = ++this.loadVersion;
    if (this.fillPriceAccountId !== accountId) {
      this.fillPriceAccountId = accountId;
      this.fillExecutionPrices.set({});
    }
    this.refreshRevision.update((value) => value + 1);
    this.cash.set(0);
    this.positions.set([]);
    this.orders.set([]);
    this.cashActivity.set([]);
    this.error.set('');
    if (accountId == null) {
      this.loading.set(false);
      return;
    }

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
      this.positions.set(
        holdings
          .filter((item) => Number(item.totalQuantity) > 0)
          .map((item) => ({
            instrumentId: item.instrumentId,
            sym: item.symbol,
            name: item.name,
            qty: Number(item.totalQuantity),
            reserved: Number(item.reservedQuantity),
            available: Number(item.availableQuantity),
          })),
      );

      const orderActivity: OrderActivity[] = orders.map((item) => ({
        id: `ORD-${item.orderId}`,
        orderId: item.orderId,
        timestamp: item.submittedAt,
        date: this.date(item.submittedAt),
        type: item.side === 'BUY' ? ('Buy' as const) : ('Sell' as const),
        sym: item.symbol,
        qty: String(item.quantity),
        indicativePrice: this.formatMoney(
          Number(item.indicativePrice),
          item.currency ?? 'USD',
          true,
        ),
        executionPrice:
          item.executionPrice == null
            ? null
            : this.formatMoney(Number(item.executionPrice), item.currency ?? 'USD', true),
        status: item.status,
        reason: item.reason,
      }));
      this.orders.set(
        orderActivity.sort((a, b) => Date.parse(b.timestamp) - Date.parse(a.timestamp)),
      );
      this.cashActivity.set(
        movements
          .map((item) => this.toCashActivity(item))
          .filter((item): item is CashActivity => item !== null)
          .sort((a, b) => Date.parse(b.timestamp) - Date.parse(a.timestamp)),
      );
    } catch (error) {
      if (version === this.loadVersion) {
        this.error.set(this.errorMessage(error));
      }
    } finally {
      if (version === this.loadVersion) {
        this.loading.set(false);
      }
    }
  }

  money(value: number): string {
    return this.formatMoney(value, 'USD');
  }

  private formatMoney(value: number, currency: string, price = false): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: price ? 8 : 2,
    }).format(value);
  }

  private toCashActivity(item: MovementDto): CashActivity | null {
    if (item.movementType === 'TRADE') return null;
    const type =
      item.movementType === 'DEPOSIT'
        ? 'Deposit'
        : item.movementType === 'WITHDRAW'
          ? 'Withdraw'
          : 'Adjustment';
    const amount = Number(item.amount.amount);
    return {
      id: `CASH-${item.cashMovementId}`,
      timestamp: item.occurredAt,
      date: this.date(item.occurredAt),
      type,
      amount: `${amount < 0 ? '−' : '+'}${this.formatMoney(Math.abs(amount), item.amount.currency)}`,
      reason: item.reason,
    };
  }

  async trade(
    mode: TradeMode,
    instrumentId: number,
    qtyInput: string,
    indicativePrice: number,
    quoteAsOf: string,
    quoteCurrency: string,
  ): Promise<TxFailure | TradeReceipt> {
    const accountId = this.account.selectedAccount()?.accountId;
    const qty = Number(qtyInput);

    if (!accountId) {
      return {
        message: 'Select a trading account first.',
        fields: ['account'],
      };
    }
    if (!Number.isInteger(instrumentId) || instrumentId <= 0) {
      return {
        message: 'Select an instrument.',
        fields: ['symbol'],
      };
    }
    if (!Number.isFinite(qty) || qty <= 0) {
      return {
        message: 'Enter a quantity greater than zero.',
        fields: ['qty'],
      };
    }
    if (!Number.isFinite(indicativePrice) || indicativePrice <= 0 || !quoteAsOf || !quoteCurrency) {
      return {
        message: 'Refresh the displayed quote and try again.',
        fields: ['quote'],
      };
    }

    try {
      const order = await firstValueFrom(
        this.http.post<OrderDto>('/api/orders', {
          accountId,
          instrumentId,
          side: mode.toUpperCase(),
          quantity: qty,
          indicativePrice,
          quoteAsOf,
          quoteCurrency,
        }),
      );
      await this.refresh(accountId);

      return {
        order,
        head: `Order ${order.status.toLowerCase()}`,
        lines: [
          `${order.side} ${order.quantity} ${order.symbol}`,
          `Indicative price ${this.money(Number(order.indicativePrice))}; no execution yet`,
          ...(order.reason ? [order.reason] : []),
          `Reference ORD-${order.orderId}`,
        ],
      };
    } catch (error) {
      return {
        message: this.errorMessage(error),
        fields: [],
      };
    }
  }

  getOrder(orderId: number): Observable<OrderDto> {
    return this.http.get<OrderDto>(`/api/orders/${orderId}`);
  }

  async getFillExecutionPrice(orderId: number): Promise<string> {
    const fill = await firstValueFrom(this.http.get<FillDto>(`/api/orders/${orderId}/fill`));
    return this.formatMoney(Number(fill.executionPrice.amount), fill.executionPrice.currency, true);
  }

  ensureFillExecutionPrice(orderId: number): void {
    const accountId = this.account.selectedAccount()?.accountId;
    if (accountId == null || this.fillExecutionPrices()[orderId] || this.fillRequests.has(orderId))
      return;

    const request = this.getFillExecutionPrice(orderId);
    this.fillRequests.set(orderId, request);
    void request
      .then((price) => {
        if (this.account.selectedAccount()?.accountId === accountId) {
          this.fillExecutionPrices.update((prices) => ({ ...prices, [orderId]: price }));
        }
      })
      .catch(() => {
        // A failed fill lookup leaves the price unavailable until the next refresh.
      })
      .finally(() => {
        if (this.fillRequests.get(orderId) === request) this.fillRequests.delete(orderId);
      });
  }

  async transfer(mode: TransferMode, amountInput: string): Promise<TxFailure | Receipt> {
    const accountId = this.account.selectedAccount()?.accountId;
    const amount = Number(amountInput);

    if (!accountId) {
      return {
        message: 'Select a trading account first.',
        fields: ['account'],
      };
    }
    if (!Number.isFinite(amount) || amount <= 0 || !/^\d+(\.\d{1,2})?$/.test(amountInput.trim())) {
      return {
        message: 'Enter a positive amount with at most two decimal places.',
        fields: ['amount'],
      };
    }

    try {
      const result = await firstValueFrom(
        this.http.post<TransferDto>(`/api/accounts/${accountId}/transfers`, {
          type: mode.toUpperCase(),
          amount,
        }),
      );
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
      return {
        message: this.errorMessage(error),
        fields: ['amount'],
      };
    }
  }

  private date(timestamp: string): string {
    return new Date(timestamp).toLocaleString('en-GB', { timeZone: 'UTC' }) + ' UTC';
  }

  private errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) {
        return 'Your session has expired. Please sign out and sign in again.';
      }
      if (error.status === 0) {
        return 'Cannot reach the backend. Check its address and CORS configuration.';
      }
      return error.error?.detail || error.error?.message || 'The request failed. Please try again.';
    }
    return 'The request failed. Please try again.';
  }
}
