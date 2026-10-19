import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AccountSelectionService } from '../core/account-selection.service';
import { MarketService, MarketSymbol, QuoteResult } from '../core/market.service';
import { OrderDto, TradingService } from '../core/trading.service';
import { MarketExplorerComponent } from './market-explorer.component';

describe('Market order review', () => {
  const stock: MarketSymbol = {
    instrumentId: 7, symbol: 'AAPL', name: 'Apple Inc.',
    type: 'equity', exchange: 'NASDAQ', currency: 'USD',
  };
  const quote: QuoteResult = {
    symbol: 'AAPL', source: 'fauxnance', stale: false, error: null,
    quote: {
      symbol: 'AAPL', price: 101, bid: 100, ask: 102,
      spreadBps: 200, currency: 'USD', change: null,
      changePercent: null, previousClose: null,
      asOf: '2026-10-14T18:00:00Z', marketState: 'OPEN',
    },
  };
  const order: OrderDto = {
    accountId: 42, orderId: 5, symbol: 'AAPL', side: 'BUY',
    quantity: 2, indicativePrice: 102, submittedAt: '2026-10-16T11:00:00Z',
    status: 'FILLED', reason: null,
  };

  let trading: {
    cash: ReturnType<typeof signal<number>>;
    positions: ReturnType<typeof signal<{ instrumentId: number; sym: string; name: string; qty: number; reserved: number; available: number }[]>>;
    trade: ReturnType<typeof vi.fn>;
    getOrder: ReturnType<typeof vi.fn>;
    refresh: ReturnType<typeof vi.fn>;
    money: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
      configurable: true,
      value: function (this: HTMLDialogElement) { this.setAttribute('open', ''); },
    });
    Object.defineProperty(HTMLDialogElement.prototype, 'close', {
      configurable: true,
      value: function (this: HTMLDialogElement) { this.removeAttribute('open'); },
    });
    trading = {
      cash: signal(1000),
      positions: signal([{ instrumentId: 7, sym: 'AAPL', name: 'Apple Inc.', qty: 3, reserved: 0, available: 3 }]),
      trade: vi.fn().mockResolvedValue({ order }),
      getOrder: vi.fn().mockReturnValue(of(order)),
      refresh: vi.fn().mockResolvedValue(undefined),
      money: vi.fn((value: number) => `$${value.toFixed(2)}`),
    };
    const market = {
      catalogue: signal([stock]), cachedQuotes: signal({}), pins: signal<string[]>([]),
      maxPins: 25, pinnedSymbols: signal<MarketSymbol[]>([]),
      loadCatalogue: vi.fn().mockResolvedValue(undefined),
      quote: vi.fn().mockResolvedValue(quote),
      pin: vi.fn(), unpin: vi.fn(), errorMessage: vi.fn(),
    };
    const account = { selectedAccount: signal({ accountId: 42, accountName: 'Trading account' }) };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: MarketService, useValue: market },
        { provide: TradingService, useValue: trading },
        { provide: AccountSelectionService, useValue: account },
      ],
    });
  });

  function start() {
    const fixture = TestBed.createComponent(MarketExplorerComponent);
    const component = fixture.componentInstance;
    component.selected.set(stock);
    component.result.set(quote);
    component.loadingCatalogue.set(false);
    component.quantity.set('2');
    fixture.detectChanges();
    return { fixture, component };
  }

  it('reviews the ask and estimate without submitting, then switches the same dialog to status', async () => {
    const { fixture, component } = start();
    const buy = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
      .find(button => button.textContent?.trim() === 'Review buy')!;
    buy.click();
    fixture.detectChanges();
    const dialog = fixture.nativeElement.querySelector('dialog') as HTMLDialogElement;
    expect(dialog.open).toBe(true);
    expect(dialog.textContent).toContain('Apple Inc.');
    expect(dialog.textContent).toContain('Buy at ask');
    expect(dialog.textContent).toContain('$102.00');
    expect(dialog.textContent).toContain('$204.00');
    expect(trading.trade).not.toHaveBeenCalled();

    const confirm = Array.from(dialog.querySelectorAll('button'))
      .find(button => button.textContent?.trim() === 'Confirm buy')!;
    confirm.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('dialog')).toBe(dialog);
    expect(dialog.textContent).toContain('Submitting your order');
    expect(trading.trade).toHaveBeenCalledExactlyOnceWith(
      'buy', 7, '2', 102, '2026-10-14T18:00:00Z', 'USD');
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('dialog')).toBe(dialog);
    expect(dialog.textContent).toContain('Order filled');
    expect(dialog.open).toBe(true);
    expect(component.submittedOrder()?.orderId).toBe(5);
  });

  it('reviews the bid for a sale and cancel does not submit', () => {
    const { fixture, component } = start();
    component.reviewTrade('sell');
    fixture.detectChanges();
    const dialog = fixture.nativeElement.querySelector('dialog') as HTMLDialogElement;
    expect(dialog.textContent).toContain('Sell at bid');
    expect(dialog.textContent).toContain('$100.00');
    expect(dialog.textContent).toContain('$200.00');
    expect(dialog.textContent).toContain('Shares available');
    (Array.from(dialog.querySelectorAll('button'))
      .find(button => button.textContent?.trim() === 'Cancel')!).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('dialog')).toBeNull();
    expect(trading.trade).not.toHaveBeenCalled();
  });
});
