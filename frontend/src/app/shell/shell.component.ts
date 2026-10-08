import { Component, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { AccountsCacheService } from '../core/accounts-cache.service';
import { AccountSelectionService } from '../core/account-selection.service';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.component.html',
})
export class ShellComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly title = inject(Title);
  private readonly accountsCache = inject(AccountsCacheService);
  private readonly accountSelection = inject(AccountSelectionService);

  readonly navItems = [
    { path: '/app/overview', label: 'Dashboard' },
    { path: '/app/portfolio', label: 'Portfolio' },
    { path: '/app/transact', label: 'Transact' },
    { path: '/app/orders', label: 'Order history' },
    { path: '/app/markets', label: 'Markets' },
    { path: '/app/account', label: 'Account details' },
  ];

  pageTitle(): string {
    return this.title.getTitle() || 'Dashboard';
  }

  async signOut(): Promise<void> {
    await this.auth.signOut();
    this.accountsCache.clearCache();
    this.accountSelection.clearSelectedAccount();
    void this.router.navigate(['/signin']);
  }
}
