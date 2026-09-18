import { Injectable, signal } from '@angular/core';

export interface AuthFailure {
  message: string;
  fields: string[];
}

/** Mock authentication boundary. Replace these methods with API calls later. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly credentials = new Map<string, string>([['jmoore', 'trade2026']]);
  readonly currentUser = signal<string | null>(null);

  isSignedIn(): boolean {
    return this.currentUser() !== null;
  }

  signIn(username: string, password: string): AuthFailure | null {
    const user = username.trim();
    const fields: string[] = [];
    if (!user) fields.push('username');
    if (!password) fields.push('password');
    if (fields.length) return { message: 'Enter your username and password.', fields };

    const stored = this.credentials.get(user.toLowerCase());
    if (!stored || stored !== password) {
      return { message: 'Username or password is incorrect.', fields: ['username', 'password'] };
    }
    this.currentUser.set(user);
    return null;
  }

  register(username: string, password: string, confirm: string): AuthFailure | null {
    const user = username.trim();
    if (!user) return { message: 'Choose a username.', fields: ['username'] };
    if (this.credentials.has(user.toLowerCase())) {
      return { message: 'That username is taken.', fields: ['username'] };
    }
    if (password.length < 8 || !/\d/.test(password) || password !== confirm) {
      return {
        message: 'Passwords must match and include 8+ characters with a number.',
        fields: ['password', 'confirm'],
      };
    }
    this.credentials.set(user.toLowerCase(), password);
    this.currentUser.set(user);
    return null;
  }

  signOut(): void {
    this.currentUser.set(null);
  }
}
