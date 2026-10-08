import { Component } from '@angular/core';
import { MarketExplorerComponent } from '../market/market-explorer.component';

@Component({
  selector: 'app-markets',
  imports: [MarketExplorerComponent],
  templateUrl: './markets.component.html',
})
export class MarketsComponent {
}
