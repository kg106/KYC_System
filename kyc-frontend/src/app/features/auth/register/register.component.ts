import { Component, inject } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule, AbstractControl, ValidationErrors } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { AuthService } from '../../../core/auth/auth.service';

function passwordStrength(control: AbstractControl): ValidationErrors | null {
  const v = control.value ?? '';
  const ok = /^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[@#$%^&+=!_]).{8,}$/.test(v);
  return ok ? null : { weakPassword: true };
}

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink,
    MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
    MatProgressSpinnerModule, MatDatepickerModule, MatNativeDateModule],
  template: `
    <h2 class="form-title">Create account</h2>
    <p class="form-subtitle">Start your KYC journey today</p>

    <form [formGroup]="form" (ngSubmit)="submit()" class="auth-form">
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Full Name</mat-label>
        <input matInput formControlName="name" placeholder="John Doe">
        <mat-icon matSuffix>badge</mat-icon>
        @if (form.get('name')?.invalid && form.get('name')?.touched) {
          <mat-error>Name must be 2-100 alphabetic characters</mat-error>
        }
      </mat-form-field>

      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Email address</mat-label>
        <input matInput type="email" formControlName="email">
        <mat-icon matSuffix>email</mat-icon>
        @if (form.get('email')?.invalid && form.get('email')?.touched) {
          <mat-error>Enter a valid email</mat-error>
        }
      </mat-form-field>

      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Mobile Number</mat-label>
        <input matInput formControlName="mobileNumber" placeholder="10-digit number">
        <mat-icon matSuffix>phone</mat-icon>
        @if (form.get('mobileNumber')?.invalid && form.get('mobileNumber')?.touched) {
          <mat-error>Enter a valid 10-digit mobile number</mat-error>
        }
      </mat-form-field>

      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Date of Birth</mat-label>
        <input matInput [matDatepicker]="dob" formControlName="dob">
        <mat-datepicker-toggle matIconSuffix [for]="dob"></mat-datepicker-toggle>
        <mat-datepicker #dob></mat-datepicker>
      </mat-form-field>

      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Password</mat-label>
        <input matInput [type]="showPwd ? 'text' : 'password'" formControlName="password">
        <button type="button" mat-icon-button matSuffix (click)="showPwd = !showPwd">
          <mat-icon>{{ showPwd ? 'visibility_off' : 'visibility' }}</mat-icon>
        </button>
        @if (form.get('password')?.hasError('weakPassword') && form.get('password')?.touched) {
          <mat-error>Min 8 chars, 1 upper, 1 lower, 1 digit, 1 special char</mat-error>
        }
      </mat-form-field>

      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Tenant ID</mat-label>
        <input matInput formControlName="tenantId" placeholder="your-company">
        <mat-icon matSuffix>business</mat-icon>
      </mat-form-field>

      @if (error) {
        <div class="error-banner"><mat-icon>error_outline</mat-icon> {{ error }}</div>
      }
      @if (success) {
        <div class="success-banner"><mat-icon>check_circle</mat-icon> {{ success }}</div>
      }

      <button mat-raised-button color="primary" class="full-width submit-btn" [disabled]="loading">
        @if (loading) { <mat-spinner diameter="20"></mat-spinner> }
        @else { Create Account }
      </button>
    </form>

    <div class="auth-links">
      Already have an account? <a [routerLink]="['/auth/login']">Sign in</a>
    </div>
  `,
  styles: [`
    .form-title { font-size: 1.5rem; font-weight: 700; margin-bottom: 4px; }
    .form-subtitle { color: var(--text-secondary); font-size: 0.88rem; margin-bottom: 28px; }
    .auth-form { display: flex; flex-direction: column; gap: 4px; }
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
    .auth-links {
      text-align: center; margin-top: 20px; font-size: 0.85rem; color: var(--text-secondary);
      a { color: var(--primary-light); text-decoration: none; &:hover { text-decoration: underline; } }
    }
  `]
})
export class RegisterComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  showPwd = false;
  loading = false;
  error = '';
  success = '';
  maxDate = new Date();

  form = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100), Validators.pattern(/^[a-zA-Z\s]*$/)]],
    email: ['', [Validators.required, Validators.email]],
    mobileNumber: ['', [Validators.required, Validators.pattern(/^[0-9]{10}$/)]],
    dob: [null],
    password: ['', [Validators.required, passwordStrength]],
    tenantId: ['', Validators.required]
  });

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading = true;
    this.error = '';
    const val = this.form.value;
    const payload = {
      ...val,
      dob: val.dob ? new Date(val.dob as any).toISOString().split('T')[0] : undefined
    };
    this.auth.register(payload as any).subscribe({
      next: () => {
        this.success = 'Account created! Redirecting to login...';
        setTimeout(() => this.router.navigate(['/auth/login']), 1500);
      },
      error: (e) => {
        this.error = e.error?.message ?? 'Registration failed';
        this.loading = false;
      }
    });
  }
}
