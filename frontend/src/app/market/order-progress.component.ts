import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnChanges, OnDestroy, Output, ViewChild, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription, catchError, exhaustMap, of, takeWhile, timeout, timer } from 'rxjs';
import { OrderDto, TradingService } from '../core/trading.service';

export function isFinalOrder(order: OrderDto): boolean {
  return ['FILLED', 'REJECTED', 'EXPIRED'].includes(order.status);
}

@Component({
  selector: 'app-order-progress',
  standalone: true,
  template: `
    <dialog #dialog aria-labelledby="order-progress-title" (cancel)="dismiss($event)">
      <h2 id="order-progress-title">{{ title() }}</h2>
      <div aria-live="polite" aria-atomic="true">
        @if (current(); as order) {
          <p>{{ order.side }} {{ order.quantity }} {{ order.symbol }}</p>
          <p class="reference">Reference ORD-{{ order.orderId }}</p>
          <ol aria-label="Order progress">
            <li>Order submitted</li>
            <li>{{ order.status === 'SUBMITTED' ? 'Awaiting acceptance' : order.status === 'ACCEPTED' ? 'Accepted — awaiting execution' : 'Final status: ' + order.status }}</li>
          </ol>
          @if (order.status === 'FILLED') {
            <p class="success">Your order was filled. Your account is being updated.</p>
          } @else if (order.status === 'REJECTED' || order.status === 'EXPIRED') {
            <p>{{ order.reason || 'The order did not execute.' }}</p>
          } @else {
            <p>Checking execution status automatically. No page refresh is needed.</p>
          }
        } @else if (!submissionError) {
          <p>Submitting your order…</p>
        }
      </div>
      @if (submissionError) { <p role="alert">{{ submissionError }}</p> }
      @if (pollError()) { <p role="alert">{{ pollError() }}</p> }
      @if (current() && !isFinalOrder(current()!)) {
        <p class="reference">Closing this window does not cancel the order. You can check it on the Orders page.</p>
      }
      <button type="button" class="btn btn-primary" autofocus (click)="dismiss()">{{ current() && isFinalOrder(current()!) ? 'Done' : 'Close' }}</button>
    </dialog>
  `,
  styles: [`
    dialog { width: min(440px, calc(100vw - 48px)); max-height: 80vh; overflow: auto; border: 1px solid var(--line); border-radius: 16px; padding: 24px; background: var(--surface); color: var(--ink); box-shadow: 0 16px 60px #0004; }
    dialog::backdrop { background: #0008; }
    h2 { margin-top: 0; font-size: 22px; }
    p, li { line-height: 1.6; }
    .reference { color: var(--muted); font-size: 13px; }
    .success { color: var(--gain); }
    button { margin-top: 12px; }
  `],
})
export class OrderProgressComponent implements OnChanges, AfterViewInit, OnDestroy {
  @Input() order: OrderDto | null = null;
  @Input() submissionError = '';
  @Output() closed = new EventEmitter<void>();
  @ViewChild('dialog', { static: true }) dialog!: ElementRef<HTMLDialogElement>;
  readonly current = signal<OrderDto | null>(null);
  readonly pollError = signal('');
  readonly isFinalOrder = isFinalOrder;
  private readonly trading = inject(TradingService);
  private polling?: Subscription;
  private trackedId?: number;

  title(): string {
    if (this.submissionError) return 'Unable to confirm submission';
    const order = this.current();
    return order ? `Order ${order.status.toLowerCase()}` : 'Submitting order';
  }

  ngAfterViewInit(): void { this.dialog.nativeElement.showModal(); }

  ngOnChanges(): void {
    if (!this.order || this.order.orderId === this.trackedId) return;
    this.polling?.unsubscribe();
    this.trackedId = this.order.orderId;
    this.current.set(this.order);
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
      this.current.set(order);
      if (isFinalOrder(order)) void this.trading.refresh(order.accountId);
    });
  }

  dismiss(event?: Event): void {
    event?.preventDefault();
    this.polling?.unsubscribe();
    this.dialog.nativeElement.close();
    this.closed.emit();
  }

  ngOnDestroy(): void { this.polling?.unsubscribe(); }
}
