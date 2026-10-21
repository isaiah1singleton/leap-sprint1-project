import { Component, inject } from '@angular/core';
import { ThemeService } from './theme.service';

@Component({
  selector: 'app-theme-toggle',
  template: `
    <button type="button" class="theme-toggle" aria-label="Dark mode" [attr.aria-pressed]="theme.isDark()"
      [title]="theme.isDark() ? 'Switch to light mode' : 'Switch to dark mode'" (click)="theme.toggle()">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        @if (theme.isDark()) {
          <circle cx="12" cy="12" r="4" /><path d="M12 2v2 M12 20v2 M2 12h2 M20 12h2 M5 5l1.5 1.5 M17.5 17.5L19 19 M5 19l1.5-1.5 M17.5 6.5L19 5" />
        } @else {
          <path d="M20.6 13.1A9 9 0 0 1 10.9 3.4a9 9 0 1 0 9.7 9.7Z" />
        }
      </svg>
      <span>{{ theme.isDark() ? 'Light mode' : 'Dark mode' }}</span>
    </button>
  `,
  styles: [`
    :host { display:inline-flex; flex:none; }
    .theme-toggle { display:inline-flex; align-items:center; justify-content:center; gap:8px; min-height:40px; padding:8px 12px; border:1px solid var(--line); border-radius:8px; background:var(--surface); color:var(--ink); cursor:pointer; font:inherit; font-size:12px; white-space:nowrap; }
    .theme-toggle:hover { background:var(--accent-soft); border-color:var(--accent); }
    svg { width:18px; height:18px; flex:none; }
    @media (max-width:640px) { .theme-toggle { width:40px; padding:8px; } span { display:none; } }
    @media print { :host { display:none; } }
  `],
})
export class ThemeToggleComponent {
  readonly theme = inject(ThemeService);
}
