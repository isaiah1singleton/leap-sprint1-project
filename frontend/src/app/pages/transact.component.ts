import { Component, computed, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Receipt, TradingService, TxFailure } from '../core/trading.service';
import { MarketExplorerComponent } from '../market/market-explorer.component';

type Mode = 'buy' | 'sell' | 'withdraw' | 'deposit';

@Component({
  selector: 'app-transact',
  imports: [FormsModule, RouterLink, MarketExplorerComponent],
  templateUrl: './transact.component.html',
})
export class TransactComponent {
  readonly trading = inject(TradingService);
  private readonly route = inject(ActivatedRoute);
  readonly modes: { id: Mode; label: string }[] = [
    { id: 'buy', label: 'Buy' }, { id: 'sell', label: 'Sell' },
    { id: 'withdraw', label: 'Withdraw' }, { id: 'deposit', label: 'Deposit' },
  ];
  mode: Mode = 'buy';
  amount = '';
  error = '';
  receipt: Receipt | null = null;
  submitting = false;
  private errorFields: string[] = [];

  constructor() {
    const requested = this.route.snapshot.queryParamMap.get('mode') as Mode | null;
    if (requested && this.modes.some((mode) => mode.id === requested)) this.mode = requested;
  }

  get isTrade(): boolean { return this.mode === 'buy' || this.mode === 'sell'; }
  get verb(): string { return this.modes.find((mode) => mode.id === this.mode)!.label; }
  get note(): string {
    switch (this.mode) {
      case 'buy': return 'Search a tradable instrument below. Accepted orders await execution; the quote is indicative.';
      case 'sell': return 'You can only sell quantities you currently hold.';
      case 'withdraw': return 'A simulated withdrawal reduces your account cash immediately.';
      default: return 'A simulated deposit increases your account cash immediately.';
    }
  }

  readonly holdings = computed(() => this.trading.positions().map((position) => ({
    sym: position.sym,
    qty: position.qty.toLocaleString('en-US'),
    available: position.available.toLocaleString('en-US'),
  })));

  invalid(field: string): boolean { return this.errorFields.includes(field); }
  setMode(mode: Mode): void {
    this.mode = mode;
    this.error = '';
    this.errorFields = [];
    this.receipt = null;
  }

  async submit(): Promise<void> {
    if (this.isTrade) return;
    this.submitting = true;
    const result: TxFailure | Receipt = await this.trading.transfer(this.mode as 'withdraw' | 'deposit', this.amount);
    this.submitting = false;
    if ('message' in result) {
      this.error = result.message;
      this.errorFields = result.fields;
      return;
    }
    this.error = '';
    this.errorFields = [];
    this.receipt = result;
    this.amount = '';
  }
}
