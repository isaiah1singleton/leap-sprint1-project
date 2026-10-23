import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { ThemeToggleComponent } from '../core/theme-toggle.component';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink, ThemeToggleComponent],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css',
})
export class RegisterComponent {
  private readonly auth = inject(AuthService);
  name = '';
  email = '';
  password = '';
  confirm = '';
  error = '';
  registered = false;
  private errorFields: string[] = [];

  invalid(field: string): boolean {
    return this.errorFields.includes(field);
  }

  async submit(): Promise<void> {
    const failure = await this.auth.register(this.name, this.email, this.password, this.confirm);
    if (failure) {
      this.error = failure.message;
      this.errorFields = failure.fields;
      return;
    }
    this.error = '';
    this.errorFields = [];
    this.registered = true;
  }
}
