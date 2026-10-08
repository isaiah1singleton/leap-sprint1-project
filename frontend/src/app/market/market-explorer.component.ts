import { CommonModule } from '@angular/common';
import { Component, computed, ElementRef, HostListener, inject, input, OnDestroy, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AccountSelectionService } from '../core/account-selection.service';
import { MarketService, MarketSymbol, QuoteResult } from '../core/market.service';
import { Receipt, TradeMode, TradingService } from '../core/trading.service';

@Component({
  selector: 'app-market-explorer',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './market-explorer.component.html',
  styleUrl: './market-explorer.component.css',
})
export class MarketExplorerComponent implements OnInit, OnDestroy {
  readonly market = inject(MarketService);
  readonly trading = inject(TradingService);
  readonly account = inject(AccountSelectionService).selectedAccount;
  private readonly element = inject(ElementRef<HTMLElement>);
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
  readonly watchlistError = signal('');
  readonly refreshing = signal(false);
  readonly watchlistQuotes = signal<Record<string, QuoteResult>>({});
  readonly updatedAt = signal<Date | null>(null);
  readonly quantity = signal('1');
  readonly receipt = signal<Receipt | null>(null);
  readonly tradeError = signal('');
  readonly common = ['AAPL', 'MSFT', 'NVDA', 'AMZN', 'GOOGL'];
  private timer?: ReturnType<typeof setInterval>;
  private selectionVersion = 0;
  private destroyed = false;

  readonly matches = computed(() => {
    const term = this.query().trim().toUpperCase();
    if (!term) return [];
    return this.market.catalogue().filter(item => item.symbol.includes(term) || item.name.toUpperCase().includes(term))
      .sort((a, b) => this.rank(a.symbol, term) - this.rank(b.symbol, term) || a.symbol.localeCompare(b.symbol)).slice(0, 12);
  });
  readonly held = computed(() => this.trading.positions().find(item => item.sym === this.selected()?.symbol)?.qty ?? 0);
  readonly qty = computed(() => Number(this.quantity()));
  readonly validQuantity = computed(() => Number.isFinite(this.qty()) && this.qty() > 0);
  readonly quote = computed(() => this.result()?.quote ?? null);
  readonly buyDisabled = computed(() => !!this.disabledReason('buy'));
  readonly sellDisabled = computed(() => !!this.disabledReason('sell'));

  ngOnInit(): void {
    void this.loadCatalogue();
    this.timer = setInterval(() => {
      if (document.visibilityState === 'visible') {
        void this.refreshWatchlist();
        if (this.selected()) void this.loadQuote(this.selected()!, true);
      }
    }, 60_000);
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.selectionVersion++;
    if (this.timer) clearInterval(this.timer);
  }

  async loadCatalogue(): Promise<void> {
    this.loadingCatalogue.set(true);
    this.catalogueError.set('');
    try {
      await this.market.loadCatalogue();
      const requested = this.route.snapshot.queryParamMap.get('symbol');
      if (requested && this.showSearch()) this.selectSymbol(requested);
      await this.refreshWatchlist();
    }
    catch (error) { this.catalogueError.set(this.market.errorMessage(error)); }
    finally { this.loadingCatalogue.set(false); }
  }

  search(value: string): void {
    this.query.set(value);
    this.expanded.set(true);
    this.activeIndex.set(-1);
  }

  searchKey(event: KeyboardEvent): void {
    const items = this.matches();
    if (event.key === 'Escape') { this.expanded.set(false); return; }
    if (!items.length) return;
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      this.expanded.set(true);
      this.activeIndex.update(index => (index + (event.key === 'ArrowDown' ? 1 : items.length - 1)) % items.length);
    } else if (event.key === 'Enter' && this.expanded()) {
      event.preventDefault();
      this.select(items[Math.max(0, this.activeIndex())]);
    }
  }

  @HostListener('document:click', ['$event'])
  outsideClick(event: Event): void {
    if (!this.element.nativeElement.contains(event.target as Node)) this.expanded.set(false);
  }

  select(item: MarketSymbol): void {
    if (!this.showSearch()) {
      void this.router.navigate(['/app/markets'], { queryParams: { symbol: item.symbol } });
      return;
    }
    this.selected.set(item);
    this.query.set(item.symbol);
    this.expanded.set(false);
    this.quantity.set('1');
    this.receipt.set(null);
    this.tradeError.set('');
    this.pinError.set('');
    void this.loadQuote(item);
  }

  selectSymbol(symbol: string): void {
    const item = this.market.catalogue().find(entry => entry.symbol === symbol);
    if (item) this.select(item);
  }

  async loadQuote(item: MarketSymbol, background = false): Promise<void> {
    if (background && this.loadingQuote()) return;
    const version = ++this.selectionVersion;
    this.loadingQuote.set(true);
    this.quoteError.set('');
    if (!background) this.result.set(null);
    try {
      const result = await this.market.quote(item.symbol);
      if (this.destroyed || version !== this.selectionVersion) return;
      this.result.set(result);
      if (result.error) this.quoteError.set(result.error.message);
    } catch (error) {
      if (!this.destroyed && version === this.selectionVersion) {
        this.result.set(null);
        this.quoteError.set(this.market.errorMessage(error));
      }
    } finally {
      if (!this.destroyed && version === this.selectionVersion) this.loadingQuote.set(false);
    }
  }

  togglePin(item: MarketSymbol): void {
    if (this.market.pins().includes(item.symbol)) this.market.unpin(item.symbol);
    else this.pinError.set(this.market.pin(item) ?? '');
    void this.refreshWatchlist();
  }

  async refreshWatchlist(): Promise<void> {
    if (!this.showWatchlist() || this.refreshing()) return;
    const symbols = [...this.market.pins()];
    if (!symbols.length) { this.watchlistQuotes.set({}); return; }
    this.refreshing.set(true);
    this.watchlistError.set('');
    try {
      const response = await this.market.quotes(symbols);
      if (this.destroyed) return;
      this.watchlistQuotes.set(Object.fromEntries(response.quotes.map(item => [item.symbol, item])));
      this.updatedAt.set(new Date());
    } catch (error) {
      this.watchlistQuotes.set({});
      this.watchlistError.set(this.market.errorMessage(error));
    } finally {
      this.refreshing.set(false);
      if (!this.destroyed && symbols.join(',') !== this.market.pins().join(',')) void this.refreshWatchlist();
    }
  }

  disabledReason(mode: TradeMode): string {
    if (!this.account()) return 'Select a trading account on the dashboard to place a simulated trade.';
    if (!this.quote() || this.loadingQuote() || this.result()?.stale) return 'A current quote is required to trade.';
    if (!this.validQuantity()) return 'Enter a quantity greater than zero.';
    const price = mode === 'buy' ? this.quote()!.ask : this.quote()!.bid;
    if (!Number.isFinite(price * this.qty())) return 'Enter a smaller quantity.';
    if (mode === 'buy' && price * this.qty() > this.trading.cash()) return 'Insufficient cash for this quantity.';
    if (mode === 'sell' && this.qty() > this.held()) return 'You do not hold enough of this symbol to sell this quantity.';
    return '';
  }

  trade(mode: TradeMode): void {
    const reason = this.disabledReason(mode);
    if (reason) { this.tradeError.set(reason); return; }
    const result = this.trading.trade(mode, this.selected()!.symbol, this.quantity(), this.quote()!, this.selected()!.name);
    if ('message' in result) this.tradeError.set(result.message);
    else { this.tradeError.set(''); this.receipt.set(result); }
  }

  money(value: number, currency = 'USD'): string {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency, maximumFractionDigits: value < 1 ? 6 : 2 }).format(value);
  }

  change(result: QuoteResult | undefined | null): string {
    const value = result?.quote?.changePercent;
    return value == null ? 'Change unavailable' : `${value > 0 ? '+' : ''}${value.toFixed(2)}%`;
  }

  private rank(symbol: string, term: string): number { return symbol === term ? 0 : symbol.startsWith(term) ? 1 : 2; }
}
