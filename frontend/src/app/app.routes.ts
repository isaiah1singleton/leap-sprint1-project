import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'app/overview' },
  {
    path: 'register',
    title: 'Create account',
    loadComponent: () => import('./auth/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'signin',
    title: 'Sign in',
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
        loadComponent: () => import('./pages/overview.component').then((m) => m.OverviewComponent),
      },
      {
        path: 'portfolio',
        title: 'Portfolio',
        loadComponent: () => import('./pages/portfolio.component').then((m) => m.PortfolioComponent),
      },
      {
        path: 'transact',
        title: 'Transact',
        loadComponent: () => import('./pages/transact.component').then((m) => m.TransactComponent),
      },
      {
        path: 'orders',
        title: 'Order history',
        loadComponent: () => import('./pages/orders.component').then((m) => m.OrdersComponent),
      },
      {
        path: 'markets',
        title: 'Market summary',
        loadComponent: () => import('./pages/markets.component').then((m) => m.MarketsComponent),
      },
      {
        path: 'account',
        title: 'Account details',
        loadComponent: () => import('./pages/account.component').then((m) => m.AccountComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'signin' },
];
