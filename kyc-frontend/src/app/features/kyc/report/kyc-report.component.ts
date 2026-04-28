import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { KycService } from '../../../core/services/kyc.service';

@Component({
  selector: 'app-kyc-report',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule,
    MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
    MatProgressSpinnerModule, MatDatepickerModule, MatNativeDateModule],
  template: `
    <div class="page-container fade-in-up">
      <h1 class="page-title">Generate KYC Report</h1>
      <p class="page-subtitle">Trigger a manual report and send it via email. Defaults to last month if no range is specified.</p>

      <div class="report-card">
        <form [formGroup]="form" (ngSubmit)="generate()" class="report-form">
          <mat-form-field appearance="outline">
            <mat-label>From Date</mat-label>
            <input matInput [matDatepicker]="df" formControlName="dateFrom">
            <mat-datepicker-toggle matIconSuffix [for]="df"></mat-datepicker-toggle>
            <mat-datepicker #df></mat-datepicker>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>To Date</mat-label>
            <input matInput [matDatepicker]="dt" formControlName="dateTo">
            <mat-datepicker-toggle matIconSuffix [for]="dt"></mat-datepicker-toggle>
            <mat-datepicker #dt></mat-datepicker>
          </mat-form-field>
          <mat-form-field appearance="outline" class="full-col">
            <mat-label>Recipient Email (optional)</mat-label>
            <input matInput type="email" formControlName="email" placeholder="Leave blank for default recipients">
            <mat-icon matSuffix>email</mat-icon>
          </mat-form-field>

          @if (error()) { <div class="error-banner full-col"><mat-icon>error_outline</mat-icon> {{ error() }}</div> }
          @if (success()) { <div class="success-banner full-col"><mat-icon>check_circle</mat-icon> {{ success() }}</div> }

          <button mat-raised-button color="primary" class="full-col" [disabled]="loading()">
            @if (loading()) { <mat-spinner diameter="20"></mat-spinner> }
            @else {
              <ng-container>
                <mat-icon>send</mat-icon>
                <span>Generate & Send Report</span>
              </ng-container>
            }
          </button>
        </form>
      </div>
    </div>
  `,
  styles: [`
    .report-card {
      background: var(--surface-1); border: 1px solid var(--border);
      border-radius: var(--radius-md); padding: 32px; max-width: 580px;
    }
    .report-form {
      display: grid; grid-template-columns: 1fr 1fr; gap: 16px;
    }
    .full-col { grid-column: 1 / -1; }
    mat-form-field { width: 100%; }
    button { height: 48px; display: flex; align-items: center; gap: 8px; justify-content: center; }
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
  `]
})
export class KycReportComponent {
  private fb = inject(FormBuilder);
  private kycService = inject(KycService);

  form = this.fb.group({ dateFrom: [null], dateTo: [null], email: [''] });
  loading = signal(false);
  error = signal('');
  success = signal('');

  generate(): void {
    this.loading.set(true); this.error.set(''); this.success.set('');
    const v = this.form.value;
    this.kycService.triggerReport(
      v.dateFrom ? new Date(v.dateFrom).toISOString().split('T')[0] : undefined,
      v.dateTo ? new Date(v.dateTo).toISOString().split('T')[0] : undefined,
      v.email || undefined
    ).subscribe({
      next: (msg: any) => { this.success.set(msg); this.loading.set(false); },
      error: (e: any) => { this.error.set(e.error?.message ?? 'Failed to generate report'); this.loading.set(false); }
    });
  }
}
