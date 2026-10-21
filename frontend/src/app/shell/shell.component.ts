import { Component, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { AccountsCacheService } from '../core/accounts-cache.service';
import { AccountSelectionService } from '../core/account-selection.service';
import { ThemeToggleComponent } from '../core/theme-toggle.component';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ThemeToggleComponent],
  templateUrl: './shell.component.html',
})
export class ShellComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly title = inject(Title);
  private readonly accountsCache = inject(AccountsCacheService);
  readonly accountSelection = inject(AccountSelectionService);
  readonly sidebarCollapsed = signal(this.initialSidebarState());

  toggleSidebar(): void {
    this.sidebarCollapsed.update(collapsed => !collapsed);
    try {
      localStorage.setItem('tr8ders:sidebar-collapsed', String(this.sidebarCollapsed()));
    } catch { /* Navigation remains usable when storage is unavailable. */ }
  }

  private initialSidebarState(): boolean {
    try {
      const saved = localStorage.getItem('tr8ders:sidebar-collapsed');
      if (saved !== null) return saved === 'true';
    } catch { /* Fall back to the viewport default. */ }
    return window.matchMedia('(max-width: 640px)').matches;
  }

  closeMobileNavigation(): void {
    if (window.matchMedia('(max-width: 640px)').matches) {
      this.sidebarCollapsed.set(true);
    }
  }

  readonly navItems = [
    { path: '/app/overview', label: 'Dashboard', icon: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z' },
    { path: '/app/portfolio', label: 'Portfolio', icon: 'M4 7h16v13H4z M8 7V4h8v3 M4 12h16 M10 12v3h4v-3' },
    { path: '/app/markets', label: 'Markets', icon: 'M3 3v18h18 M7 15l4-5 4 3 6-8' },
    { path: '/app/transact', label: 'Transact', icon: 'M4 7h16 M16 3l4 4-4 4 M20 17H4 M8 13l-4 4 4 4' },
    { path: '/app/orders', label: 'Order history', icon: 'M6 3h12v18H6z M9 7h6 M9 11h6 M9 15h4' },
    { path: '/app/cash-activity', label: 'Cash activity', icon: 'M3 6h18v12H3z M3 10h18 M7 15h4' },
    { path: '/app/account', label: 'Account details', icon: 'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M4 21v-2a8 8 0 0 1 16 0v2' },
  ];

  readonly adminNavItems = [
    { path: '/app/admin-audit', label: 'Admin audit' },
  ];

  pageTitle(): string {
    return this.title.getTitle() || 'Dashboard';
  }

  signOut(): void {
    this.auth.signOut();
    this.accountsCache.clearCache();
    this.accountSelection.clearSelectedAccount();
    void this.router.navigate(['/signin']);
  }
}
