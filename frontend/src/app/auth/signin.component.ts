import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-signin',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <div class="auth-card">
        <h1 class="auth-title">Sign in</h1>
        <p class="auth-sub">Access your investor dashboard.</p>

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
      </div>
    </div>
  `,
})
export class SigninComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

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
    void this.router.navigate(['/app/overview']);
  }
}
