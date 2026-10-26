import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

describe('AuthService HTTP boundary', () => {
  let auth: AuthService;
  let http: HttpTestingController;
  const token = (claims: object) => `header.${btoa(JSON.stringify(claims))}.test-signature`;
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('validates required input without sending a request', async () => {
    expect(await auth.signIn(' ', '')).toMatchObject({ fields: ['email', 'password'] });
    expect(await auth.register('', 'a@b.test', 'password1', 'password1')).toMatchObject({
      fields: ['name'],
    });
    expect(await auth.register('Alice', '', 'password1', 'password1')).toMatchObject({
      fields: ['email'],
    });
    expect(await auth.register('Alice', 'a@b.test', 'short1', 'short1')).toMatchObject({
      fields: ['password', 'confirm'],
    });
    http.expectNone(() => true);
  });

  it('stores an authenticated identity and derives roles from the returned JWT', async () => {
    const result = auth.signIn(' alice@example.com ', 'password1');
    const req = http.expectOne(`${environment.authApiUrl}/auth/login`);
    expect(req.request.body).toEqual({ email: 'alice@example.com', password: 'password1' });
    const accessToken = token({ sub: 'alice@example.com', name: 'Alice Trader', roles: ['ADMIN'] });
    req.flush({ accessToken, refreshToken: { uuid: 'test', body: 'test' } });
    expect(await result).toBeNull();
    expect(auth.currentUser()).toBe('Alice Trader');
    expect(auth.currentUserFirstName()).toBe('Alice');
    expect(auth.isSignedIn()).toBe(true);
    expect(auth.isAdmin()).toBe(true);
    expect(auth.isAnalyst()).toBe(false);
    expect(sessionStorage.getItem('accessToken')).toBe(accessToken);
    await auth.signOut();
    expect(auth.currentUser()).toBeNull();
    expect(auth.isSignedIn()).toBe(false);
    expect(sessionStorage.getItem('accessToken')).toBeNull();
  });

  it.each([
    [{ detail: 'Invalid credentials' }, 401, 'Invalid credentials'],
    [{ message: ['email invalid', 'password required'] }, 400, 'email invalid password required'],
    [{ message: 'Duplicate email' }, 409, 'Duplicate email'],
    ['Service unavailable', 503, 'Service unavailable'],
  ])(
    'exposes structured backend errors without creating a session',
    async (body, status, message) => {
      const result = auth.signIn('alice@example.com', 'password1');
      http
        .expectOne(`${environment.authApiUrl}/auth/login`)
        .flush(body, { status, statusText: 'Failure' });
      expect(await result).toHaveProperty('message', message);
      expect(auth.isSignedIn()).toBe(false);
      expect(sessionStorage.getItem('accessToken')).toBeNull();
    },
  );

  it('registration succeeds without implicitly authenticating the user', async () => {
    const result = auth.register(' Alice ', ' alice@example.com ', 'password1', 'password1');
    const req = http.expectOne(`${environment.authApiUrl}/auth/register`);
    expect(req.request.body).toEqual({
      name: 'Alice',
      email: 'alice@example.com',
      password: 'password1',
    });
    req.flush({ registered: true, email: 'alice@example.com' });
    expect(await result).toBeNull();
    expect(auth.isSignedIn()).toBe(false);
  });

  it('malformed token data grants no administrator or analyst role', () => {
    sessionStorage.setItem('accessToken', 'broken.token');
    sessionStorage.setItem('isAdmin', 'true');
    expect(auth.isAdmin()).toBe(false);
    expect(auth.isAnalyst()).toBe(false);
  });
});
