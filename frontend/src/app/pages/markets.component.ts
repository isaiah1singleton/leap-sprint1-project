import { Component, inject, OnInit, signal } from '@angular/core';
import { MarketService, MarketSymbol, QuoteResult } from '../core/market.service';

interface MarketRow {
  instrument: MarketSymbol;
  result: QuoteResult;
}

@Component({
  selector: 'app-markets',
  templateUrl: './markets.component.html',
})
export class MarketsComponent implements OnInit {
  readonly market = inject(MarketService);

  readonly rows = signal<MarketRow[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');

  ngOnInit(): void {
    void this.loadMarkets();
  }

  async loadMarkets(): Promise<void> {
    this.loading.set(true);
    this.error.set('');

    try {
      await this.market.loadCatalogue();

      const instruments = this.market
        .catalogue()
        .filter((item) => item.type !== 'equity')
        .slice(0, 25);

      if (!instruments.length) {
        this.rows.set([]);
        return;
      }

      const response = await this.market.quotes(instruments.map((instrument) => instrument.symbol));

      const results = new Map(response.quotes.map((result) => [result.symbol, result]));

      this.rows.set(
        instruments
          .map((instrument) => ({
            instrument,
            result: results.get(instrument.symbol),
          }))
          .filter((row): row is MarketRow => row.result !== undefined),
      );
    } catch (error) {
      this.error.set(this.market.errorMessage(error));
    } finally {
      this.loading.set(false);
    }
  }

  price(result: QuoteResult): string {
    if (!result.quote) return 'Unavailable';

    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: result.quote.currency || 'USD',
      maximumFractionDigits: result.quote.price < 1 ? 6 : 2,
    }).format(result.quote.price);
  }

  change(result: QuoteResult): string {
    const value = result.quote?.changePercent;

    if (value == null) return 'Unavailable';

    return `${value > 0 ? '+' : ''}${value.toFixed(2)}%`;
  }
}
