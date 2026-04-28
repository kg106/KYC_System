import { Component, inject } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink,
    MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <h2 class="form-title">Welcome back</h2>
    <p class="form-subtitle">Sign in to your account to continue</p>

    <form [formGroup]="form" (ngSubmit)="submit()" class="auth-form">
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Email address</mat-label>
        <input matInput type="email" formControlName="email" placeholder="you@company.com" autocomplete="email">
        <mat-icon matSuffix>email</mat-icon>
        @if (form.get('email')?.invalid && form.get('email')?.touched) {
          <mat-error>Enter a valid email</mat-error>
        }
      </mat-form-field>

      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Password</mat-label>
        <input matInput [type]="showPassword ? 'text' : 'password'" formControlName="password" autocomplete="current-password">
        <button type="button" mat-icon-button matSuffix (click)="showPassword = !showPassword">
          <mat-icon>{{ showPassword ? 'visibility_off' : 'visibility' }}</mat-icon>
        </button>
        @if (form.get('password')?.invalid && form.get('password')?.touched) {
          <mat-error>Password is required</mat-error>
        }
      </mat-form-field>

      @if (error) {
        <div class="error-banner">
          <mat-icon>error_outline</mat-icon> {{ error }}
        </div>
      }

      <button mat-raised-button color="primary" class="full-width submit-btn" [disabled]="loading">
        @if (loading) { <mat-spinner diameter="20"></mat-spinner> }
        @else { Sign In }
      </button>
    </form>

    <div class="auth-links">
      <a [routerLink]="['/auth/forgot-password']">Forgot password?</a>
      <span>·</span>
      <a [routerLink]="['/auth/register']">Create account</a>
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
    .auth-links {
      display: flex; gap: 10px; justify-content: center;
      margin-top: 20px; font-size: 0.85rem;
      a { color: var(--primary-light); text-decoration: none; &:hover { text-decoration: underline; } }
      span { color: var(--text-secondary); }
    }
  `]
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });

  loading = false;
  showPassword = false;
  error = '';

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading = true;
    this.error = '';
    this.auth.login(this.form.value as any).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: (e) => {
        this.error = e.error?.message ?? 'Invalid email or password';
        this.loading = false;
      }
    });
  }
}
