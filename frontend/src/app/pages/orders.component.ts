import { Component, inject } from '@angular/core';
import { TradingService } from '../core/trading.service';

@Component({
  selector: 'app-orders',
  templateUrl: './orders.component.html',
})
export class OrdersComponent {
  readonly trading = inject(TradingService);
  cancelError = '';
  cancelling: number | null = null;

  async cancel(orderId: number): Promise<void> {
    this.cancelling = orderId;
    this.cancelError = await this.trading.cancel(orderId) ?? '';
    this.cancelling = null;
  }
}
