import { Component, inject } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatStepperModule } from '@angular/material/stepper';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink,
    MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
    MatProgressSpinnerModule, MatStepperModule],
  template: `
    <h2 class="form-title">Reset Password</h2>
    <p class="form-subtitle">We'll send a 6-digit code to your email</p>

    <mat-stepper [linear]="true" #stepper orientation="horizontal" class="slim-stepper">
      <!-- Step 1: Enter email -->
      <mat-step [stepControl]="emailForm">
        <ng-template matStepLabel>Send Code</ng-template>
        <form [formGroup]="emailForm" (ngSubmit)="sendCode(stepper)" class="step-form">
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Email address</mat-label>
            <input matInput type="email" formControlName="email">
            <mat-icon matSuffix>email</mat-icon>
          </mat-form-field>
          @if (step1Error) { <div class="error-banner"><mat-icon>error_outline</mat-icon> {{ step1Error }}</div> }
          <button mat-raised-button color="primary" class="full-width submit-btn" [disabled]="loading1">
            @if (loading1) { <mat-spinner diameter="20"></mat-spinner> }
            @else {
              <ng-container>Send Reset Code</ng-container>
            }
          </button>
        </form>
      </mat-step>

      <!-- Step 2: Enter code + new password -->
      <mat-step [stepControl]="resetForm">
        <ng-template matStepLabel>Set Password</ng-template>
        <form [formGroup]="resetForm" (ngSubmit)="resetPassword()" class="step-form">
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>6-digit Code</mat-label>
            <input matInput formControlName="token" maxlength="6">
            <mat-icon matSuffix>pin</mat-icon>
          </mat-form-field>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>New Password</mat-label>
            <input matInput [type]="showPwd ? 'text' : 'password'" formControlName="newPassword">
            <button type="button" mat-icon-button matSuffix (click)="showPwd = !showPwd">
              <mat-icon>{{ showPwd ? 'visibility_off' : 'visibility' }}</mat-icon>
            </button>
          </mat-form-field>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Confirm Password</mat-label>
            <input matInput type="password" formControlName="confirmPassword">
          </mat-form-field>
          @if (step2Error) { <div class="error-banner"><mat-icon>error_outline</mat-icon> {{ step2Error }}</div> }
          @if (step2Success) { <div class="success-banner"><mat-icon>check_circle</mat-icon> {{ step2Success }}</div> }
          <button mat-raised-button color="primary" class="full-width submit-btn" [disabled]="loading2">
            @if (loading2) { <mat-spinner diameter="20"></mat-spinner> }
            @else {
              <ng-container>Reset Password</ng-container>
            }
          </button>
        </form>
      </mat-step>
    </mat-stepper>

    <div class="auth-links">
      <a [routerLink]="['/auth/login']">← Back to Login</a>
    </div>
  `,
  styles: [`
    .form-title { font-size: 1.5rem; font-weight: 700; margin-bottom: 4px; }
    .form-subtitle { color: var(--text-secondary); font-size: 0.88rem; margin-bottom: 20px; }
    .slim-stepper { background: transparent; margin-bottom: 8px; }
    .step-form { display: flex; flex-direction: column; gap: 4px; padding-top: 16px; }
    .full-width { width: 100%; }
    .submit-btn { height: 48px; font-size: 1rem; margin-top: 8px; display: flex; align-items: center; justify-content: center; gap: 8px; }
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
      border-radius: var(--radius-sm); padding: 10px 14px; font-size: 0.85rem;
    }
    .auth-links { text-align: center; margin-top: 20px; font-size: 0.85rem;
      a { color: var(--primary-light); text-decoration: none; } }
  `]
})
export class ForgotPasswordComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);

  emailForm = this.fb.group({ email: ['', [Validators.required, Validators.email]] });
  resetForm = this.fb.group({
    token: ['', [Validators.required, Validators.pattern(/^[A-Z0-9]{1,6}$/i)]],
    newPassword: ['', Validators.required],
    confirmPassword: ['', Validators.required]
  });

  loading1 = false; loading2 = false; showPwd = false;
  step1Error = ''; step2Error = ''; step2Success = '';

  sendCode(stepper: any): void {
    if (this.emailForm.invalid) { this.emailForm.markAllAsTouched(); return; }
    this.loading1 = true; this.step1Error = '';
    this.auth.forgotPassword({ email: this.emailForm.value.email! }).subscribe({
      next: () => { this.loading1 = false; stepper.next(); },
      error: (e) => { 
        this.step1Error = (typeof e.error === 'string' ? e.error : (e.error?.message ?? 'Failed to send code'));
        this.loading1 = false; 
      }
    });
  }

  resetPassword(): void {
    if (this.resetForm.invalid) { this.resetForm.markAllAsTouched(); return; }
    const v = this.resetForm.value;
    if (v.newPassword !== v.confirmPassword) { this.step2Error = 'Passwords do not match'; return; }
    this.loading2 = true; this.step2Error = '';
    this.auth.resetPassword({
      email: this.emailForm.value.email!,
      token: v.token!,
      newPassword: v.newPassword!,
      confirmPassword: v.confirmPassword!
    }).subscribe({
      next: () => { this.step2Success = 'Password reset! You can now login.'; this.loading2 = false; },
      error: (e) => { 
        this.step2Error = (typeof e.error === 'string' ? e.error : (e.error?.message ?? 'Reset failed'));
        this.loading2 = false; 
      }
    });
  }
}
