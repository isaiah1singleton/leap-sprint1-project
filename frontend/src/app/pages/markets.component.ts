import { Component, inject } from '@angular/core';
import { TradingService } from '../core/trading.service';

@Component({
  selector: 'app-markets',
  template: `
    <div class="row">
      <div class="card" style="flex:1">
        <h2 class="card-title">Indices, FX and commodities</h2>
        @for (row of trading.markets; track row.name) {
          <div class="list-row">
            <span>{{ row.name }}</span>
            <span style="display:flex;gap:14px"><span>{{ row.level }}</span><span class="dim" style="min-width:58px;text-align:right">{{ row.change }}</span></span>
          </div>
        }
      </div>
      <div class="card" style="width:250px;flex:none">
        <h2 class="card-title">Session</h2>
        <p style="font-size:13px;color:var(--ink-soft);line-height:1.7;margin:0">Status — Open<br />Close — 21:00 UTC<br />Data — delayed 15 min</p>
        <p style="border-top:1px solid var(--line-soft);padding-top:12px;margin-top:12px;font-size:12px;color:var(--muted);line-height:1.5">Upgrade to real-time data from Account details.</p>
      </div>
    </div>
  `,
})
export class MarketsComponent {
  readonly trading = inject(TradingService);
}
