import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDividerModule } from '@angular/material/divider';
import { UserService } from '../../../core/services/user.service';
import { AuthService } from '../../../core/auth/auth.service';
import { User } from '../../../core/models/models';

@Component({
  selector: 'app-user-profile',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule,
    MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
    MatProgressSpinnerModule, MatDatepickerModule, MatNativeDateModule, MatDividerModule],
  template: `
    <div class="page-container fade-in-up">
      <h1 class="page-title">My Profile</h1>
      <p class="page-subtitle">Update your personal information</p>

      @if (loading()) {
        <div class="loading-state"><mat-spinner diameter="32"></mat-spinner></div>
      } @else if (user()) {
        <div class="profile-card">
          <div class="profile-header">
            <div class="avatar">{{ user()!.name[0].toUpperCase() }}</div>
            <div>
              <div class="profile-name">{{ user()!.name }}</div>
              <div class="profile-email">{{ user()!.email }}</div>
              <div class="profile-tenant">Tenant: {{ user()!.tenantId ?? '–' }}</div>
            </div>
          </div>
          <mat-divider class="divider"></mat-divider>
          <form [formGroup]="form" (ngSubmit)="save()" class="edit-form">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Full Name</mat-label>
              <input matInput formControlName="name">
              <mat-icon matSuffix>person</mat-icon>
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Mobile Number</mat-label>
              <input matInput formControlName="mobileNumber">
              <mat-icon matSuffix>phone</mat-icon>
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Date of Birth</mat-label>
              <input matInput [matDatepicker]="dob" formControlName="dob">
              <mat-datepicker-toggle matIconSuffix [for]="dob"></mat-datepicker-toggle>
              <mat-datepicker #dob></mat-datepicker>
            </mat-form-field>

            @if (saveError()) { <div class="error-banner"><mat-icon>error_outline</mat-icon> {{ saveError() }}</div> }
            @if (saveSuccess()) { <div class="success-banner"><mat-icon>check_circle</mat-icon> {{ saveSuccess() }}</div> }

            <button mat-raised-button color="primary" [disabled]="saving()">
              @if (saving()) { <mat-spinner diameter="18"></mat-spinner> } @else { Save Changes }
            </button>
          </form>
        </div>
      }
    </div>
  `,
  styles: [`
    .loading-state { display: flex; justify-content: center; padding: 60px; }
    .profile-card {
      background: var(--surface-1); border: 1px solid var(--border);
      border-radius: var(--radius-md); padding: 32px; max-width: 520px;
    }
    .profile-header { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; }
    .avatar {
      width: 56px; height: 56px; border-radius: 50%;
      background: linear-gradient(135deg, var(--primary), var(--accent));
      display: flex; align-items: center; justify-content: center;
      font-size: 1.4rem; font-weight: 700; color: #fff;
    }
    .profile-name { font-size: 1.1rem; font-weight: 700; }
    .profile-email { color: var(--text-secondary); font-size: 0.85rem; }
    .profile-tenant { color: var(--primary-light); font-size: 0.78rem; margin-top: 4px; }
    .divider { margin-bottom: 24px; }
    .edit-form { display: flex; flex-direction: column; gap: 4px; }
    .full-width { width: 100%; }
    button { height: 44px; margin-top: 8px; }
    .error-banner {
      display: flex; align-items: center; gap: 8px;
      background: rgba(255,82,82,0.12); color: var(--error);
      border: 1px solid rgba(255,82,82,0.3);
      border-radius: var(--radius-sm); padding: 10px 14px; font-size: 0.85rem;
    }
    .success-banner {
      display: flex; align-items: center; gap: 8px;
      background: rgba(0,230,118,0.12); color: var(--success);
      border: 1px solid rgba(0,230,118,0.3);
      border-radius: var(--radius-sm); padding: 10px 14px;
    }
  `]
})
export class UserProfileComponent implements OnInit {
  private fb = inject(FormBuilder);
  private userService = inject(UserService);
  private auth = inject(AuthService);

  user = signal<User | null>(null);
  loading = signal(true);
  saving = signal(false);
  saveError = signal('');
  saveSuccess = signal('');

  form = this.fb.group({
    name: ['', [Validators.required, Validators.pattern(/^[a-zA-Z\s]*$/)]],
    mobileNumber: ['', Validators.pattern(/^[0-9]{10}$/)],
    dob: [null as any]
  });

  ngOnInit(): void {
    const userId = this.auth.currentUser()?.id;
    if (!userId) { this.loading.set(false); return; }
    this.userService.getUserById(userId).subscribe({
      next: (u) => {
        this.user.set(u);
        this.form.patchValue({ name: u.name, mobileNumber: u.mobileNumber, dob: u.dob as any });
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  save(): void {
    if (this.form.invalid) return;
    const userId = this.auth.currentUser()?.id;
    if (!userId) return;
    this.saving.set(true); this.saveError.set(''); this.saveSuccess.set('');
    const v = this.form.value;
    this.userService.updateUser(userId, {
      name: v.name ?? undefined,
      mobileNumber: v.mobileNumber ?? undefined,
      dob: v.dob ? new Date(v.dob).toISOString().split('T')[0] : undefined
    }).subscribe({
      next: (u) => { this.user.set(u); this.saveSuccess.set('Profile updated!'); this.saving.set(false); },
      error: (e) => { this.saveError.set(e.error?.message ?? 'Update failed'); this.saving.set(false); }
    });
  }
}
