import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export interface AuthFailure {
  message: string;
  fields: string[];
}

interface AuthResponse {
  clientId: number;
  email: string;
  clientSegment: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = 'http://localhost:8080/api/auth';
  readonly currentUser = signal<string | null>(sessionStorage.getItem('currentUserEmail'));

  isSignedIn(): boolean {
    return this.currentUser() !== null;
  }

  async signIn(email: string, password: string): Promise<AuthFailure | null> {
    const userEmail = email.trim();
    const fields: string[] = [];
    if (!userEmail) fields.push('email');
    if (!password) fields.push('password');
    if (fields.length) return { message: 'Enter your email and password.', fields };

    try {
      const response = await firstValueFrom(
        this.http.post<AuthResponse>(`${this.apiUrl}/sign-in`, { email: userEmail, password }),
      );
      this.setCurrentUser(response.email);
      return null;
    } catch (error) {
      return this.apiFailure(error, ['email', 'password']);
    }
  }

  async register(email: string, password: string, confirm: string): Promise<AuthFailure | null> {
    const userEmail = email.trim();
    if (!userEmail) return { message: 'Enter your email.', fields: ['email'] };
    if (password.length < 8 || !/\d/.test(password) || password !== confirm) {
      return {
        message: 'Passwords must match and include 8+ characters with a number.',
        fields: ['password', 'confirm'],
      };
    }
    try {
      const response = await firstValueFrom(
        this.http.post<AuthResponse>(`${this.apiUrl}/register`, { email: userEmail, password }),
      );
      this.setCurrentUser(response.email);
      return null;
    } catch (error) {
      return this.apiFailure(error, ['email']);
    }
  }

  signOut(): void {
    sessionStorage.removeItem('currentUserEmail');
    this.currentUser.set(null);
  }

  private setCurrentUser(email: string): void {
    sessionStorage.setItem('currentUserEmail', email);
    this.currentUser.set(email);
  }

  private apiFailure(error: unknown, fields: string[]): AuthFailure {
    if (error instanceof HttpErrorResponse && typeof error.error === 'string') {
      return { message: error.error, fields };
    }
    return { message: 'Unable to reach the authentication service. Please try again.', fields };
  }
}
