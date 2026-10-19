import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export interface AuthFailure {
  message: string;
  fields: string[];
}

interface AuthResponse {
  clientId: number;
  clientName: string;
  email: string;
  clientSegment: string;
  accessToken: string;
  tokenType: string;
  expiresAt: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/auth';
  readonly currentUser = signal<string | null>(sessionStorage.getItem('currentUserName'));
  readonly currentUserEmail = signal<string | null>(sessionStorage.getItem('currentUserEmail'));
  readonly currentUserSegment = signal<string | null>(sessionStorage.getItem('currentUserSegment'));

  isSignedIn(): boolean {
    return this.currentUser() !== null && this.getAccessToken() !== null;
  }

  isAdmin(): boolean {
    return this.currentUserSegment()?.toUpperCase() === 'ADMIN';
  }

  getAccessToken(): string | null {
    return sessionStorage.getItem('accessToken');
  }

  beginLocalSession(email: string, segment: 'RETAIL' | 'ADMIN'): void {
    const normalizedEmail = email.trim().toLowerCase();
    const name = normalizedEmail.includes('@')
      ? normalizedEmail.split('@')[0]
      : normalizedEmail;
    const titleName = name
      .split(/[._-]/g)
      .filter(Boolean)
      .map(part => part.charAt(0).toUpperCase() + part.slice(1))
      .join(' ') || 'Local User';

    this.setCurrentUser(
      titleName,
      normalizedEmail,
      segment,
      `local-${segment.toLowerCase()}-${Date.now()}`,
    );
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
      this.setCurrentUser(response.clientName, response.email, response.clientSegment, response.accessToken);
      return null;
    } catch (error) {
      return this.apiFailure(error, ['email', 'password']);
    }
  }

  async signInAdmin(email: string, password: string): Promise<AuthFailure | null> {
    const userEmail = email.trim();
    const fields: string[] = [];
    if (!userEmail) fields.push('email');
    if (!password) fields.push('password');
    if (fields.length) return { message: 'Enter your admin email and password.', fields };

    try {
      const response = await firstValueFrom(
        this.http.post<AuthResponse>(`${this.apiUrl}/admin-sign-in`, { email: userEmail, password }),
      );
      this.setCurrentUser(response.clientName, response.email, response.clientSegment, response.accessToken);
      return null;
    } catch (error) {
      return this.apiFailure(error, ['email', 'password']);
    }
  }

  async register(
    name: string,
    email: string,
    password: string,
    confirm: string,
  ): Promise<AuthFailure | null> {
    const userName = name.trim();
    if (!userName) return { message: 'Enter your full name.', fields: ['name'] };
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
        this.http.post<AuthResponse>(`${this.apiUrl}/register`, {
          name: userName,
          email: userEmail,
          password,
        }),
      );
      this.setCurrentUser(response.clientName, response.email, response.clientSegment, response.accessToken);
      return null;
    } catch (error) {
      return this.apiFailure(error, ['name', 'email']);
    }
  }

  async signOut(): Promise<void> {
    sessionStorage.removeItem('currentUserName');
    sessionStorage.removeItem('currentUserEmail');
    sessionStorage.removeItem('currentUserSegment');
    sessionStorage.removeItem('accessToken');
    this.currentUser.set(null);
    this.currentUserEmail.set(null);
    this.currentUserSegment.set(null);
    return Promise.resolve();
  }

  private setCurrentUser(name: string, email: string, clientSegment: string, accessToken: string): void {
    sessionStorage.setItem('currentUserName', name);
    sessionStorage.setItem('currentUserEmail', email);
    sessionStorage.setItem('currentUserSegment', clientSegment);
    sessionStorage.setItem('accessToken', accessToken);
    this.currentUser.set(name);
    this.currentUserEmail.set(email);
    this.currentUserSegment.set(clientSegment);
  }

  private apiFailure(error: unknown, fields: string[]): AuthFailure {
    if (error instanceof HttpErrorResponse && error.status === 0) {
<<<<<<< HEAD
      return {
        message:
          'Cannot reach the backend. Check that the backend container is running and port 8082 is reachable.',
        fields: [],
      };
=======
      return { message: 'Cannot reach the backend. Check that the backend container is running and port 8082 is reachable.', fields: [] };
>>>>>>> feature/admin-kafka
    }
    if (error instanceof HttpErrorResponse && error.error?.detail) {
      return { message: error.error.detail, fields };
    }
    if (error instanceof HttpErrorResponse && typeof error.error === 'string') {
      return { message: error.error, fields };
    }
    if (error instanceof HttpErrorResponse && error.status >= 500) {
<<<<<<< HEAD
      return {
        message:
          'The backend failed to process this request. Check its container logs and try again.',
        fields: [],
      };
=======
      return { message: 'The backend failed to process this request. Check its container logs and try again.', fields: [] };
>>>>>>> feature/admin-kafka
    }
    return { message: 'An error occurred. Please try again.', fields };
  }
}
