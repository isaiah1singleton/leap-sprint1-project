import { Component, inject } from '@angular/core';
import { TradingService } from '../core/trading.service';

@Component({
  selector: 'app-portfolio',
  templateUrl: './portfolio.component.html',
})
export class PortfolioComponent {
  readonly trading = inject(TradingService);
}
