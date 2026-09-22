import { Component, computed, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Receipt, TradingService, TxFailure } from '../core/trading.service';

type Mode = 'buy' | 'sell' | 'withdraw' | 'deposit';

@Component({
  selector: 'app-transact',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="row">
      <div class="card card-pad-lg tx-panel">
        <div class="seg">
          @for (option of modes; track option.id) {
            <button class="seg-opt" [class.active]="mode === option.id" (click)="setMode(option.id)">{{ option.label }}</button>
          }
        </div>

        @if (receipt) {
          <div style="margin-top:18px">
            <div class="receipt-mark">✓</div><h2 class="receipt-head">{{ receipt.head }}</h2>
            <p class="receipt-body">@for (line of receipt.lines; track line) { {{ line }}<br /> }</p>
            <button class="btn btn-primary btn-sm" (click)="receipt = null">Place another</button>
            <button class="btn btn-secondary btn-sm" routerLink="/app/orders">View order history</button>
          </div>
        } @else {
          <p class="auth-sub" style="margin-top:16px">{{ note }}</p>
          @if (isTrade) {
            <label class="label" for="symbol">Symbol</label>
            <input id="symbol" class="input" [class.invalid]="invalid('symbol')" [(ngModel)]="symbol" (keyup.enter)="submit()" placeholder="e.g. AAPL" />
            <label class="label" for="qty">Quantity</label>
            <input id="qty" class="input" [class.invalid]="invalid('qty')" [(ngModel)]="qty" (keyup.enter)="submit()" placeholder="Number of shares" />
          } @else {
            <label class="label" for="amount">Amount</label>
            <input id="amount" class="input" [class.invalid]="invalid('amount')" [(ngModel)]="amount" (keyup.enter)="submit()" placeholder="0.00" />
            <p class="stat-note">Cash available {{ trading.money(trading.cash()) }}</p>
          }
          @if (error) { <p class="error-text">{{ error }}</p> }
          <button class="btn btn-primary" (click)="submit()">Confirm {{ verb }}</button>
        }
      </div>

      <div class="card card-pad-lg" style="flex:1;display:flex;flex-direction:column">
        <h2 class="card-title">Your holdings</h2>
        @for (row of holdings(); track row.sym) {
          <div class="list-row"><div><strong>{{ row.sym }}</strong><div class="sub">{{ row.qty }} held</div></div><div style="text-align:right">{{ row.last }}<div class="sub">{{ row.value }}</div></div></div>
        }
        <p class="note" style="margin-top:auto;padding-top:14px">Symbols outside your holdings can be bought at a mock price of $100.00 in this prototype.</p>
      </div>
    </div>
  `,
})
export class TransactComponent {
  readonly trading = inject(TradingService);
  private readonly route = inject(ActivatedRoute);
  readonly modes: { id: Mode; label: string }[] = [
    { id: 'buy', label: 'Buy' }, { id: 'sell', label: 'Sell' },
    { id: 'withdraw', label: 'Withdraw' }, { id: 'deposit', label: 'Deposit' },
  ];
  mode: Mode = 'buy';
  symbol = '';
  qty = '';
  amount = '';
  error = '';
  receipt: Receipt | null = null;
  private errorFields: string[] = [];

  constructor() {
    const requested = this.route.snapshot.queryParamMap.get('mode') as Mode | null;
    if (requested && this.modes.some((mode) => mode.id === requested)) this.mode = requested;
  }

  get isTrade(): boolean { return this.mode === 'buy' || this.mode === 'sell'; }
  get verb(): string { return this.modes.find((mode) => mode.id === this.mode)!.label; }
  get note(): string {
    switch (this.mode) {
      case 'buy': return 'Market order, executed at the last traded price.';
      case 'sell': return 'You can only sell quantities you currently hold.';
      case 'withdraw': return 'Withdrawals go to your linked account ending 4417.';
      default: return 'Deposits from your linked account ending 4417 are available immediately.';
    }
  }

  readonly holdings = computed(() => this.trading.positions().map((position) => ({
    sym: position.sym,
    qty: position.qty.toLocaleString('en-US'),
    last: this.trading.money(position.last),
    value: this.trading.money(position.qty * position.last),
  })));

  invalid(field: string): boolean { return this.errorFields.includes(field); }
  setMode(mode: Mode): void {
    this.mode = mode;
    this.error = '';
    this.errorFields = [];
    this.receipt = null;
  }

  submit(): void {
    const result: TxFailure | Receipt = this.isTrade
      ? this.trading.trade(this.mode as 'buy' | 'sell', this.symbol, this.qty)
      : this.trading.transfer(this.mode as 'withdraw' | 'deposit', this.amount);
    if ('message' in result) {
      this.error = result.message;
      this.errorFields = result.fields;
      return;
    }
    this.error = '';
    this.errorFields = [];
    this.receipt = result;
    this.symbol = '';
    this.qty = '';
    this.amount = '';
  }
}
