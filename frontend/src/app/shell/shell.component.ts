import { Component, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="shell">
      <nav class="sidebar">
        <div class="brand"><span class="brand-mark"></span> Inside Tr8ders</div>
        @for (item of navItems; track item.path) {
          <a class="nav-link" [routerLink]="item.path" routerLinkActive="active">{{ item.label }}</a>
        }
        <p class="sidebar-note">REGULAR USER — NO ADMIN TOOLS</p>
      </nav>

      <div class="main">
        <header class="topbar">
          <h1>{{ pageTitle() }}</h1>
          <div class="topbar-right">
            <span class="session-label">Signed in as</span>
            <span>{{ auth.currentUser() }}</span>
            <span class="avatar"></span>
            <button class="btn-chip" (click)="signOut()">Sign out</button>
          </div>
        </header>
        <main class="page"><router-outlet /></main>
      </div>
    </div>
  `,
})
export class ShellComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly title = inject(Title);

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

  signOut(): void {
    this.auth.signOut();
    void this.router.navigate(['/signin']);
  }
}
