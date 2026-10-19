import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { ClientThemeService } from '../core/client-theme.service';

@Component({
  selector: 'app-signin',
  imports: [FormsModule, RouterLink],
  templateUrl: './signin.component.html',
})
export class SigninComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly clientTheme = inject(ClientThemeService);

  email = '';
  password = '';
  error = '';
  mode: 'client' | 'admin' = 'client';
  private errorFields: string[] = [];

  constructor() {
    this.clientTheme.enable();
  }

  setMode(mode: 'client' | 'admin'): void {
    this.mode = mode;
    if (mode === 'admin') {
      this.clientTheme.disable();
    } else {
      this.clientTheme.enable();
    }
  }

  invalid(field: string): boolean {
    return this.errorFields.includes(field);
  }

  async submit(): Promise<void> {
    const fields: string[] = [];
    if (!this.email.trim()) fields.push('email');
    if (!this.password) fields.push('password');
    if (fields.length) {
      this.error = 'Enter your email and password.';
      this.errorFields = fields;
      return;
    }

    const failure = this.mode === 'admin'
      ? await this.auth.signInAdmin(this.email, this.password)
      : await this.auth.signIn(this.email, this.password);
    if (failure) {
      this.error = failure.message;
      this.errorFields = failure.fields;
      return;
    }

    this.error = '';
    this.errorFields = [];
    this.password = '';
    void this.router.navigate([this.mode === 'admin' ? '/app/admin-audit' : '/app/overview']);
  }
}
