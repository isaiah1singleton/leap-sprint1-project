import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const adminGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const signedIn = auth.isSignedIn();
  const isAdmin = auth.isAdmin();
  const isAnalyst = auth.isAnalyst();

  if (!signedIn) {
    console.info('[RouteGuard] adminGuard', {
      target: state.url,
      signedIn,
      isAdmin,
      isAnalyst,
      decision: 'REDIRECT /signin',
    });
    return router.createUrlTree(['/signin']);
  }

  const allowed = isAdmin;
  console.info('[RouteGuard] adminGuard', {
    target: state.url,
    signedIn,
    isAdmin,
    isAnalyst,
    decision: allowed ? 'ALLOW' : 'REDIRECT /app/overview',
  });
  return allowed ? true : router.createUrlTree(['/app/overview']);
};
