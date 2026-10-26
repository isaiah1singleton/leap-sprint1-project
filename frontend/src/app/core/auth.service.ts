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
  private readonly storedAccessToken = sessionStorage.getItem('accessToken') ?? '';
  readonly currentUser = signal<string | null>(
    this.nameFromToken(this.storedAccessToken) ?? sessionStorage.getItem('currentUserName'),
  );
  readonly currentUserFirstName = signal<string | null>(
    this.firstNameFromToken(this.storedAccessToken) ?? sessionStorage.getItem('currentUserFirstName'),
  );
  readonly currentUserEmail = signal<string | null>(sessionStorage.getItem('currentUserEmail'));
  readonly currentUserSegment = signal<string | null>(sessionStorage.getItem('currentUserSegment'));

  redirectAnalystToSuperset(): void {
    const token = this.getAccessToken();

    if (!token || !this.isAnalyst()) {
      throw new Error('An authenticated analyst session is required.');
    }

    const form = document.createElement('form');
    form.method = 'POST';
    form.action = environment.supersetLoginUrl;
    form.style.display = 'none';

    const input = document.createElement('input');
    input.type = 'hidden';
    input.name = 'access_token';
    input.value = token;

    form.appendChild(input);
    document.body.appendChild(form);

    form.submit();
  }

  isSignedIn(): boolean {
    return this.currentUser() !== null && this.getAccessToken() !== null;
  }

  isAdmin(): boolean {
    return this.hasRole('admin');
  }

  isAnalyst(): boolean {
    return this.hasRole('analyst');
  }

  getAccessToken(): string | null {
    return sessionStorage.getItem('accessToken');
  }

  beginLocalSession(email: string, segment: 'RETAIL' | 'ADMIN'): void {
    const normalizedEmail = email.trim().toLowerCase();
    const name = normalizedEmail.includes('@') ? normalizedEmail.split('@')[0] : normalizedEmail;
    const titleName =
      name
        .split(/[._-]/g)
        .filter(Boolean)
        .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
        .join(' ') || 'Local User';

    this.setCurrentUser(
      titleName,
      normalizedEmail,
      segment,
      `local-${segment.toLowerCase()}-${Date.now()}`,
      titleName.split(/\s+/)[0],
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
        this.displayNameFor(userEmail, response.accessToken),
        userEmail,
        this.segmentFromToken(response.accessToken),
        response.accessToken,
        this.firstNameFromToken(response.accessToken),
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
    sessionStorage.removeItem('currentUserFirstName');
    sessionStorage.removeItem('currentUserEmail');
    sessionStorage.removeItem('currentUserSegment');
    sessionStorage.removeItem('isAdmin');
    sessionStorage.removeItem('isAnalyst');
    sessionStorage.removeItem('accessToken');
    this.currentUser.set(null);
    this.currentUserFirstName.set(null);
    this.currentUserEmail.set(null);
    this.currentUserSegment.set(null);
    return Promise.resolve();
  }

  private setCurrentUser(
    name: string,
    email: string,
    clientSegment: string,
    accessToken: string,
    firstName: string | null,
  ): void {
    const roles = this.rolesFromToken(accessToken);
    sessionStorage.setItem('currentUserName', name);
    if (firstName) sessionStorage.setItem('currentUserFirstName', firstName);
    else sessionStorage.removeItem('currentUserFirstName');
    sessionStorage.setItem('currentUserEmail', email);
    sessionStorage.setItem('currentUserSegment', clientSegment);
    sessionStorage.setItem('accessToken', accessToken);
    sessionStorage.setItem('isAdmin', String(roles.includes('admin')));
    sessionStorage.setItem('isAnalyst', String(roles.includes('analyst')));
    this.currentUser.set(name);
    this.currentUserFirstName.set(firstName);
    this.currentUserEmail.set(email);
    this.currentUserSegment.set(clientSegment);
  }

  private displayNameFor(email: string, accessToken: string): string {
    return this.nameFromToken(accessToken) || email.split('@')[0] || email;
  }

  private firstNameFromToken(accessToken: string): string | null {
    return this.nameFromToken(accessToken)?.split(/\s+/)[0] ?? null;
  }

  private nameFromToken(accessToken: string): string | null {
    try {
      const encodedPayload = accessToken.split('.')[1];
      if (!encodedPayload) return null;

      const base64Payload = encodedPayload.replace(/-/g, '+').replace(/_/g, '/');
      const paddedPayload = base64Payload.padEnd(Math.ceil(base64Payload.length / 4) * 4, '=');
      const payloadBytes = Uint8Array.from(atob(paddedPayload), (character) => character.charCodeAt(0));
      const payload = JSON.parse(new TextDecoder().decode(payloadBytes)) as { name?: unknown };
      return typeof payload.name === 'string' && payload.name.trim() ? payload.name.trim() : null;
    } catch {
      return null;
    }
  }

  private segmentFromToken(accessToken: string): string {
    return this.rolesFromToken(accessToken).includes('admin') ? 'ADMIN' : 'RETAIL';
  }

  private hasRole(role: string): boolean {
    const token = this.getAccessToken();
    if (token) return this.rolesFromToken(token).includes(role);
    return sessionStorage.getItem(role === 'admin' ? 'isAdmin' : 'isAnalyst') === 'true';
  }

  private rolesFromToken(accessToken: string): string[] {
    try {
      const encodedPayload = accessToken.split('.')[1];
      if (!encodedPayload) return [];

      const base64Payload = encodedPayload.replace(/-/g, '+').replace(/_/g, '/');
      const paddedPayload = base64Payload.padEnd(Math.ceil(base64Payload.length / 4) * 4, '=');
      const payloadBytes = Uint8Array.from(atob(paddedPayload), (character) => character.charCodeAt(0));
      const payload = JSON.parse(new TextDecoder().decode(payloadBytes)) as { roles?: unknown };
      return Array.isArray(payload.roles)
        ? payload.roles
            .filter((role): role is string => typeof role === 'string')
            .map((role) => role.toLowerCase())
        : [];
    } catch {
      return [];
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
