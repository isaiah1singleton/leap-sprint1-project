import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { computed, effect, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthService } from './auth.service';

export interface MarketSymbol {
  symbol: string;
  name: string;
  type: 'equity' | 'crypto';
  exchange: string;
  currency: string;
}

export interface MarketQuote {
  symbol: string;
  price: number;
  bid: number;
  ask: number;
  spreadBps: number;
  currency: string | null;
  change: number | null;
  changePercent: number | null;
  previousClose: number | null;
  asOf: string;
  marketState: string;
}

export interface QuoteResult {
  symbol: string;
  quote: MarketQuote | null;
  source: string | null;
  stale: boolean;
  error: { code: string; message: string } | null;
}

@Injectable({ providedIn: 'root' })
export class MarketService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  readonly catalogue = signal<MarketSymbol[]>([]);
  readonly pins = signal<string[]>([]);
  readonly maxPins = 25;
  readonly pinnedSymbols = computed(() => this.pins().map(symbol =>
    this.catalogue().find(item => item.symbol === symbol)).filter((item): item is MarketSymbol => !!item));
  private catalogueRequest: Promise<void> | null = null;
  private storageKey: string | null = null;

  constructor() {
    effect(() => {
      const email = this.auth.currentUserEmail();
      this.storageKey = email ? `tr8ders:watchlist:${email}` : null;
      let saved: unknown = [];
      try { saved = JSON.parse(localStorage.getItem(this.storageKey ?? '') ?? '[]'); } catch { /* Empty watchlist. */ }
      this.pins.set(Array.isArray(saved)
        ? [...new Set(saved.filter((item): item is string => typeof item === 'string'))].slice(0, this.maxPins)
        : []);
    });
  }

  async loadCatalogue(): Promise<void> {
    if (this.catalogue().length) return;
    this.catalogueRequest ??= firstValueFrom(this.http.get<MarketSymbol[]>('/api/market/symbols', {
      params: { limit: 1000 },
    })).then(items => { this.catalogue.set(items); }).finally(() => { this.catalogueRequest = null; });
    await this.catalogueRequest;
    // Discard outdated or unsupported pins from browser storage.
    this.pins.update(pins => pins.filter(symbol => this.catalogue().some(item => item.symbol === symbol && item.type === 'equity')));
    this.savePins();
  }

  pin(item: MarketSymbol): string | null {
    if (this.pins().includes(item.symbol)) return null;
    if (item.type !== 'equity' || !this.catalogue().some(entry => entry.symbol === item.symbol && entry.type === 'equity')) {
      return 'Only stocks from the US catalogue can be pinned.';
    }
    if (this.pins().length >= this.maxPins) return 'You can pin up to 25 stocks. Unpin one to add another.';
    this.pins.update(pins => [...pins, item.symbol]);
    this.savePins();
    return null;
  }

  unpin(symbol: string): void {
    this.pins.update(pins => pins.filter(item => item !== symbol));
    this.savePins();
  }

  quote(symbol: string): Promise<QuoteResult> {
    return firstValueFrom(this.http.get<QuoteResult>(`/api/market/quotes/${encodeURIComponent(symbol)}`));
  }

  quotes(symbols: string[]): Promise<{ quotes: QuoteResult[]; disclaimer: string }> {
    return firstValueFrom(this.http.get<{ quotes: QuoteResult[]; disclaimer: string }>('/api/market/quotes', {
      params: { symbols: symbols.join(',') },
    }));
  }

  errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) return 'Your session has expired. Please sign out and sign in again.';
      if (error.error?.detail) return error.error.detail;
      if (error.status === 0) return 'Cannot reach the backend. Check that it is running.';
    }
    return 'Could not load market data. Please try again.';
  }

  private savePins(): void {
    if (this.storageKey) {
      try { localStorage.setItem(this.storageKey, JSON.stringify(this.pins())); } catch { /* Storage may be disabled. */ }
    }
  }
}
