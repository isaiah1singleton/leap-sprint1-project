import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const signedIn = auth.isSignedIn();
  console.info('[RouteGuard] authGuard', {
    target: state.url,
    signedIn,
    decision: signedIn ? 'ALLOW' : 'REDIRECT /signin',
  });
  return signedIn ? true : router.createUrlTree(['/signin']);
};
