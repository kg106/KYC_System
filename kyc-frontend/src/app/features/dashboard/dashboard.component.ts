import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/auth/auth.service';
import { KycService } from '../../core/services/kyc.service';
import { UserService } from '../../core/services/user.service';
import { TenantService } from '../../core/services/tenant.service';
import { KycRequest } from '../../core/models/models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, MatCardModule, MatIconModule, MatButtonModule, MatProgressSpinnerModule],
  template: `
    <div class="page-container fade-in-up">
      <h1 class="page-title">Dashboard</h1>
      <p class="page-subtitle">Welcome back, {{ userName() }} 👋</p>

      <!-- Quick Action Cards (role-aware) -->
      <div class="actions-grid">
        @if (!isAdminOnly()) {
          <a class="action-card" routerLink="/kyc/upload">
            <div class="action-icon upload"><mat-icon>upload_file</mat-icon></div>
            <div class="action-label">Upload Document</div>
            <div class="action-sub">Submit PAN or Aadhaar for verification</div>
          </a>
          <a class="action-card" routerLink="/kyc/status">
            <div class="action-icon status"><mat-icon>fact_check</mat-icon></div>
            <div class="action-label">My KYC Status</div>
            <div class="action-sub">Check your latest verification status</div>
          </a>
          <a class="action-card" routerLink="/kyc/history">
            <div class="action-icon history"><mat-icon>history</mat-icon></div>
            <div class="action-label">KYC History</div>
            <div class="action-sub">View all previous attempts</div>
          </a>
        }
        @if (isAdmin()) {
          <a class="action-card" routerLink="/kyc/search">
            <div class="action-icon search"><mat-icon>manage_search</mat-icon></div>
            <div class="action-label">KYC Search</div>
            <div class="action-sub">Search and filter submissions</div>
          </a>
          <a class="action-card" routerLink="/kyc/report">
            <div class="action-icon report"><mat-icon>assessment</mat-icon></div>
            <div class="action-label">Generate Report</div>
            <div class="action-sub">Email KYC stats report</div>
          </a>
          <a class="action-card" routerLink="/users">
            <div class="action-icon users"><mat-icon>group</mat-icon></div>
            <div class="action-label">Manage Users</div>
            <div class="action-sub">View and manage tenant users</div>
          </a>
        }
        @if (isSuperAdmin()) {
          <a class="action-card" routerLink="/tenants">
            <div class="action-icon tenants"><mat-icon>business</mat-icon></div>
            <div class="action-label">Tenants</div>
            <div class="action-sub">Manage all tenants</div>
          </a>
        }
      </div>

      <!-- Stats row for admin -->
      @if (isAdmin() && stats()) {
        <h2 class="section-title">Overview</h2>
        <div class="stats-grid">
          <div class="stat-card">
            <div class="stat-icon verified"><mat-icon>verified</mat-icon></div>
            <div class="stat-value">{{ stats()?.verified ?? 0 }}</div>
            <div class="stat-label">Verified</div>
          </div>
          <div class="stat-card">
            <div class="stat-icon pending"><mat-icon>pending</mat-icon></div>
            <div class="stat-value">{{ stats()?.pending ?? 0 }}</div>
            <div class="stat-label">Pending</div>
          </div>
          <div class="stat-card">
            <div class="stat-icon failed"><mat-icon>cancel</mat-icon></div>
            <div class="stat-value">{{ stats()?.failed ?? 0 }}</div>
            <div class="stat-label">Failed</div>
          </div>
          <div class="stat-card">
            <div class="stat-icon total"><mat-icon>summarize</mat-icon></div>
            <div class="stat-value">{{ stats()?.totalKycRequests ?? 0 }}</div>
            <div class="stat-label">Total</div>
          </div>
        </div>
      }

      <!-- User KYC status preview -->
      @if (!isAdminOnly()) {
        <h2 class="section-title">Latest KYC Status</h2>
        @if (loadingKyc()) {
          <div class="loading-row"><mat-spinner diameter="28"></mat-spinner> Looking up your status...</div>
        } @else if (latestKyc()) {
          <div class="kyc-preview-card">
            <div class="kyc-meta">
              <span class="badge {{ latestKyc()!.status.toLowerCase() }}">{{ latestKyc()!.status }}</span>
              <span class="meta-text">Attempt #{{ latestKyc()!.attemptNumber }}</span>
              <span class="meta-text">· {{ latestKyc()!.documentType }}</span>
            </div>
            @if (latestKyc()!.failureReason) {
              <div class="failure-reason"><mat-icon>warning</mat-icon> {{ latestKyc()!.failureReason }}</div>
            }
            <a mat-button color="accent" routerLink="/kyc/history">View Full History →</a>
          </div>
        } @else {
          <div class="empty-state">
            <mat-icon>inbox</mat-icon>
            <p>No KYC submissions yet.</p>
            <a mat-raised-button color="primary" routerLink="/kyc/upload">Upload Your First Document</a>
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .actions-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
      gap: 16px;
      margin-bottom: 32px;
    }
    .action-card {
      background: var(--surface-1);
      border: 1px solid var(--border);
      border-radius: var(--radius-md);
      padding: 20px;
      cursor: pointer;
      text-decoration: none;
      color: var(--text-primary);
      transition: all var(--transition);
      &:hover {
        background: var(--surface-2);
        border-color: var(--primary);
        transform: translateY(-2px);
        box-shadow: 0 8px 32px rgba(92,107,192,0.2);
      }
    }
    .action-icon {
      width: 44px; height: 44px; border-radius: 12px;
      display: flex; align-items: center; justify-content: center;
      margin-bottom: 12px;
      mat-icon { color: #fff; }
      &.upload  { background: linear-gradient(135deg, #5c6bc0, #7c4dff); }
      &.status  { background: linear-gradient(135deg, #00acc1, #00e5ff); }
      &.history { background: linear-gradient(135deg, #ff7043, #ff8a65); }
      &.search  { background: linear-gradient(135deg, #8e24aa, #ce93d8); }
      &.report  { background: linear-gradient(135deg, #43a047, #a5d6a7); }
      &.users   { background: linear-gradient(135deg, #f06292, #f48fb1); }
      &.tenants { background: linear-gradient(135deg, #ffa726, #ffe0b2); }
    }
    .action-label { font-weight: 600; font-size: 1rem; margin-bottom: 4px; }
    .action-sub { color: var(--text-secondary); font-size: 0.8rem; line-height: 1.4; }

    .section-title { font-size: 1.1rem; font-weight: 600; margin-bottom: 16px; color: var(--text-secondary); }

    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
      gap: 12px; margin-bottom: 32px;
    }
    .stat-card {
      background: var(--surface-1); border: 1px solid var(--border);
      border-radius: var(--radius-md); padding: 20px; text-align: center;
    }
    .stat-icon {
      width: 40px; height: 40px; border-radius: 50%;
      display: inline-flex; align-items: center; justify-content: center;
      margin-bottom: 10px;
      mat-icon { font-size: 20px; width: 20px; height: 20px; }
      &.verified { background: rgba(0,230,118,0.15); mat-icon { color: var(--success); } }
      &.pending  { background: rgba(255,171,0,0.15);  mat-icon { color: var(--warning); } }
      &.failed   { background: rgba(255,82,82,0.15);  mat-icon { color: var(--error); } }
      &.total    { background: rgba(92,107,192,0.15); mat-icon { color: var(--primary-light); } }
    }
    .stat-value { font-size: 1.8rem; font-weight: 700; }
    .stat-label { font-size: 0.78rem; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.5px; }

    .loading-row { display: flex; align-items: center; gap: 12px; color: var(--text-secondary); padding: 20px 0; }

    .kyc-preview-card {
      background: var(--surface-1); border: 1px solid var(--border);
      border-radius: var(--radius-md); padding: 20px;
    }
    .kyc-meta { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
    .meta-text { color: var(--text-secondary); font-size: 0.85rem; }
    .failure-reason {
      display: flex; align-items: center; gap: 6px;
      background: rgba(255,82,82,0.1); color: var(--error);
      padding: 8px 12px; border-radius: var(--radius-sm);
      font-size: 0.85rem; margin-bottom: 12px;
      mat-icon { font-size: 18px; }
    }

    .empty-state {
      display: flex; flex-direction: column; align-items: center; gap: 12px;
      padding: 48px; background: var(--surface-1); border: 1px solid var(--border);
      border-radius: var(--radius-md); text-align: center;
      mat-icon { font-size: 48px; color: var(--text-secondary); }
      p { color: var(--text-secondary); }
    }
  `]
})
export class DashboardComponent implements OnInit {
  private auth = inject(AuthService);
  private kycService = inject(KycService);
  private tenantService = inject(TenantService);

  latestKyc = signal<KycRequest | null>(null);
  stats = signal<any>(null);
  loadingKyc = signal(false);

  userName = () => this.auth.currentUser()?.name ?? 'there';
  isAdmin = () => this.auth.hasRole('ADMIN') || this.auth.hasRole('TENANT_ADMIN') || this.auth.hasRole('SUPER_ADMIN');
  isSuperAdmin = () => this.auth.hasRole('SUPER_ADMIN');
  isAdminOnly = () => this.isAdmin() && !this.auth.hasRole('USER');

  ngOnInit(): void {
    const user = this.auth.currentUser();
    if (user?.id && !this.isAdminOnly()) {
      this.loadingKyc.set(true);
      this.kycService.getLatestStatus(user.id).subscribe({
        next: (k) => { this.latestKyc.set(k); this.loadingKyc.set(false); },
        error: () => this.loadingKyc.set(false)
      });
    }
    if (this.isAdmin() && user?.tenantId) {
      this.tenantService.getStats(user.tenantId).subscribe({
        next: (s) => this.stats.set(s),
        error: () => {}
      });
    }
  }
}
