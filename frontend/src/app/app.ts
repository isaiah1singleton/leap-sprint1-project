import { Component, inject } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { ClientThemeService } from './core/client-theme.service';

@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  template: '<router-outlet />',
})
export class App {
  private readonly router = inject(Router);
  private readonly clientTheme = inject(ClientThemeService);

  constructor() {
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => {
        if (event.urlAfterRedirects === '/app/admin-audit') {
          this.clientTheme.disable();
        } else {
          this.clientTheme.enable();
        }
      });
  }
}
