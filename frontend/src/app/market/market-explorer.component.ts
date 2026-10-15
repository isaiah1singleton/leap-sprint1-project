import { CommonModule } from '@angular/common';
import { OrderProgressComponent } from './order-progress.component';
import {
  Component,
  computed,
  ElementRef,
  HostListener,
  inject,
  input,
  OnDestroy,
  OnInit,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  ActivatedRoute,
  Router,
  RouterLink,
} from '@angular/router';
import {
  AccountSelectionService,
} from '../core/account-selection.service';
import {
  MarketService,
  MarketSymbol,
  QuoteResult,
} from '../core/market.service';
import {
  OrderDto,
  TradeMode,
  TradingService,
} from '../core/trading.service';

@Component({
  selector: 'app-market-explorer',
  imports: [CommonModule, FormsModule, RouterLink, OrderProgressComponent],
  templateUrl: './market-explorer.component.html',
  styleUrl: './market-explorer.component.css',
})
export class MarketExplorerComponent
  implements OnInit, OnDestroy {
  readonly market = inject(MarketService);
  readonly trading = inject(TradingService);
  readonly account =
    inject(AccountSelectionService).selectedAccount;
  private readonly element =
    inject(ElementRef<HTMLElement>);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly showWatchlist = input(true);
  readonly showSearch = input(true);
  readonly query = signal('');
  readonly expanded = signal(false);
  readonly activeIndex = signal(-1);
  readonly loadingCatalogue = signal(true);
  readonly catalogueError = signal('');
  readonly selected = signal<MarketSymbol | null>(null);
  readonly result = signal<QuoteResult | null>(null);
  readonly loadingQuote = signal(false);
  readonly quoteError = signal('');
  readonly pinError = signal('');
  readonly watchlistQuotes = this.market.cachedQuotes;
  readonly quantity = signal('1');
  readonly tradeError = signal('');
  readonly orderPopupOpen = signal(false);
  readonly submittedOrder = signal<OrderDto | null>(null);
  readonly common = computed(() =>
    this.market.catalogue()
      .filter(item => item.type === 'equity')
      .slice(0, 5));
  readonly submitting = signal(false);
  readonly quoteClock = signal(Date.now());

  private quoteClockTimer?:
    ReturnType<typeof setInterval>;
  private selectionVersion = 0;
  private destroyed = false;

  readonly matches = computed(() => {
    const term = this.query().trim().toUpperCase();
    if (!term) return [];

    return this.market.catalogue()
      .filter(item =>
        item.symbol.includes(term)
        || item.name.toUpperCase().includes(term))
      .sort((a, b) =>
        this.rank(a.symbol, term)
        - this.rank(b.symbol, term)
        || a.symbol.localeCompare(b.symbol))
      .slice(0, 12);
  });

  readonly held = computed(() =>
    this.trading.positions()
      .find(item =>
        item.sym === this.selected()?.symbol)?.qty ?? 0);
  readonly qty = computed(() =>
    Number(this.quantity()));
  readonly validQuantity = computed(() =>
    Number.isFinite(this.qty()) && this.qty() > 0);
  readonly quote = computed(() =>
    this.result()?.quote ?? null);
  readonly buyDisabled = computed(() =>
    !!this.disabledReason('buy'));
  readonly sellDisabled = computed(() =>
    !!this.disabledReason('sell'));

  ngOnInit(): void {
    void this.loadCatalogue();

    this.quoteClockTimer = setInterval(
      () => this.quoteClock.set(Date.now()),
      1_000);
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.selectionVersion++;
    if (this.quoteClockTimer) {
      clearInterval(this.quoteClockTimer);
    }
  }

  async loadCatalogue(): Promise<void> {
    this.loadingCatalogue.set(true);
    this.catalogueError.set('');

    try {
      await this.market.loadCatalogue();
      const requested =
        this.route.snapshot.queryParamMap.get('symbol');
      if (requested && this.showSearch()) {
        this.selectSymbol(requested);
      }
    } catch (error) {
      this.catalogueError.set(
        this.market.errorMessage(error));
    } finally {
      this.loadingCatalogue.set(false);
    }
  }

  search(value: string): void {
    this.query.set(value);
    this.expanded.set(true);
    this.activeIndex.set(-1);
  }

  searchKey(event: KeyboardEvent): void {
    const items = this.matches();
    if (event.key === 'Escape') {
      this.expanded.set(false);
      return;
    }
    if (!items.length) return;

    if (event.key === 'ArrowDown'
      || event.key === 'ArrowUp') {
      event.preventDefault();
      this.expanded.set(true);
      this.activeIndex.update(index =>
        (index + (event.key === 'ArrowDown'
          ? 1 : items.length - 1)) % items.length);
    } else if (event.key === 'Enter'
      && this.expanded()) {
      event.preventDefault();
      this.select(items[Math.max(
        0, this.activeIndex())]);
    }
  }

  @HostListener('document:click', ['$event'])
  outsideClick(event: Event): void {
    if (!this.element.nativeElement.contains(
      event.target as Node)) {
      this.expanded.set(false);
    }
  }

  select(item: MarketSymbol): void {
    if (!this.showSearch()) {
      void this.router.navigate(
        ['/app/markets'],
        { queryParams: { symbol: item.symbol } });
      return;
    }

    this.selected.set(item);
    this.query.set(item.symbol);
    this.expanded.set(false);
    this.quantity.set('1');
    this.tradeError.set('');
    this.pinError.set('');
    void this.loadQuote(item);
  }

  selectSymbol(symbol: string): void {
    const item = this.market.catalogue()
      .find(entry => entry.symbol === symbol);
    if (item) this.select(item);
  }

  async loadQuote(
    item: MarketSymbol,
    background = false,
  ): Promise<void> {
    if (background && this.loadingQuote()) return;

    const version = ++this.selectionVersion;
    this.loadingQuote.set(true);
    this.quoteError.set('');
    if (!background) this.result.set(null);

    try {
      const result =
        await this.market.quote(item.symbol);
      if (this.destroyed
        || version !== this.selectionVersion) {
        return;
      }

      // The ask, bid, currency, and asOf change together.
      this.result.set(result);
      if (result.error) {
        this.quoteError.set(result.error.message);
      }
    } catch (error) {
      if (!this.destroyed
        && version === this.selectionVersion) {
        this.result.set(null);
        this.quoteError.set(
          this.market.errorMessage(error));
      }
    } finally {
      if (!this.destroyed
        && version === this.selectionVersion) {
        this.loadingQuote.set(false);
      }
    }
  }

  togglePin(item: MarketSymbol): void {
    if (this.market.pins().includes(item.symbol)) {
      this.market.unpin(item.symbol);
    } else {
      this.pinError.set(this.market.pin(item) ?? '');
    }
  }

  disabledReason(mode: TradeMode): string {
    if (!this.account()) {
      return 'Select a trading account on the dashboard to place an order.';
    }
    if (this.submitting()) {
      return 'Submitting order…';
    }

    const instrument = this.selected();
    const snapshot = this.result();
    const quote = snapshot?.quote;

    if (!instrument
      || !quote
      || snapshot?.error
      || this.loadingQuote()
      || quote.symbol !== instrument.symbol
      || !quote.currency
      || quote.currency !== instrument.currency) {
      return 'A valid provider quote is required to trade.';
    }

    const quoteTime = Date.parse(quote.asOf);
    const now = this.quoteClock();
    if (!Number.isFinite(quoteTime)
      || quoteTime > now) {
      return 'A valid quote timestamp that is not in the future is required.';
    }

    if (!this.validQuantity()) {
      return 'Enter a quantity greater than zero.';
    }

    const price =
      mode === 'buy' ? quote.ask : quote.bid;
    if (!Number.isFinite(price)
      || price <= 0
      || !Number.isFinite(price * this.qty())) {
      return 'A valid side price and quantity are required.';
    }

    return '';
  }

  async trade(mode: TradeMode): Promise<void> {
    const reason = this.disabledReason(mode);
    if (reason) {
      this.tradeError.set(reason);
      return;
    }

    // Capture one displayed quote before the request starts.
    const instrumentId =
      this.selected()!.instrumentId;
    const quote = this.result()!.quote!;
    const price =
      mode === 'buy' ? quote.ask : quote.bid;
    const quantity = this.quantity();

    this.submitting.set(true);
    this.submittedOrder.set(null);
    this.tradeError.set('');
    this.orderPopupOpen.set(true);
    try {
      const result = await this.trading.trade(
        mode,
        instrumentId,
        quantity,
        price,
        quote.asOf,
        quote.currency!);

      if ('message' in result) {
        this.tradeError.set(result.message);
      } else {
        this.tradeError.set('');
        this.submittedOrder.set(result.order);
      }
    } finally {
      this.submitting.set(false);
    }
  }

  money(
    value: number,
    currency = 'USD',
  ): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
      maximumFractionDigits:
        value < 1 ? 6 : 2,
    }).format(value);
  }

  quoteMoney(
    value: number,
    currency = 'USD',
  ): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 8,
    }).format(value);
  }

  change(
    result: QuoteResult | undefined | null,
  ): string {
    const value =
      result?.quote?.changePercent;
    return value == null
      ? 'Change unavailable'
      : `${value > 0 ? '+' : ''}${value.toFixed(2)}%`;
  }

  private rank(
    symbol: string,
    term: string,
  ): number {
    return symbol === term
      ? 0
      : symbol.startsWith(term) ? 1 : 2;
  }
}
