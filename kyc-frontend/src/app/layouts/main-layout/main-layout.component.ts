import { Component, inject, computed } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDividerModule } from '@angular/material/divider';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../core/auth/auth.service';

interface NavItem {
  label: string;
  icon: string;
  route: string;
  roles?: string[];  // if empty = all roles
}

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [
    CommonModule, RouterOutlet, RouterLink, RouterLinkActive,
    MatToolbarModule, MatSidenavModule, MatListModule, MatIconModule,
    MatButtonModule, MatMenuModule, MatTooltipModule, MatDividerModule
  ],
  template: `
    <mat-sidenav-container class="sidenav-container">
      <!-- Sidebar -->
      <mat-sidenav mode="side" opened class="sidenav">
        <div class="sidenav-brand">
          <div class="brand-icon"><mat-icon>verified_user</mat-icon></div>
          <div>
            <div class="brand-label">KYC<span class="accent">System</span></div>
            <div class="brand-role">{{ getRoleLabel() }}</div>
          </div>
        </div>

        <mat-nav-list class="nav-list">
          @for (item of visibleNav(); track item.route) {
            <a mat-list-item [routerLink]="item.route" routerLinkActive="active-link"
               class="nav-item" [matTooltip]="item.label" matTooltipPosition="right">
              <mat-icon matListItemIcon>{{ item.icon }}</mat-icon>
              <span matListItemTitle>{{ item.label }}</span>
            </a>
          }
        </mat-nav-list>

        <div class="sidenav-footer">
          <a mat-list-item class="nav-item logout-btn" (click)="logout()">
            <mat-icon matListItemIcon>logout</mat-icon>
            <span matListItemTitle>Logout</span>
          </a>
        </div>
      </mat-sidenav>

      <!-- Main content -->
      <mat-sidenav-content class="main-content">
        <mat-toolbar class="topbar">
          <span class="spacer"></span>
          <div class="user-chip" [matMenuTriggerFor]="userMenu">
            <div class="user-avatar">{{ userInitial() }}</div>
            <div class="user-info">
              <span class="user-name">{{ userName() }}</span>
            </div>
            <mat-icon>expand_more</mat-icon>
          </div>
          <mat-menu #userMenu="matMenu">
            <button mat-menu-item routerLink="/users/profile">
              <mat-icon>person</mat-icon> My Profile
            </button>
            <mat-divider></mat-divider>
            <button mat-menu-item (click)="logout()">
              <mat-icon>logout</mat-icon> Logout
            </button>
          </mat-menu>
        </mat-toolbar>

        <div class="content-area">
          <router-outlet />
        </div>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    .sidenav-container { height: 100vh; background: var(--surface-0); }

    .sidenav {
      width: 240px;
      background: var(--surface-1);
      border-right: 1px solid var(--border) !important;
      display: flex; flex-direction: column;
    }

    .sidenav-brand {
      padding: 20px 16px;
      display: flex; align-items: center; gap: 12px;
      border-bottom: 1px solid var(--border);
    }
    .brand-icon {
      width: 40px; height: 40px; border-radius: 12px;
      background: linear-gradient(135deg, var(--primary), var(--accent));
      display: flex; align-items: center; justify-content: center;
      mat-icon { color: #fff; font-size: 20px; width: 20px; height: 20px; }
    }
    .brand-label { font-size: 1.1rem; font-weight: 700; }
    .accent { color: var(--accent); }
    .brand-role { font-size: 0.7rem; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.8px; }

    .nav-list { flex: 1; padding: 12px 8px; }
    .nav-item {
      border-radius: var(--radius-sm) !important;
      margin-bottom: 2px;
      color: var(--text-secondary) !important;
      transition: all var(--transition) !important;
      height: 44px !important;
      mat-icon { color: var(--text-secondary); }
      &:hover { background: var(--glass-hover) !important; color: var(--text-primary) !important; mat-icon { color: var(--primary-light); } }
    }
    .active-link {
      background: rgba(92,107,192,0.18) !important;
      color: var(--primary-light) !important;
      mat-icon { color: var(--primary-light); }
    }

    .sidenav-footer {
      padding: 8px; border-top: 1px solid var(--border);
      .logout-btn { color: var(--error) !important; mat-icon { color: var(--error); } }
    }

    .topbar {
      background: var(--surface-1) !important;
      border-bottom: 1px solid var(--border);
      height: 60px;
      padding: 0 24px;
      box-shadow: none !important;
    }

    .user-chip {
      display: flex; align-items: center; gap: 10px;
      padding: 6px 12px; border-radius: 50px;
      background: var(--glass);
      cursor: pointer;
      transition: background var(--transition);
      &:hover { background: var(--glass-hover); }
    }
    .user-avatar {
      width: 32px; height: 32px; border-radius: 50%;
      background: linear-gradient(135deg, var(--primary), var(--accent));
      display: flex; align-items: center; justify-content: center;
      font-weight: 700; font-size: 0.85rem; color: #fff;
    }
    .user-name { font-size: 0.85rem; font-weight: 500; }

    .main-content { background: var(--surface-0); }
    .content-area { overflow-y: auto; height: calc(100vh - 60px); }
  `]
})
export class MainLayoutComponent {
  private auth = inject(AuthService);

  private allNav: NavItem[] = [
    { label: 'Dashboard',  icon: 'dashboard',      route: '/dashboard' },
    { label: 'Upload KYC', icon: 'upload_file',     route: '/kyc/upload' },
    { label: 'My Status',  icon: 'fact_check',      route: '/kyc/status' },
    { label: 'My History', icon: 'history',         route: '/kyc/history' },
    { label: 'KYC Search', icon: 'manage_search',   route: '/kyc/search',    roles: ['ADMIN', 'TENANT_ADMIN', 'SUPER_ADMIN'] },
    { label: 'Reports',    icon: 'assessment',      route: '/kyc/report',    roles: ['ADMIN', 'TENANT_ADMIN', 'SUPER_ADMIN'] },
    { label: 'My Profile', icon: 'person',          route: '/users/profile' },
    { label: 'Users',      icon: 'group',           route: '/users',         roles: ['ADMIN', 'TENANT_ADMIN', 'SUPER_ADMIN'] },
    { label: 'Tenants',    icon: 'business',        route: '/tenants',       roles: ['SUPER_ADMIN'] },
  ];

  visibleNav = computed(() => {
    return this.allNav.filter(item => {
      if (!item.roles || item.roles.length === 0) return true;
      return item.roles.some(r => this.auth.hasRole(r));
    });
  });

  userName = computed(() => this.auth.currentUser()?.name ?? this.auth.currentUser()?.email ?? 'User');
  userInitial = computed(() => (this.userName()[0] ?? 'U').toUpperCase());

  getRoleLabel(): string {
    const roles = this.auth.getRoles();
    if (roles.includes('ROLE_SUPER_ADMIN')) return 'Super Admin';
    if (roles.includes('ROLE_TENANT_ADMIN')) return 'Tenant Admin';
    if (roles.includes('ROLE_ADMIN')) return 'Admin';
    return 'User';
  }

  logout(): void {
    this.auth.logout();
  }
}
