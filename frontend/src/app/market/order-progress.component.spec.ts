import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { OrderDto, TradingService } from '../core/trading.service';
import { OrderProgressComponent } from './order-progress.component';

describe('Order progress', () => {
  const accepted: OrderDto = {
    orderId: 5, accountId: 42, symbol: 'AAPL', side: 'BUY', quantity: 2,
    indicativePrice: 100, submittedAt: '2026-10-14T18:00:00Z',
    status: 'ACCEPTED', reason: null,
  };
  let service: { getOrder: ReturnType<typeof vi.fn>; refresh: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    // jsdom does not implement native dialog methods.
    Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
      configurable: true,
      value: function (this: HTMLDialogElement) { this.setAttribute('open', ''); },
    });
    Object.defineProperty(HTMLDialogElement.prototype, 'close', {
      configurable: true,
      value: function (this: HTMLDialogElement) { this.removeAttribute('open'); },
    });
    vi.useFakeTimers();
    service = { getOrder: vi.fn(), refresh: vi.fn().mockResolvedValue(undefined) };
    TestBed.configureTestingModule({ providers: [{ provide: TradingService, useValue: service }] });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.useRealTimers();
  });

  function start() {
    const fixture = TestBed.createComponent(OrderProgressComponent);
    const component = fixture.componentInstance;
    component.order = accepted;
    component.ngOnChanges();
    return { fixture, component };
  }

  for (const status of ['FILLED', 'REJECTED', 'EXPIRED'] as const) {
    it(`updates accepted to ${status}, refreshes the account, and stops polling`, async () => {
      service.getOrder.mockReturnValueOnce(of(accepted))
        .mockReturnValue(of({ ...accepted, status, reason: 'Decision reason' }));
      const { component } = start();
      await vi.advanceTimersByTimeAsync(0);
      expect(component.current()?.status).toBe('ACCEPTED');
      await vi.advanceTimersByTimeAsync(2000);
      expect(component.current()?.status).toBe(status);
      expect(service.refresh).toHaveBeenCalledWith(42);
      await vi.advanceTimersByTimeAsync(10000);
      expect(service.getOrder).toHaveBeenCalledTimes(2);
    });
  }

  it('retains accepted status on network failure and recovers on the next poll', async () => {
    service.getOrder.mockReturnValueOnce(throwError(() => new Error('Network unavailable')))
      .mockReturnValue(of({ ...accepted, status: 'FILLED' }));
    const { component } = start();
    await vi.advanceTimersByTimeAsync(0);
    expect(component.current()?.status).toBe('ACCEPTED');
    expect(component.pollError()).toContain('Retrying');
    await vi.advanceTimersByTimeAsync(2000);
    expect(component.current()?.status).toBe('FILLED');
    expect(component.pollError()).toBe('');
  });

  it('stops polling when the popup is destroyed', async () => {
    service.getOrder.mockReturnValue(of(accepted));
    const { fixture } = start();
    await vi.advanceTimersByTimeAsync(0);
    fixture.destroy();
    await vi.advanceTimersByTimeAsync(10000);
    expect(service.getOrder).toHaveBeenCalledTimes(1);
  });

  it('shows an immediate rejection without polling', () => {
    const fixture = TestBed.createComponent(OrderProgressComponent);
    const component = fixture.componentInstance;
    component.order = { ...accepted, status: 'REJECTED', reason: 'Insufficient cash' };
    component.ngOnChanges();
    expect(component.current()?.reason).toBe('Insufficient cash');
    expect(component.title()).toBe('Order rejected');
    expect(service.getOrder).not.toHaveBeenCalled();
  });

  it('closes the dialog and stops polling without cancelling the order', async () => {
    service.getOrder.mockReturnValue(of(accepted));
    const { fixture, component } = start();
    fixture.detectChanges();
    const closed = vi.fn();
    component.closed.subscribe(closed);
    await vi.advanceTimersByTimeAsync(0);
    component.dismiss();
    expect(closed).toHaveBeenCalledOnce();
    expect(component.dialog.nativeElement.open).toBe(false);
    await vi.advanceTimersByTimeAsync(10000);
    expect(service.getOrder).toHaveBeenCalledTimes(1);
  });
});
