import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { ThemeToggleComponent } from '../core/theme-toggle.component';

@Component({
  selector: 'app-signin',
  imports: [FormsModule, RouterLink, ThemeToggleComponent],
  templateUrl: './signin.component.html',
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
    const fields: string[] = [];

    if (!this.email.trim()) fields.push('email');
    if (!this.password) fields.push('password');

    if (fields.length) {
      this.error = 'Enter your email and password.';
      this.errorFields = fields;
      return;
    }

    const failure = await this.auth.signIn(this.email, this.password);

    if (failure) {
      this.error = failure.message;
      this.errorFields = failure.fields;
      return;
    }

    this.error = '';
    this.errorFields = [];
    this.password = '';

    if (this.auth.isAdmin()) {
      await this.router.navigate(['/app/admin-audit']);
      return;
    }

    if (this.auth.isAnalyst()) {
      this.auth.redirectAnalystToSuperset();
      return;
    }

    await this.router.navigate(['/app/overview']);
  }
}
