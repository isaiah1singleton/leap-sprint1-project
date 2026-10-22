import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

export interface AuthFailure {
  message: string;
  fields: string[];
}

interface AuthResponse {
  accessToken: string;
  refreshToken: {
    uuid: string;
    body: string;
  };
}

interface RegisterResponse {
  name: string;
  email: string;
  registered: true;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/auth';
  private readonly authApiUrl = `${environment.authApiUrl}/auth`;
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
        this.http.post<AuthResponse>(`${this.authApiUrl}/login`, { email: userEmail, password }),
      );
      this.setCurrentUser(
        this.displayNameFor(userEmail),
        userEmail,
        this.segmentFromToken(response.accessToken),
        response.accessToken,
      );
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
        this.http.post<AuthResponse>(`${this.authApiUrl}/login`, { email: userEmail, password }),
      );
      this.setCurrentUser(
        this.displayNameFor(userEmail),
        userEmail,
        this.segmentFromToken(response.accessToken),
        response.accessToken,
      );
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
        this.http.post<RegisterResponse>(`${this.authApiUrl}/register`, {
          name: userName,
          email: userEmail,
          password,
        }),
      );
      if (!response.registered) {
        return { message: 'Registration did not complete. Please try again.', fields: [] };
      }
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

  private displayNameFor(email: string): string {
    return email.split('@')[0] || email;
  }

  private segmentFromToken(accessToken: string): string {
    try {
      const payload = JSON.parse(atob(accessToken.split('.')[1])) as { roles?: string[] };
      return payload.roles?.some((role) => role.toLowerCase() === 'admin') ? 'ADMIN' : 'RETAIL';
    } catch {
      return 'RETAIL';
    }
  }

  private apiFailure(error: unknown, fields: string[]): AuthFailure {
    if (error instanceof HttpErrorResponse && error.status === 0) {
      return {
        message:
          'Cannot reach the backend. Check that the backend container is running and port 8082 is reachable.',
        fields: [],
      };
    }
    if (error instanceof HttpErrorResponse && error.error?.detail) {
      return { message: error.error.detail, fields };
    }
    if (error instanceof HttpErrorResponse && Array.isArray(error.error?.message)) {
      return { message: error.error.message.join(' '), fields };
    }
    if (error instanceof HttpErrorResponse && typeof error.error?.message === 'string') {
      return { message: error.error.message, fields };
    }
    if (error instanceof HttpErrorResponse && typeof error.error === 'string') {
      return { message: error.error, fields };
    }
    if (error instanceof HttpErrorResponse && error.status >= 500) {
      return {
        message:
          'The backend failed to process this request. Check its container logs and try again.',
        fields: [],
      };
    }
    return { message: 'An error occurred. Please try again.', fields };
  }
}
