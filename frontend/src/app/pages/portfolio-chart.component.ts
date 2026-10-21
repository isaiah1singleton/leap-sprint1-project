import { Component, computed, input, signal } from '@angular/core';
import { PortfolioHistoryPoint } from './portfolio-history.model';

@Component({
  selector: 'app-portfolio-chart',
  templateUrl: './portfolio-chart.component.html',
  styleUrl: './portfolio-chart.component.css',
})
export class PortfolioChartComponent {
  readonly points = input.required<PortfolioHistoryPoint[]>();
  readonly metric = input.required<'portfolioValue' | 'lifetimeProfitLoss'>();
  readonly title = input.required<string>();
  readonly selected = signal(-1);
  readonly valid = computed(() => this.points().filter(point => this.value(point) != null));
  readonly current = computed(() => this.points()[this.selected()] ?? this.points().at(-1));
  readonly color = computed(() => this.metric() === 'portfolioValue' ? 'var(--chart-portfolio)'
    : (this.value(this.points().at(-1)) ?? 0) < 0 ? 'var(--chart-loss)' : 'var(--chart-profit)');
  readonly domain = computed(() => {
    const values = this.valid().map(point => this.value(point)!);
    const low = values.length ? values.reduce((a, b) => Math.min(a, b)) : 0;
    const high = values.length ? values.reduce((a, b) => Math.max(a, b)) : 1;
    const padding = Math.max((high - low) * .15, Math.abs(high) * .005, 1);
    return { low: low - padding, high: high + padding };
  });
  readonly ticks = computed(() => [0, 1, 2, 3].map(index => {
    const { low, high } = this.domain();
    return { y: 24 + index * 58, value: high - index * (high - low) / 3 };
  }));
  readonly paths = computed(() => {
    const segments: { line: string; area: string; startX: number; startY: number; single: boolean }[] = [];
    let segment: { x: number; y: number }[] = [];
    const flush = () => {
      if (!segment.length) return;
      const line = segment.map((point, index) => `${index ? 'L' : 'M'}${point.x} ${point.y}`).join(' ');
      segments.push({ line, area: `${line} L${segment.at(-1)!.x} 198 L${segment[0].x} 198 Z`,
        startX: segment[0].x, startY: segment[0].y, single: segment.length === 1 });
      segment = [];
    };
    for (const point of this.points()) {
      if (this.value(point) == null) {
        flush();
      } else {
        segment.push({ x: this.x(point), y: this.y(this.value(point)!) });
      }
    }
    flush();
    return segments;
  });
  readonly dateTicks = computed(() => {
    const points = this.points();
    if (!points.length) return [];
    const indices = [...new Set([0, Math.floor((points.length - 1) / 2), points.length - 1])];
    const sameDay = points[0].observedAt.slice(0, 10) === points.at(-1)!.observedAt.slice(0, 10);
    return indices.map(index => ({ x: this.x(points[index]), label: new Intl.DateTimeFormat('en-GB',
      sameDay ? { hour: '2-digit', minute: '2-digit', timeZone: 'UTC' }
        : { month: 'short', day: 'numeric', timeZone: 'UTC' }).format(new Date(points[index].observedAt)) }));
  });

  value(point: PortfolioHistoryPoint | undefined): number | null {
    const value = point?.[this.metric()];
    return value == null || !Number.isFinite(Number(value)) ? null : Number(value);
  }

  x(point: PortfolioHistoryPoint): number {
    const first = Date.parse(this.points()[0].observedAt);
    const last = Date.parse(this.points().at(-1)!.observedAt);
    return first === last ? 320 : 68 + (Date.parse(point.observedAt) - first) / (last - first) * 504;
  }

  y(value: number): number {
    const { low, high } = this.domain();
    return 24 + (high - value) / (high - low) * 174;
  }

  inspect(event: PointerEvent): void {
    const svg = event.currentTarget as SVGSVGElement;
    const rect = svg.getBoundingClientRect();
    const target = (event.clientX - rect.left) / rect.width * 600;
    let closest = -1;
    let distance = Infinity;
    this.points().forEach((point, index) => {
      const next = Math.abs(this.x(point) - target);
      if (next < distance) { distance = next; closest = index; }
    });
    this.selected.set(closest);
  }

  navigate(event: KeyboardEvent): void {
    const last = this.points().length - 1;
    if (last < 0) return;
    const index = this.selected() < 0 ? last : this.selected();
    switch (event.key) {
      case 'ArrowLeft': this.selected.set(Math.max(0, index - 1)); break;
      case 'ArrowRight': this.selected.set(Math.min(last, index + 1)); break;
      case 'Home': this.selected.set(0); break;
      case 'End': this.selected.set(last); break;
      case 'Escape': this.selected.set(-1); break;
      default: return;
    }
    event.preventDefault();
  }

  money(value: number | null | undefined, compact = false): string {
    return value == null ? 'Unavailable' : new Intl.NumberFormat('en-US', {
      style: 'currency', currency: 'USD', notation: compact ? 'compact' : 'standard',
      maximumFractionDigits: compact ? 1 : 2,
    }).format(value);
  }

  date(value: string): string {
    return new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric',
      hour: '2-digit', minute: '2-digit', second: '2-digit', timeZone: 'UTC' }).format(new Date(value)) + ' UTC';
  }
}
