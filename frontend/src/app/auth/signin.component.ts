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
