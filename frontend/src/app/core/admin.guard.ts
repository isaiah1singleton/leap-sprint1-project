import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const signedIn = auth.isSignedIn();
  const isAdmin = auth.isAdmin();

  if (!signedIn) {
    console.info('[RouteGuard] adminGuard', {
      target: state.url,
      signedIn,
      isAdmin,
      decision: 'REDIRECT /login',
    });
    return router.createUrlTree(['/login']);
  }

  const allowed = isAdmin;
  console.info('[RouteGuard] adminGuard', {
    target: state.url,
    signedIn,
    isAdmin,
    decision: allowed ? 'ALLOW' : 'REDIRECT /app/overview',
  });
  return allowed ? true : router.createUrlTree(['/app/overview']);
};
