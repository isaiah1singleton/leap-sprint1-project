import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const clientGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isSignedIn()) {
    return router.createUrlTree(['/login']);
  }

  if (auth.isAnalyst()) {
    auth.redirectAnalystToSuperset();
    return false;
  }

  if (auth.isAdmin()) {
    return router.createUrlTree(['/app/admin-audit']);
  }

  return true;
};
