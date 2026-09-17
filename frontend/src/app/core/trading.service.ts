import { computed, Injectable, signal } from '@angular/core';

export interface Position {
  sym: string;
  name: string;
  qty: number;
  avg: number;
  last: number;
}

export interface Order {
  id: string;
  date: string;
  type: 'Buy' | 'Sell' | 'Deposit' | 'Withdraw';
  sym: string;
  qty: string;
  price: string;
  status: 'Filled' | 'Pending' | 'Cleared' | 'Rejected';
}

export interface MarketRow {
  name: string;
  level: string;
  change: string;
  up: boolean;
}

export interface Receipt {
  head: string;
  lines: string[];
}

export interface TxFailure {
  message: string;
  fields: string[];
}

export type TradeMode = 'buy' | 'sell';
export type TransferMode = 'withdraw' | 'deposit';

const UNKNOWN_SYMBOL_PRICE = 100;

/** In-memory trading state. Replace method bodies with API calls when available. */
@Injectable({ providedIn: 'root' })
export class TradingService {
  readonly cash = signal(48250);
  readonly positions = signal<Position[]>([
    { sym: 'AAPL', name: 'Apple Inc.', qty: 320, avg: 188.4, last: 214.62 },
    { sym: 'MSFT', name: 'Microsoft Corp.', qty: 140, avg: 372.1, last: 408.85 },
    { sym: 'NVDA', name: 'NVIDIA Corp.', qty: 90, avg: 104.25, last: 121.4 },
    { sym: 'VOD.L', name: 'Vodafone Group', qty: 4200, avg: 0.72, last: 0.69 },
  ]);
  readonly orders = signal<Order[]>([
    { id: 'ORD-10442', date: '15 Sep 2026, 14:22', type: 'Buy', sym: 'NVDA', qty: '90', price: '121.40', status: 'Filled' },
    { id: 'ORD-10438', date: '15 Sep 2026, 09:51', type: 'Sell', sym: 'TSLA', qty: '55', price: '242.10', status: 'Filled' },
    { id: 'ORD-10431', date: '12 Sep 2026, 16:04', type: 'Deposit', sym: '—', qty: '—', price: '10,000.00', status: 'Cleared' },
    { id: 'ORD-10425', date: '11 Sep 2026, 11:37', type: 'Buy', sym: 'MSFT', qty: '40', price: '405.90', status: 'Filled' },
    { id: 'ORD-10419', date: '09 Sep 2026, 10:12', type: 'Withdraw', sym: '—', qty: '—', price: '2,500.00', status: 'Rejected' },
  ]);
  readonly markets: MarketRow[] = [
    { name: 'S&P 500', level: '5,682.31', change: '+0.42%', up: true },
    { name: 'NASDAQ 100', level: '20,114.86', change: '+0.68%', up: true },
    { name: 'FTSE 100', level: '8,341.09', change: '-0.17%', up: false },
    { name: 'EUR / USD', level: '1.0942', change: '+0.08%', up: true },
    { name: 'Gold (spot)', level: '2,418.55', change: '-0.31%', up: false },
    { name: 'Brent Crude', level: '78.24', change: '+1.12%', up: false },
  ];

  readonly invested = computed(() =>
    this.positions().reduce((total, position) => total + position.qty * position.last, 0),
  );
  readonly totalValue = computed(() => this.invested() + this.cash());
  readonly pendingOrderCount = computed(
    () => this.orders().filter((order) => order.status === 'Pending').length,
  );

  money(value: number): string {
    return '$' + value.toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  }

  trade(mode: TradeMode, symbolInput: string, qtyInput: string): TxFailure | Receipt {
    const sym = symbolInput.trim().toUpperCase();
    const qty = Number.parseFloat(qtyInput);
    if (!sym) return { message: 'Enter a symbol.', fields: ['symbol'] };
    if (!(qty > 0)) return { message: 'Enter a quantity greater than zero.', fields: ['qty'] };

    const held = this.positions().find((position) => position.sym === sym);
    if (mode === 'sell' && (!held || held.qty < qty)) {
      return { message: `You do not hold enough of ${sym} to sell that quantity.`, fields: ['symbol', 'qty'] };
    }

    const price = held ? held.last : UNKNOWN_SYMBOL_PRICE;
    const consideration = price * qty;
    if (mode === 'buy' && consideration > this.cash()) {
      return { message: `Insufficient cash. Available ${this.money(this.cash())}.`, fields: ['qty'] };
    }

    const next = this.positions().map((position) =>
      position.sym === sym
        ? { ...position, qty: mode === 'buy' ? position.qty + qty : position.qty - qty }
        : position,
    );
    if (mode === 'buy' && !held) next.push({ sym, name: sym, qty, avg: price, last: price });
    this.positions.set(next.filter((position) => position.qty > 0));
    this.cash.update((cash) => (mode === 'buy' ? cash - consideration : cash + consideration));

    const order = this.pushOrder({
      type: mode === 'buy' ? 'Buy' : 'Sell',
      sym,
      qty: String(qty),
      price: price.toFixed(2),
      status: 'Filled',
    });
    return {
      head: `${order.type} order filled`,
      lines: [
        `${order.qty} ${sym} at ${this.money(price)}`,
        `Consideration ${this.money(consideration)}`,
        `Reference ${order.id}`,
      ],
    };
  }

  transfer(mode: TransferMode, amountInput: string): TxFailure | Receipt {
    const amount = Number.parseFloat(amountInput);
    if (!(amount > 0)) return { message: 'Enter an amount greater than zero.', fields: ['amount'] };
    if (mode === 'withdraw' && amount > this.cash()) {
      return { message: `Insufficient cash. Available ${this.money(this.cash())}.`, fields: ['amount'] };
    }

    this.cash.update((cash) => (mode === 'withdraw' ? cash - amount : cash + amount));
    const order = this.pushOrder({
      type: mode === 'withdraw' ? 'Withdraw' : 'Deposit',
      sym: '—',
      qty: '—',
      price: amount.toFixed(2),
      status: mode === 'withdraw' ? 'Pending' : 'Cleared',
    });
    return {
      head: `${order.type} requested`,
      lines: [
        mode === 'withdraw'
          ? `${this.money(amount)} to account ending 4417`
          : `${this.money(amount)} from account ending 4417`,
        mode === 'withdraw' ? 'Settles in 1–2 business days' : 'Available immediately',
        `Reference ${order.id}`,
      ],
    };
  }

  private pushOrder(partial: Omit<Order, 'id' | 'date'>): Order {
    const order: Order = {
      id: 'ORD-' + (10450 + this.orders().length),
      date: '16 Sep 2026, 10:04',
      ...partial,
    };
    this.orders.update((orders) => [order, ...orders]);
    return order;
  }
}
