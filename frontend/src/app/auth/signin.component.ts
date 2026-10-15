import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-signin',
  imports: [FormsModule, RouterLink],
  templateUrl: './signin.component.html',
})
export class SigninComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  email = '';
  password = '';
  error = '';
  mode: 'client' | 'admin' = 'client';
  private errorFields: string[] = [];

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

    this.auth.beginLocalSession(this.email, this.mode === 'admin' ? 'ADMIN' : 'RETAIL');
    this.error = '';
    this.errorFields = [];
    this.password = '';
    void this.router.navigate([this.mode === 'admin' ? '/app/admin-audit' : '/app/overview']);
  }
}
