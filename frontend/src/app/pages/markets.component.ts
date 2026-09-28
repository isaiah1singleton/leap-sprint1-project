import { Component, inject } from '@angular/core';
import { TradingService } from '../core/trading.service';

@Component({
  selector: 'app-markets',
  templateUrl: './markets.component.html',
})
    </div>
})
export class MarketsComponent {
  readonly trading = inject(TradingService);
}
