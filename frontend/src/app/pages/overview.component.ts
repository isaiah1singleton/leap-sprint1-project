import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TradingService } from '../core/trading.service';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-overview',
  imports: [RouterLink],
  templateUrl: './overview.component.html',
})
export class OverviewComponent {
  readonly auth = inject(AuthService);
  readonly trading = inject(TradingService);
  readonly dayChange = '+$1,284.22 (+0.41%)';
  readonly recent = computed(() => this.trading.orders().slice(0, 4));
}
