import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { AuthService } from './auth.service';
import { authGuard } from './auth.guard';
import { adminGuard } from './admin.guard';
import { clientGuard } from './client.guard';

describe('role and authentication guards', () => {
  const auth = {
    isSignedIn: vi.fn(),
    isAdmin: vi.fn(),
    isAnalyst: vi.fn(),
    redirectAnalystToSuperset: vi.fn(),
  };
  beforeEach(() => {
    Object.values(auth).forEach((mock) => mock.mockReset());
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
  });
  function run(guard: CanActivateFn) {
    return TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, { url: '/app/account' } as RouterStateSnapshot),
    );
  }
  it.each([authGuard, adminGuard, clientGuard])('redirects anonymous users', (guard) => {
    expect(TestBed.inject(Router).serializeUrl(run(guard) as UrlTree)).toMatch(
      /^\/(login|signin)$/,
    );
  });
  it('allows an ordinary client and blocks admin access', () => {
    auth.isSignedIn.mockReturnValue(true);
    expect(run(authGuard)).toBe(true);
    expect(run(clientGuard)).toBe(true);
    expect(TestBed.inject(Router).serializeUrl(run(adminGuard) as UrlTree)).toBe('/app/overview');
  });
  it('routes an administrator to the audit ledger', () => {
    auth.isSignedIn.mockReturnValue(true);
    auth.isAdmin.mockReturnValue(true);
    expect(run(adminGuard)).toBe(true);
    expect(TestBed.inject(Router).serializeUrl(run(clientGuard) as UrlTree)).toBe(
      '/app/admin-audit',
    );
  });
  it('sends analysts to their dashboard and cancels client navigation', () => {
    auth.isSignedIn.mockReturnValue(true);
    auth.isAnalyst.mockReturnValue(true);
    expect(run(clientGuard)).toBe(false);
    expect(auth.redirectAnalystToSuperset).toHaveBeenCalledOnce();
  });
});
