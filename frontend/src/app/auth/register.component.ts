import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <div class="auth-card">
        <h1 class="auth-title">Create your account</h1>
        <p class="auth-sub">Start trading with a personal investor account.</p>

        <label class="label" for="username">Username</label>
        <input id="username" class="input" [class.invalid]="invalid('username')"
               [(ngModel)]="username" (keyup.enter)="submit()" placeholder="Choose a username" />

        <label class="label" for="password">Password</label>
        <input id="password" type="password" class="input" [class.invalid]="invalid('password')"
               [(ngModel)]="password" (keyup.enter)="submit()" placeholder="Create a password" />

        <label class="label" for="confirm">Confirm password</label>
        <input id="confirm" type="password" class="input" [class.invalid]="invalid('confirm')"
               [(ngModel)]="confirm" (keyup.enter)="submit()" placeholder="Repeat your password" />

        @if (error) {
          <p class="error-text">{{ error }}</p>
        }

        <button class="btn btn-primary" (click)="submit()">Create account</button>
        <p class="auth-foot">Already have an account? <a routerLink="/signin">Sign in</a></p>
        <p class="prototype-note">
          PROTOTYPE — password needs 8+ characters and a number, and both fields must match.
          Valid input signs you straight in.
        </p>
      </div>
    </div>
  `,
})
export class RegisterComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  username = '';
  password = '';
  confirm = '';
  error = '';
  private errorFields: string[] = [];

  invalid(field: string): boolean {
    return this.errorFields.includes(field);
  }

  submit(): void {
    const failure = this.auth.register(this.username, this.password, this.confirm);
    if (failure) {
      this.error = failure.message;
      this.errorFields = failure.fields;
      return;
    }
    this.error = '';
    this.errorFields = [];
    void this.router.navigate(['/app/overview']);
  }
}
