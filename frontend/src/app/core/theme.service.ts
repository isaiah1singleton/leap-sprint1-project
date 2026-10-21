import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';

type Theme = 'light' | 'dark';
const STORAGE_KEY = 'tr8ders:theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly window = this.document.defaultView;
  private readonly systemTheme = this.window?.matchMedia?.('(prefers-color-scheme: dark)');
  private preference = this.readPreference();
  readonly theme = signal<Theme>(this.preference ?? this.deviceTheme());
  readonly isDark = computed(() => this.theme() === 'dark');

  constructor() {
    this.apply(this.theme());
    const onSystemChange = () => {
      if (this.preference === null) this.apply(this.deviceTheme());
    };
    const onStorageChange = (event: StorageEvent) => {
      if (event.key !== STORAGE_KEY && event.key !== null) return;
      this.preference = this.readPreference();
      this.apply(this.preference ?? this.deviceTheme());
    };
    this.systemTheme?.addEventListener('change', onSystemChange);
    this.window?.addEventListener('storage', onStorageChange);
    inject(DestroyRef).onDestroy(() => {
      this.systemTheme?.removeEventListener('change', onSystemChange);
      this.window?.removeEventListener('storage', onStorageChange);
    });
  }

  toggle(): void {
    this.preference = this.isDark() ? 'light' : 'dark';
    this.apply(this.preference);
    try {
      this.window?.localStorage.setItem(STORAGE_KEY, this.preference);
    } catch { /* The theme still works when browser storage is unavailable. */ }
  }

  private readPreference(): Theme | null {
    try {
      const value = this.window?.localStorage.getItem(STORAGE_KEY);
      return value === 'light' || value === 'dark' ? value : null;
    } catch { return null; }
  }

  private deviceTheme(): Theme { return this.systemTheme?.matches ? 'dark' : 'light'; }

  private apply(theme: Theme): void {
    this.theme.set(theme);
    this.document.documentElement.dataset['theme'] = theme;
    this.document.documentElement.style.colorScheme = theme;
  }
}
