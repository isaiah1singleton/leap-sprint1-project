import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { adminGuard } from './core/admin.guard';
import { clientGuard } from './core/client.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'app/overview' },
  {
    path: 'register',
    loadComponent: () => import('./auth/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'signin',
    loadComponent: () => import('./auth/signin.component').then((m) => m.SigninComponent),
  },
  {
    path: 'app',
    canActivate: [authGuard],
    loadComponent: () => import('./shell/shell.component').then((m) => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'overview' },
      {
        path: 'overview',
        title: 'Dashboard',
        canActivate: [clientGuard],
        loadComponent: () => import('./pages/overview.component').then((m) => m.OverviewComponent),
      },
      {
        path: 'portfolio',
        title: 'Portfolio',
        canActivate: [clientGuard],
        loadComponent: () => import('./pages/portfolio.component').then((m) => m.PortfolioComponent),
      },
      {
        path: 'transact',
        title: 'Transact',
        canActivate: [clientGuard],
        loadComponent: () => import('./pages/transact.component').then((m) => m.TransactComponent),
      },
      {
        path: 'orders',
        title: 'Order history',
        canActivate: [clientGuard],
        loadComponent: () => import('./pages/orders.component').then((m) => m.OrdersComponent),
      },
      {
        path: 'cash-activity',
        title: 'Cash activity',
        loadComponent: () => import('./pages/cash-activity.component').then((m) => m.CashActivityComponent),
      },
      {
        path: 'markets',
        title: 'Market summary',
        canActivate: [clientGuard],
        loadComponent: () => import('./pages/markets.component').then((m) => m.MarketsComponent),
      },
      {
        path: 'account',
        title: 'Account details',
        canActivate: [clientGuard],
        loadComponent: () => import('./pages/account.component').then((m) => m.AccountComponent),
      },
      {
        path: 'admin-audit',
        title: 'Admin audit dashboard',
        canActivate: [adminGuard],
        loadComponent: () => import('./pages/admin-audit.component').then((m) => m.AdminAuditComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'signin' },
];
