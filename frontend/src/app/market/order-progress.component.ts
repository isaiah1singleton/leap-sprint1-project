import { CommonModule } from '@angular/common';
import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, ViewChild, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription, catchError, exhaustMap, of, takeWhile, timeout, timer } from 'rxjs';
import { OrderDto, TradeMode, TradingService } from '../core/trading.service';

export interface OrderReview {
  mode: TradeMode;
  accountId: number;
  accountName: string;
  instrumentId: number;
  symbol: string;
  name: string;
  type: string;
  exchange: string;
  currency: string;
  quantity: number;
  quantityInput: string;
  price: number;
  estimatedTotal: number;
  quoteAsOf: string;
  source: string | null;
  stale: boolean;
  cashBalance: number;
  availableShares: number;
}

export function isFinalOrder(order: OrderDto): boolean {
  return ['FILLED', 'REJECTED', 'EXPIRED'].includes(order.status);
}

@Component({
  selector: 'app-order-progress',
  standalone: true,
  imports: [CommonModule],
  template: `
    <dialog #dialog aria-labelledby="order-progress-title" (cancel)="dismiss($event)">
      <h2 id="order-progress-title">{{ title() }}</h2>
      @if (isReview() && review; as details) {
        <p class="intro">Check this indicative order before submitting it.</p>
        <div class="instrument"><strong>{{ details.symbol }}</strong><span>{{ details.name }}</span></div>
        <dl class="review-grid">
          <div><dt>Action</dt><dd>{{ details.mode === 'buy' ? 'Buy' : 'Sell' }}</dd></div>
          <div><dt>Instrument type</dt><dd>{{ details.type | titlecase }}</dd></div>
          <div><dt>Market</dt><dd>{{ details.exchange }}</dd></div>
          <div><dt>Account</dt><dd>{{ details.accountName }}</dd></div>
          <div><dt>{{ details.mode === 'buy' ? 'Buy at ask' : 'Sell at bid' }}</dt><dd>{{ money(details.price, details.currency, true) }}</dd></div>
          <div><dt>Quantity</dt><dd>{{ details.quantity }}</dd></div>
          <div class="estimate"><dt>Estimated {{ details.mode === 'buy' ? 'cost' : 'proceeds' }}</dt><dd>{{ money(details.estimatedTotal, details.currency) }}</dd></div>
          <div><dt>{{ details.mode === 'buy' ? 'Cash balance' : 'Shares available' }}</dt><dd>{{ details.mode === 'buy' ? money(details.cashBalance, details.currency) : details.availableShares }}</dd></div>
        </dl>
        <p class="reference">Quote as of {{ details.quoteAsOf | date:'MMM d, y, HH:mm:ss':'UTC' }} UTC
          @if (details.source) { · {{ details.source }} }
        </p>
        <p class="note">The final execution price may differ from this estimate. Confirming submits the order; it does not guarantee a fill.</p>
        <div class="actions">
          <button type="button" class="btn btn-secondary" (click)="dismiss()">Cancel</button>
          <button type="button" class="btn btn-primary" (click)="confirmed.emit()">Confirm {{ details.mode }}</button>
        </div>
      } @else {
      <div aria-live="polite" aria-atomic="true">
        @if (current(); as order) {
          <p>{{ order.side }} {{ order.quantity }} {{ order.symbol }}</p>
          <p class="reference">Reference ORD-{{ order.orderId }}</p>
          <div class="progress-track" aria-hidden="true">
            <div class="progress-fill" [class.pending]="!isFinalOrder(order)"
              [class.rejected]="order.status === 'REJECTED'"
              [class.expired]="order.status === 'EXPIRED'"
              [style.transform]="'scaleX(' + progress() / 100 + ')'"
              [style.transition-duration]="progressDuration()"></div>
          </div>
          @if (order.status === 'REJECTED') {
            <p class="decision-message rejected-message" role="alert"><strong>Order rejected.</strong> {{ order.reason || 'The order did not execute.' }}</p>
          } @else if (order.status === 'EXPIRED') {
            <p class="decision-message expired-message" role="status"><strong>Order expired.</strong> {{ order.reason || 'The order did not execute.' }}</p>
          }
          <ol aria-label="Order progress">
            <li>Order submitted</li>
            <li>{{ order.status === 'SUBMITTED' ? 'Awaiting acceptance' : order.status === 'ACCEPTED' ? 'Accepted — awaiting execution' : 'Final status: ' + order.status }}</li>
          </ol>
          @if (order.status === 'FILLED') {
            <p class="success">Your order was filled. Your account is being updated.</p>
          } @else if (!isFinalOrder(order)) {
            <p>Checking execution status automatically. No page refresh is needed.</p>
          }
        } @else if (!submissionError) {
          <p>Submitting your order…</p>
          <div class="progress-track" aria-hidden="true">
            <div class="progress-fill pending" [style.transform]="'scaleX(' + progress() / 100 + ')'"
              [style.transition-duration]="progressDuration()"></div>
          </div>
        }
      </div>
      @if (submissionError) { <p role="alert">{{ submissionError }}</p> }
      @if (pollError()) { <p role="alert">{{ pollError() }}</p> }
      @if (current() && !isFinalOrder(current()!)) {
        <p class="reference">Closing this window does not cancel the order. You can check it on the Orders page.</p>
      }
      <button type="button" class="btn btn-primary" [disabled]="submitting" (click)="dismiss()">{{ current() && isFinalOrder(current()!) ? 'Done' : 'Close' }}</button>
      }
    </dialog>
  `,
  styles: [`
    dialog { width: min(500px, calc(100vw - 32px)); max-height: min(88vh, 780px); overflow: auto; border: 1px solid var(--line); border-radius: 14px; padding: 24px; background: var(--surface); color: var(--ink); box-shadow: 0 24px 80px #06172166; }
    dialog::backdrop { background: #071b29a6; backdrop-filter: blur(7px); -webkit-backdrop-filter: blur(7px); }
    h2 { margin-top: 0; font-size: 22px; }
    p, li { line-height: 1.6; }
    .intro { color: var(--muted); margin-top: -4px; }
    .instrument { display: flex; flex-wrap: wrap; align-items: baseline; gap: 8px; margin: 20px 0 16px; }
    .instrument strong { font-size: 25px; }
    .instrument span { color: var(--muted); }
    .review-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 1px; overflow: hidden; border: 1px solid var(--line); border-radius: 9px; background: var(--line); margin: 0; }
    .review-grid > div { background: var(--surface); padding: 10px 13px; min-width: 0; }
    .review-grid dt { color: var(--muted); font-size: 11px; }
    .review-grid dd { margin: 4px 0 0; font-size: 14px; font-weight: 650; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
    .review-grid .estimate { grid-column: 1 / -1; display: flex; justify-content: space-between; align-items: baseline; background: var(--accent-soft); gap: 12px; }
    .review-grid .estimate dt { color: var(--ink-soft); font-size: 13px; }
    .review-grid .estimate dd { font-size: 22px; }
    .note { font-size: 12px; color: var(--muted); margin: 14px 0 0; }
    .reference { color: var(--muted); font-size: 13px; }
    .success { color: var(--gain); }
    .progress-track { height: 12px; margin: 20px 0 16px; overflow: hidden; border-radius: 999px; background: var(--line-soft); }
    .progress-fill { position: relative; height: 100%; width: 100%; overflow: hidden; border-radius: inherit; background: var(--accent); transform-origin: left center; transition-property: transform, background-color; transition-timing-function: linear; will-change: transform; }
    .progress-fill.pending::after { content: ''; position: absolute; inset: 0; background: linear-gradient(90deg, transparent 0%, #ffffff77 50%, transparent 100%); transform: translateX(-100%); animation: progress-sheen 1.8s linear infinite; }
    .progress-fill.rejected { background: var(--danger); }
    .progress-fill.expired { background: #a86b16; }
    .decision-message { margin: 0 0 16px; padding: 12px 14px; border-radius: 8px; line-height: 1.5; }
    .rejected-message { color: var(--danger); background: var(--danger-tint); border: 1px solid var(--danger-line); }
    .expired-message { color: var(--warning-ink); background: var(--warning-surface); border: 1px solid var(--warning-line); }
    @keyframes progress-sheen { to { transform: translateX(100%); } }
    @media (prefers-reduced-motion: reduce) { .progress-fill.pending::after { animation: none; } }
    .actions { display: flex; gap: 10px; margin-top: 22px; }
    .actions button { width: 50%; margin: 0; }
    dialog > button { margin-top: 12px; }
    @media (max-width: 480px) { dialog { padding: 20px; } .review-grid .estimate dd { font-size: 18px; } }
  `],
})
export class OrderProgressComponent implements OnChanges, AfterViewInit, OnDestroy {
  @Input() review: OrderReview | null = null;
  @Input() submitting = false;
  @Input() order: OrderDto | null = null;
  @Input() submissionError = '';
  @Output() confirmed = new EventEmitter<void>();
  @Output() closed = new EventEmitter<void>();
  @ViewChild('dialog', { static: true }) dialog!: ElementRef<HTMLDialogElement>;
  readonly current = signal<OrderDto | null>(null);
  readonly pollError = signal('');
  readonly progress = signal(0);
  readonly progressDuration = signal('1s');
  readonly isFinalOrder = isFinalOrder;
  private readonly trading = inject(TradingService);
  private polling?: Subscription;
  private progressTimer?: ReturnType<typeof setTimeout>;
  private trackedId?: number;

  title(): string {
    if (this.isReview()) return `Review ${this.review!.mode} order`;
    if (this.submissionError) return 'Unable to confirm submission';
    const order = this.current();
    return order ? `Order ${order.status.toLowerCase()}` : 'Submitting order';
  }

  isReview(): boolean {
    return this.review !== null && !this.submitting && !this.current() && !this.submissionError;
  }

  money(value: number, currency: string, price = false): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency', currency, minimumFractionDigits: 2,
      maximumFractionDigits: price ? 8 : 2,
    }).format(value);
  }

  ngAfterViewInit(): void { this.dialog.nativeElement.showModal(); }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['submitting'] && this.submitting && !this.order) {
      this.animateProgress('SUBMITTING');
    }
    if (!this.order || this.order.orderId === this.trackedId) return;
    this.polling?.unsubscribe();
    this.trackedId = this.order.orderId;
    this.current.set(this.order);
    this.animateProgress(this.order.status);
    if (isFinalOrder(this.order)) return;

    const id = this.order.orderId;
    this.polling = timer(0, 2000).pipe(
      exhaustMap(() => this.trading.getOrder(id).pipe(
        timeout(10000),
        catchError((error: unknown) => {
          this.pollError.set(error instanceof HttpErrorResponse && error.status === 401
            ? 'Your session expired. Sign in again to check this order.'
            : 'Status is temporarily unavailable. Retrying automatically; do not submit the order again.');
          return of(null);
        }),
      )),
      takeWhile(order => order === null || !isFinalOrder(order), true),
    ).subscribe(order => {
      if (!order) return;
      this.pollError.set('');
      if (this.current()?.status !== order.status) this.animateProgress(order.status);
      this.current.set(order);
      if (isFinalOrder(order)) void this.trading.refresh(order.accountId);
    });
  }

  private animateProgress(status: OrderDto['status'] | 'SUBMITTING'): void {
    if (this.progressTimer) clearTimeout(this.progressTimer);
    const target = status === 'SUBMITTING' ? 14
      : status === 'SUBMITTED' ? 28
      : status === 'ACCEPTED' ? 88
      : status === 'FILLED' ? 100 : 50;
    if (status === 'ACCEPTED' && this.progress() < 20) this.progress.set(20);
    this.progressTimer = setTimeout(() => {
      this.progressDuration.set(status === 'ACCEPTED' ? '30s' : '1s');
      this.progress.set(target);
    }, 50);
  }

  dismiss(event?: Event): void {
    event?.preventDefault();
    if (this.submitting) return;
    this.polling?.unsubscribe();
    if (this.progressTimer) clearTimeout(this.progressTimer);
    this.dialog.nativeElement.close();
    this.closed.emit();
  }

  ngOnDestroy(): void {
    this.polling?.unsubscribe();
    if (this.progressTimer) clearTimeout(this.progressTimer);
  }
}
