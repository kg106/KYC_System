import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatChipsModule } from '@angular/material/chips';
import { UserService } from '../../../core/services/user.service';
import { User, Page } from '../../../core/models/models';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule,
    MatTableModule, MatButtonModule, MatIconModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatPaginatorModule, MatProgressSpinnerModule,
    MatDialogModule, MatTooltipModule, MatChipsModule],
  template: `
    <div class="page-container fade-in-up">
      <h1 class="page-title">User Management</h1>
      <p class="page-subtitle">Search, view, and manage users within your tenant</p>

      <!-- Search filters -->
      <div class="filter-card">
        <form [formGroup]="form" (ngSubmit)="search()" class="filter-form">
          <mat-form-field appearance="outline">
            <mat-label>Name</mat-label>
            <input matInput formControlName="name">
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Email</mat-label>
            <input matInput formControlName="email">
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Mobile</mat-label>
            <input matInput formControlName="mobileNumber">
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Status</mat-label>
            <mat-select formControlName="isActive">
              <mat-option [value]="null">All</mat-option>
              <mat-option [value]="true">Active</mat-option>
              <mat-option [value]="false">Inactive</mat-option>
            </mat-select>
          </mat-form-field>
          <div class="filter-actions">
            <button mat-raised-button color="primary" type="submit" [disabled]="loading()">
              <mat-icon>search</mat-icon> Search
            </button>
            <button mat-stroked-button type="button" (click)="resetAndLoadAll()">
              <mat-icon>clear</mat-icon> Clear
            </button>
          </div>
        </form>
      </div>

      @if (editingUser()) {
        <div class="filter-card fade-in-up" style="border-left: 4px solid var(--accent); margin-bottom: 24px;">
          <h3 style="margin: 0 0 16px 0; font-size: 1.1rem;">Edit User: {{ editingUser()?.name }}</h3>
          <form [formGroup]="editForm" (ngSubmit)="saveUpdateUser()" class="filter-form">
            <mat-form-field appearance="outline">
              <mat-label>Full Name</mat-label>
              <input matInput formControlName="name">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Email</mat-label>
              <input matInput formControlName="email">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Mobile Number</mat-label>
              <input matInput formControlName="mobileNumber">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Status</mat-label>
              <mat-select formControlName="isActive">
                <mat-option [value]="true">Active</mat-option>
                <mat-option [value]="false">Inactive</mat-option>
              </mat-select>
            </mat-form-field>
            <div class="filter-actions" style="grid-column: 1/-1;">
              <button mat-raised-button color="accent" type="submit">Update User</button>
              <button mat-stroked-button type="button" (click)="editingUser.set(null)">Cancel</button>
            </div>
          </form>
        </div>
      }

      @if (loading()) {
        <div class="loading-state"><mat-spinner diameter="32"></mat-spinner></div>
      } @else if (result()) {
        <div class="results-header">{{ result()!.totalElements }} user(s)</div>
        <div class="table-wrapper">
          <table mat-table [dataSource]="result()!.content" class="mat-elevation-z0">
            <ng-container matColumnDef="id">
              <th mat-header-cell *matHeaderCellDef>ID</th>
              <td mat-cell *matCellDef="let u">{{ u.id }}</td>
            </ng-container>
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef>Name</th>
              <td mat-cell *matCellDef="let u">{{ u.name }}</td>
            </ng-container>
            <ng-container matColumnDef="email">
              <th mat-header-cell *matHeaderCellDef>Email</th>
              <td mat-cell *matCellDef="let u">{{ u.email }}</td>
            </ng-container>
            <ng-container matColumnDef="mobile">
              <th mat-header-cell *matHeaderCellDef>Mobile</th>
              <td mat-cell *matCellDef="let u">{{ u.mobileNumber }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let u">
                <span class="badge" [class.verified]="u.isActive" [class.failed]="!u.isActive">
                  {{ u.isActive ? 'Active' : 'Inactive' }}
                </span>
              </td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>Actions</th>
              <td mat-cell *matCellDef="let u">
                <button mat-icon-button (click)="onEdit(u)" [matTooltip]="'Edit user'">
                  <mat-icon>edit</mat-icon>
                </button>
                <button mat-icon-button color="warn" [matTooltip]="'Delete user'" (click)="deleteUser(u)">
                  <mat-icon>delete</mat-icon>
                </button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns;"></tr>
          </table>
        </div>
        <mat-paginator [length]="result()!.totalElements" [pageSize]="pageSize"
          [pageSizeOptions]="[10,25,50]" (page)="onPage($event)"></mat-paginator>
      }
    </div>
  `,
  styles: [`
    .page-container { padding-bottom: 40px; }
    .filter-card { background: var(--surface-1); border: 1px solid var(--border); border-radius: var(--radius-md); padding: 24px; margin-bottom: 24px; }
    .filter-form { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 12px; align-items: start; }
    .filter-actions { display: flex; gap: 8px; align-items: center; padding-top: 4px; }
    .loading-state { display: flex; justify-content: center; padding: 40px; }
    .results-header { margin-bottom: 12px; color: var(--text-secondary); font-size: 0.88rem; }
    .table-wrapper { border-radius: var(--radius-md); overflow: hidden; border: 1px solid var(--border); margin-bottom: 4px; }
    th.mat-mdc-header-cell { font-weight: 600; color: var(--text-secondary); font-size: 0.78rem; text-transform: uppercase; }
    td.mat-mdc-cell { font-size: 0.88rem; }
  `]
})
export class UserListComponent implements OnInit {
  private fb = inject(FormBuilder);
  private userService = inject(UserService);

  form = this.fb.group({ name: [''], email: [''], mobileNumber: [''], isActive: [null] });
  editForm = this.fb.group({ name: [''], email: [''], mobileNumber: [''], isActive: [true] });
  
  result = signal<Page<User> | null>(null);
  loading = signal(false);
  editingUser = signal<User | null>(null);
  columns = ['id', 'name', 'email', 'mobile', 'status', 'actions'];
  pageSize = 10;
  currentPage = 0;

  ngOnInit(): void { this.search(); }

  search(page = 0): void {
    this.loading.set(true);
    const v = this.form.value;
    this.userService.searchUsers({
      name: v.name || undefined,
      email: v.email || undefined,
      mobileNumber: v.mobileNumber || undefined,
      isActive: v.isActive ?? undefined,
      page, size: this.pageSize
    }).subscribe({
      next: (r) => { this.result.set(r); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  resetAndLoadAll(): void { this.form.reset(); this.search(); }
  onPage(e: PageEvent): void { this.currentPage = e.pageIndex; this.pageSize = e.pageSize; this.search(e.pageIndex); }

  onEdit(user: User): void {
    this.editingUser.set(user);
    this.editForm.patchValue({
      name: user.name,
      email: user.email,
      mobileNumber: user.mobileNumber,
      isActive: user.isActive
    });
  }

  saveUpdateUser(): void {
    if (this.editForm.invalid) { this.editForm.markAllAsTouched(); return; }
    const id = this.editingUser()?.id!;
    const v = this.editForm.value;
    this.userService.updateUser(id, {
      name: v.name || undefined,
      email: v.email || undefined,
      mobileNumber: v.mobileNumber || undefined,
      isActive: v.isActive ?? undefined
    }).subscribe({
      next: () => {
        this.editingUser.set(null);
        this.search(this.currentPage);
      },
      error: (e) => alert(e.error?.message ?? 'Update failed')
    });
  }

  deleteUser(user: User): void {
    if (!confirm(`Delete user "${user.name}"? This cannot be undone.`)) return;
    this.userService.deleteUser(user.id!).subscribe({ next: () => this.search(this.currentPage) });
  }
}
