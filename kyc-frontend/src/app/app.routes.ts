import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/auth/guards';
import { AuthLayoutComponent } from './layouts/auth-layout/auth-layout.component';
import { MainLayoutComponent } from './layouts/main-layout/main-layout.component';

export const routes: Routes = [
  // Auth routes
  {
    path: 'auth',
    component: AuthLayoutComponent,
    children: [
      {
        path: 'login',
        canActivate: [guestGuard],
        loadComponent: () =>
          import('./features/auth/login/login.component').then(m => m.LoginComponent)
      },
      {
        path: 'register',
        canActivate: [guestGuard],
        loadComponent: () =>
          import('./features/auth/register/register.component').then(m => m.RegisterComponent)
      },
      {
        path: 'forgot-password',
        loadComponent: () =>
          import('./features/auth/forgot-password/forgot-password.component').then(m => m.ForgotPasswordComponent)
      },
      { path: '', redirectTo: 'login', pathMatch: 'full' }
    ]
  },

  // Protected routes (inside main layout)
  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
      },

      // KYC routes
      {
        path: 'kyc/upload',
        loadComponent: () =>
          import('./features/kyc/upload/kyc-upload.component').then(m => m.KycUploadComponent)
      },
      {
        path: 'kyc/status',
        loadComponent: () =>
          import('./features/kyc/status/kyc-status.component').then(m => m.KycStatusComponent)
      },
      {
        path: 'kyc/history',
        loadComponent: () =>
          import('./features/kyc/history/kyc-history.component').then(m => m.KycHistoryComponent)
      },
      {
        path: 'kyc/search',
        canActivate: [roleGuard(['ADMIN', 'TENANT_ADMIN', 'SUPER_ADMIN'])],
        loadComponent: () =>
          import('./features/kyc/search/kyc-search.component').then(m => m.KycSearchComponent)
      },
      {
        path: 'kyc/report',
        canActivate: [roleGuard(['ADMIN', 'TENANT_ADMIN', 'SUPER_ADMIN'])],
        loadComponent: () =>
          import('./features/kyc/report/kyc-report.component').then(m => m.KycReportComponent)
      },

      // User routes
      {
        path: 'users/profile',
        loadComponent: () =>
          import('./features/users/profile/user-profile.component').then(m => m.UserProfileComponent)
      },
      {
        path: 'users',
        canActivate: [roleGuard(['ADMIN', 'TENANT_ADMIN', 'SUPER_ADMIN'])],
        loadComponent: () =>
          import('./features/users/list/user-list.component').then(m => m.UserListComponent)
      },

      // Tenant routes (SUPER_ADMIN only)
      {
        path: 'tenants',
        canActivate: [roleGuard(['SUPER_ADMIN'])],
        loadComponent: () =>
          import('./features/tenants/tenant-list/tenant-list.component').then(m => m.TenantListComponent)
      },

      { path: '', redirectTo: 'dashboard', pathMatch: 'full' }
    ]
  },

  { path: '**', redirectTo: '/dashboard' }
];
