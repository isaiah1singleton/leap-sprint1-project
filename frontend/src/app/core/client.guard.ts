import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const clientGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const signedIn = auth.isSignedIn();
  const isAdmin = auth.isAdmin();
  const isAnalyst = auth.isAnalyst();

  if (!signedIn) {
    console.info('[RouteGuard] clientGuard', {
      target: state.url,
      signedIn,
      isAdmin,
      isAnalyst,
      decision: 'REDIRECT /signin',
    });
    return router.createUrlTree(['/signin']);
  }

  const privileged = isAdmin || isAnalyst;
  console.info('[RouteGuard] clientGuard', {
    target: state.url,
    signedIn,
    isAdmin,
    isAnalyst,
    decision: privileged ? 'REDIRECT /app/admin-audit' : 'ALLOW',
  });
  return privileged ? router.createUrlTree(['/app/admin-audit']) : true;
};
