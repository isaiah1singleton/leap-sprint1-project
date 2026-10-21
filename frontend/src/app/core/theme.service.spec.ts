import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { ThemeService } from './theme.service';
import { ThemeToggleComponent } from './theme-toggle.component';

describe('Site theme', () => {
  const storageKey = 'tr8ders:theme';
  let media: { matches: boolean; addEventListener: ReturnType<typeof vi.fn>; removeEventListener: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    localStorage.removeItem(storageKey);
    media = { matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() };
    vi.stubGlobal('matchMedia', vi.fn(() => media));
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    localStorage.removeItem(storageKey);
    delete document.documentElement.dataset['theme'];
    document.documentElement.style.colorScheme = '';
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('uses a saved choice before the device theme and applies it to native controls', () => {
    localStorage.setItem(storageKey, 'dark');
    const theme = TestBed.inject(ThemeService);
    expect(theme.isDark()).toBe(true);
    expect(document.documentElement.dataset['theme']).toBe('dark');
    expect(document.documentElement.style.colorScheme).toBe('dark');
    theme.toggle();
    expect(localStorage.getItem(storageKey)).toBe('light');
    expect(document.documentElement.dataset['theme']).toBe('light');
  });

  it('follows device changes until the user makes an explicit choice', () => {
    const theme = TestBed.inject(ThemeService);
    const onChange = media.addEventListener.mock.calls[0][1];
    media.matches = true;
    onChange();
    expect(theme.isDark()).toBe(true);
    theme.toggle();
    onChange();
    expect(theme.isDark()).toBe(false);
  });

  it('syncs changes across tabs and returns to device preference when storage is cleared', () => {
    const theme = TestBed.inject(ThemeService);
    localStorage.setItem(storageKey, 'dark');
    window.dispatchEvent(new StorageEvent('storage', { key: storageKey, newValue: 'dark' }));
    expect(theme.isDark()).toBe(true);
    localStorage.removeItem(storageKey);
    window.dispatchEvent(new StorageEvent('storage', { key: storageKey }));
    expect(theme.isDark()).toBe(false);
  });

  it('still toggles when browser storage is blocked', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('Blocked'); });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('Blocked'); });
    const theme = TestBed.inject(ThemeService);
    expect(() => theme.toggle()).not.toThrow();
    expect(document.documentElement.dataset['theme']).toBe('dark');
  });

  it('exposes its current state to assistive technology and keeps multiple controls in sync', () => {
    const first = TestBed.createComponent(ThemeToggleComponent);
    const second = TestBed.createComponent(ThemeToggleComponent);
    first.detectChanges(); second.detectChanges();
    const button = first.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(button.getAttribute('aria-pressed')).toBe('false');
    button.click(); first.detectChanges(); second.detectChanges();
    expect(button.getAttribute('aria-pressed')).toBe('true');
    expect(second.nativeElement.textContent).toContain('Light mode');
    expect(button.title).toBe('Switch to light mode');
  });

  it('removes its device listener when destroyed', () => {
    TestBed.inject(ThemeService);
    const listener = media.addEventListener.mock.calls[0][1];
    TestBed.resetTestingModule();
    expect(media.removeEventListener).toHaveBeenCalledWith('change', listener);
  });
});
