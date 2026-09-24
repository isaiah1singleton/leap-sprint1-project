import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-signin',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <div class="auth-card">
        <h1 class="auth-title">Sign in</h1>
        <p class="auth-sub">Access your investor dashboard.</p>
        @if (returnTo) {
          <p class="auth-sub">Sign in to continue to {{ returnTo }}.</p>
        }

        @if (error) {
          <p class="error-banner">{{ error }}</p>
        }

        <label class="label" for="email">Email</label>
        <input id="email" class="input" type="email" [class.invalid]="invalid('email')"
               [(ngModel)]="email" (keyup.enter)="submit()" placeholder="you@example.com" />

        <label class="label" for="password">Password</label>
        <input id="password" type="password" class="input" [class.invalid]="invalid('password')"
               [(ngModel)]="password" (keyup.enter)="submit()" placeholder="Your password" />

        <button class="btn btn-primary" (click)="submit()">Sign in</button>

        <p class="auth-foot">No account yet? <a routerLink="/register">Create one</a></p>
        @if (auth.mockAuth) {
          <p class="prototype-note">Frontend demo sign in<br />
            Email: <strong>{{ auth.demoEmail }}</strong><br />
            Password: <strong>{{ auth.demoPassword }}</strong>
          </p>
        }
      </div>
    </div>
  `,
})
export class SigninComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  readonly returnTo = this.destinationLabel(this.route.snapshot.queryParamMap.get('returnUrl'));

  email = '';
  password = '';
  error = '';
  private errorFields: string[] = [];

  invalid(field: string): boolean {
    return this.errorFields.includes(field);
  }

  async submit(): Promise<void> {
    const failure = await this.auth.signIn(this.email, this.password);
    if (failure) {
      this.error = failure.message;
      this.errorFields = failure.fields;
      this.password = '';
      return;
    }
    this.error = '';
    this.errorFields = [];
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    void this.router.navigateByUrl(returnUrl?.startsWith('/app/') ? returnUrl : '/app/overview');
  }

  private destinationLabel(url: string | null): string | null {
    const labels: Record<string, string> = {
      '/app/overview': 'Dashboard',
      '/app/portfolio': 'Portfolio',
      '/app/transact': 'Transact',
      '/app/orders': 'Order history',
      '/app/markets': 'Markets',
      '/app/account': 'Account details',
    };
    return labels[url?.split('?')[0] ?? ''] ?? null;
  }
}
