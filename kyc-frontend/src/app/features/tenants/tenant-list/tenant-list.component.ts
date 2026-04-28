import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialogModule } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatExpansionModule } from '@angular/material/expansion';
import { TenantService } from '../../../core/services/tenant.service';
import { Tenant, Page, TenantStats } from '../../../core/models/models';

@Component({
  selector: 'app-tenant-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule,
    MatTableModule, MatButtonModule, MatIconModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatPaginatorModule, MatProgressSpinnerModule,
    MatDialogModule, MatTooltipModule, MatSlideToggleModule, MatExpansionModule],
  template: `
    <div class="page-container fade-in-up">
      <div class="page-header">
        <div>
          <h1 class="page-title">Tenant Management</h1>
          <p class="page-subtitle">Manage all tenants in the system</p>
        </div>
        <button mat-raised-button color="primary" (click)="showCreate = !showCreate; editingTenant.set(null)">
          <mat-icon>add</mat-icon> New Tenant
        </button>
      </div>

      <!-- Create Tenant Panel -->
      @if (showCreate) {
        <div class="create-card fade-in-up">
          <h3 class="section-h3">Create New Tenant</h3>
          <form [formGroup]="createForm" (ngSubmit)="createTenant()" class="create-form">
            <mat-form-field appearance="outline">
              <mat-label>Tenant ID</mat-label>
              <input matInput formControlName="tenantId" placeholder="acme-corp">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Name</mat-label>
              <input matInput formControlName="name">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Email</mat-label>
              <input matInput type="email" formControlName="email">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Plan</mat-label>
              <mat-select formControlName="plan">
                <mat-option value="FREE">Free</mat-option>
                <mat-option value="PRO">Pro</mat-option>
                <mat-option value="ENTERPRISE">Enterprise</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Max Daily Attempts</mat-label>
              <input matInput type="number" formControlName="maxDailyAttempts">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Admin Email (optional)</mat-label>
              <input matInput type="email" formControlName="adminEmail">
            </mat-form-field>
            @if (createError()) { <div class="error-banner full-col"><mat-icon>error_outline</mat-icon> {{ createError() }}</div> }
            @if (createSuccess()) { <div class="success-banner full-col"><mat-icon>check_circle</mat-icon> {{ createSuccess() }}</div> }
            <div class="create-actions full-col">
              <button mat-raised-button color="primary" type="submit" [disabled]="creating()">Create Tenant</button>
              <button mat-stroked-button type="button" (click)="showCreate = false">Cancel</button>
            </div>
          </form>
        </div>
      }

      <!-- Edit Tenant Panel -->
      @if (editingTenant()) {
        <div class="create-card fade-in-up" style="border-color: var(--accent);">
          <h3 class="section-h3">Edit Tenant: {{ editingTenant()?.tenantId }}</h3>
          <form [formGroup]="editForm" (ngSubmit)="saveUpdate()" class="create-form">
            <mat-form-field appearance="outline">
              <mat-label>Name</mat-label>
              <input matInput formControlName="name">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Email</mat-label>
              <input matInput type="email" formControlName="email">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Plan</mat-label>
              <mat-select formControlName="plan">
                <mat-option value="FREE">Free</mat-option>
                <mat-option value="PRO">Pro</mat-option>
                <mat-option value="ENTERPRISE">Enterprise</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Max Daily Attempts</mat-label>
              <input matInput type="number" formControlName="maxDailyAttempts">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Allowed Doc Types (comma separated)</mat-label>
              <input matInput formControlName="allowedDocumentTypes">
            </mat-form-field>
            
            @if (createError()) { <div class="error-banner full-col"><mat-icon>error_outline</mat-icon> {{ createError() }}</div> }
            <div class="create-actions full-col">
              <button mat-raised-button color="accent" type="submit" [disabled]="creating()">Update Tenant</button>
              <button mat-stroked-button type="button" (click)="editingTenant.set(null)">Cancel</button>
            </div>
          </form>
        </div>
      }

      <!-- Tenant Table -->
      @if (loading()) {
        <div class="loading-state"><mat-spinner diameter="32"></mat-spinner></div>
      } @else if (result()) {
        <div class="table-wrapper">
          <table mat-table [dataSource]="result()!.content" class="mat-elevation-z0">
            <ng-container matColumnDef="tenantId">
              <th mat-header-cell *matHeaderCellDef>Tenant ID</th>
              <td mat-cell *matCellDef="let t"><code class="code-chip">{{ t.tenantId }}</code></td>
            </ng-container>
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef>Name</th>
              <td mat-cell *matCellDef="let t">{{ t.name }}</td>
            </ng-container>
            <ng-container matColumnDef="plan">
              <th mat-header-cell *matHeaderCellDef>Plan</th>
              <td mat-cell *matCellDef="let t">{{ t.plan ?? '–' }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let t">
                <span class="badge" [class.verified]="t.isActive" [class.failed]="!t.isActive">
                  {{ t.isActive ? 'Active' : 'Inactive' }}
                </span>
              </td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>Actions</th>
              <td mat-cell *matCellDef="let t">
                <button mat-icon-button (click)="onEdit(t)" [matTooltip]="'Edit Tenant'">
                  <mat-icon>edit</mat-icon>
                </button>
                @if (t.isActive) {
                  <button mat-icon-button color="warn" [matTooltip]="'Deactivate'" (click)="toggle(t, false)">
                    <mat-icon>block</mat-icon>
                  </button>
                } @else {
                  <button mat-icon-button color="primary" [matTooltip]="'Activate'" (click)="toggle(t, true)">
                    <mat-icon>check_circle</mat-icon>
                  </button>
                }
                <button mat-icon-button [matTooltip]="'Rotate API Key'" (click)="rotateKey(t)">
                  <mat-icon>autorenew</mat-icon>
                </button>
                <button mat-icon-button [matTooltip]="'View Stats'" (click)="viewStats(t)">
                  <mat-icon>bar_chart</mat-icon>
                </button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns;"></tr>
          </table>
        </div>
        <mat-paginator [length]="result()!.totalElements" [pageSize]="pageSize"
          [pageSizeOptions]="[10,25]" (page)="onPage($event)"></mat-paginator>

        <!-- Stats Drawer -->
        @if (selectedStats()) {
          <div class="stats-drawer fade-in-up">
            <div class="stats-header">
              <strong>Stats: {{ selectedStats()!.tenantId }}</strong>
              <button mat-icon-button (click)="selectedStats.set(null)"><mat-icon>close</mat-icon></button>
            </div>
            <div class="mini-stats">
              <div class="mini-stat"><div class="mini-val">{{ selectedStats()!.totalKycRequests }}</div><div class="mini-lbl">Total</div></div>
              <div class="mini-stat"><div class="mini-val verified">{{ selectedStats()!.verified }}</div><div class="mini-lbl">Verified</div></div>
              <div class="mini-stat"><div class="mini-val pending">{{ selectedStats()!.pending }}</div><div class="mini-lbl">Pending</div></div>
              <div class="mini-stat"><div class="mini-val failed">{{ selectedStats()!.failed }}</div><div class="mini-lbl">Failed</div></div>
            </div>
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px; }
    .create-card { background: var(--surface-1); border: 1px solid var(--border); border-radius: var(--radius-md); padding: 24px; margin-bottom: 24px; border-left: 4px solid var(--primary); }
    .section-h3 { font-size: 1rem; font-weight: 600; margin-bottom: 16px; }
    .create-form { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 12px; }
    .full-col { grid-column: 1/-1; }
    .create-actions { display: flex; gap: 12px; }
    .loading-state { display: flex; justify-content: center; padding: 40px; }
    .table-wrapper { border-radius: var(--radius-md); overflow: hidden; border: 1px solid var(--border); margin-bottom: 4px; }
    th.mat-mdc-header-cell { font-weight: 600; color: var(--text-secondary); font-size: 0.78rem; text-transform: uppercase; }
    td.mat-mdc-cell { font-size: 0.88rem; }
    .code-chip { background: var(--surface-3); padding: 2px 8px; border-radius: 4px; font-size: 0.8rem; }
    .error-banner { display: flex; align-items: center; gap: 8px; background: rgba(255,82,82,0.12); color: var(--error); border: 1px solid rgba(255,82,82,0.3); border-radius: var(--radius-sm); padding: 10px 14px; font-size: 0.85rem; }
    .success-banner { display: flex; align-items: center; gap: 8px; background: rgba(0,230,118,0.12); color: var(--success); border: 1px solid rgba(0,230,118,0.3); border-radius: var(--radius-sm); padding: 10px 14px; }
    .stats-drawer { background: var(--surface-2); border: 1px solid var(--border); border-radius: var(--radius-md); padding: 16px; margin-top: 16px; }
    .stats-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
    .mini-stats { display: flex; gap: 20px; }
    .mini-stat { text-align: center; }
    .mini-val { font-size: 1.4rem; font-weight: 700; &.verified { color: var(--success); } &.pending { color: var(--warning); } &.failed { color: var(--error); } }
    .mini-lbl { font-size: 0.72rem; color: var(--text-secondary); text-transform: uppercase; }
  `]
})
export class TenantListComponent implements OnInit {
  private fb = inject(FormBuilder);
  private tenantService = inject(TenantService);

  result = signal<Page<Tenant> | null>(null);
  loading = signal(false);
  creating = signal(false);
  createError = signal('');
  createSuccess = signal('');
  selectedStats = signal<TenantStats | null>(null);
  editingTenant = signal<Tenant | null>(null);
  showCreate = false;
  pageSize = 10;
  currentPage = 0;
  columns = ['tenantId', 'name', 'plan', 'status', 'actions'];

  createForm = this.fb.group({
    tenantId: ['', Validators.required],
    name: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    plan: ['FREE'],
    maxDailyAttempts: [5],
    adminEmail: ['']
  });

  editForm = this.fb.group({
    name: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    plan: [''],
    maxDailyAttempts: [5],
    allowedDocumentTypes: ['']
  });

  ngOnInit(): void { this.load(); }

  load(page = 0): void {
    this.loading.set(true);
    this.tenantService.getAllTenants(page, this.pageSize).subscribe({
      next: (r) => { this.result.set(r); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  onEdit(tenant: Tenant): void {
    this.showCreate = false;
    this.editingTenant.set(tenant);
    this.editForm.patchValue({
      name: tenant.name,
      email: tenant.email,
      plan: tenant.plan,
      maxDailyAttempts: tenant.maxDailyAttempts,
      allowedDocumentTypes: tenant.allowedDocumentTypes
    });
    this.createError.set('');
    this.createSuccess.set('');
  }

  saveUpdate(): void {
    if (this.editForm.invalid) { this.editForm.markAllAsTouched(); return; }
    this.creating.set(true); this.createError.set('');
    const id = this.editingTenant()?.tenantId!;
    const v = this.editForm.value;
    
    this.tenantService.updateTenant(id, {
      name: v.name!,
      email: v.email!,
      plan: v.plan || undefined,
      maxDailyAttempts: v.maxDailyAttempts ?? undefined,
      allowedDocumentTypes: v.allowedDocumentTypes ? v.allowedDocumentTypes.split(',').map(s => s.trim()) : undefined
    }).subscribe({
      next: () => { 
        this.creating.set(false); 
        this.editingTenant.set(null); 
        this.load(this.currentPage);
      },
      error: (e) => { this.createError.set(e.error?.message ?? 'Update failed'); this.creating.set(false); }
    });
  }

  createTenant(): void {
    if (this.createForm.invalid) { this.createForm.markAllAsTouched(); return; }
    this.creating.set(true); this.createError.set(''); this.createSuccess.set('');
    const v = this.createForm.value;
    this.tenantService.createTenant({
      tenantId: v.tenantId!, name: v.name!, email: v.email!,
      plan: v.plan ?? undefined, maxDailyAttempts: v.maxDailyAttempts ?? undefined,
      adminEmail: v.adminEmail || undefined
    }).subscribe({
      next: () => { this.createSuccess.set('Tenant created!'); this.creating.set(false); this.showCreate = false; this.load(); },
      error: (e) => { this.createError.set(e.error?.message ?? 'Failed'); this.creating.set(false); }
    });
  }

  toggle(tenant: Tenant, active: boolean): void {
    const op = active ? this.tenantService.activate(tenant.tenantId) : this.tenantService.deactivate(tenant.tenantId);
    op.subscribe(() => this.load(this.currentPage));
  }

  rotateKey(tenant: Tenant): void {
    if (!confirm(`Rotate API key for ${tenant.tenantId}? The old key will be invalidated.`)) return;
    this.tenantService.rotateApiKey(tenant.tenantId).subscribe(({ apiKey }) => alert(`New API Key: ${apiKey}`));
  }

  viewStats(tenant: Tenant): void {
    this.tenantService.getStats(tenant.tenantId).subscribe(s => this.selectedStats.set(s));
  }

  onPage(e: PageEvent): void { this.currentPage = e.pageIndex; this.pageSize = e.pageSize; this.load(e.pageIndex); }
}
