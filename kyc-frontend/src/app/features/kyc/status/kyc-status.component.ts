import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatStepperModule } from '@angular/material/stepper';
import { KycService } from '../../../core/services/kyc.service';
import { AuthService } from '../../../core/auth/auth.service';
import { KycRequest, KycStatus } from '../../../core/models/models';

const STATUS_STEPS: KycStatus[] = ['PENDING', 'SUBMITTED', 'PROCESSING', 'VERIFIED'];

@Component({
  selector: 'app-kyc-status',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatStepperModule],
  template: `
    <div class="page-container fade-in-up">
      <h1 class="page-title">My KYC Status</h1>
      <p class="page-subtitle">Latest verification progress</p>

      @if (loading()) {
        <div class="loading-state"><mat-spinner diameter="36"></mat-spinner></div>
      } @else if (kyc()) {
        <div class="status-card">
          <!-- Status stepper -->
          <div class="stepper-wrap">
            @for (step of steps; track step; let i = $index) {
              <div class="step" [class.done]="stepIndex() > i" [class.active]="stepIndex() === i" [class.failed]="isFailed() && stepIndex() === i">
                <div class="step-circle">
                  @if (isFailed() && i === stepIndex()) {
                    <mat-icon>close</mat-icon>
                  } @else if (stepIndex() > i) {
                    <mat-icon>check</mat-icon>
                  } @else {
                    <span>{{ i + 1 }}</span>
                  }
                </div>
                <div class="step-label">{{ step }}</div>
                @if (i < steps.length - 1) { <div class="step-line"></div> }
              </div>
            }
          </div>

          <!-- Details -->
          <div class="detail-grid">
            <div class="detail-item">
              <span class="detail-label">Status</span>
              <span class="badge {{ kyc()!.status.toLowerCase() }}">{{ kyc()!.status }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">Document Type</span>
              <span class="detail-value">{{ kyc()!.documentType ?? '–' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">Attempt #</span>
              <span class="detail-value">{{ kyc()!.attemptNumber }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">Submitted At</span>
              <span class="detail-value">{{ kyc()!.submittedAt | date:'medium' }}</span>
            </div>
            @if (kyc()!.extractedName) {
              <div class="detail-item">
                <span class="detail-label">Name (OCR)</span>
                <span class="detail-value">{{ kyc()!.extractedName }}</span>
              </div>
            }
            @if (kyc()!.extractedDob) {
              <div class="detail-item">
                <span class="detail-label">DOB (OCR)</span>
                <span class="detail-value">{{ kyc()!.extractedDob }}</span>
              </div>
            }
          </div>

          @if (kyc()!.failureReason) {
            <div class="failure-box">
              <mat-icon>warning</mat-icon>
              <div>
                <strong>Reason for Failure</strong>
                <p>{{ kyc()!.failureReason }}</p>
              </div>
            </div>
          }

          @if (isFailed()) {
            <a mat-raised-button color="primary" routerLink="/kyc/upload">
              <mat-icon>refresh</mat-icon> Re-submit Document
            </a>
          }
        </div>
      } @else {
        <div class="empty-state">
          <mat-icon>inbox</mat-icon>
          <p>No KYC submission found.</p>
          <a mat-raised-button color="primary" routerLink="/kyc/upload">Upload Now</a>
        </div>
      }
    </div>
  `,
  styles: [`
    .loading-state { display: flex; justify-content: center; padding: 60px; }
    .status-card {
      background: var(--surface-1); border: 1px solid var(--border);
      border-radius: var(--radius-md); padding: 32px; max-width: 700px;
    }
    .stepper-wrap {
      display: flex; align-items: flex-start; justify-content: center;
      gap: 0; margin-bottom: 32px; overflow-x: auto;
    }
    .step {
      display: flex; flex-direction: column; align-items: center;
      position: relative; flex: 1; min-width: 80px;
    }
    .step-circle {
      width: 40px; height: 40px; border-radius: 50%;
      background: var(--surface-3); color: var(--text-secondary);
      display: flex; align-items: center; justify-content: center;
      font-weight: 700; font-size: 0.9rem; transition: all var(--transition);
      z-index: 1;
      mat-icon { font-size: 18px; width: 18px; height: 18px; }
    }
    .step.done .step-circle { background: var(--success); color: #000; }
    .step.active .step-circle { background: var(--primary); color: #fff; box-shadow: 0 0 0 4px rgba(92,107,192,0.3); }
    .step.failed .step-circle { background: var(--error); color: #fff; }
    .step-label { font-size: 0.72rem; color: var(--text-secondary); margin-top: 8px; text-align: center; text-transform: uppercase; letter-spacing: 0.5px; }
    .step.active .step-label, .step.done .step-label { color: var(--text-primary); }
    .step-line {
      position: absolute; top: 20px; left: 50%; width: 100%;
      height: 2px; background: var(--surface-3); z-index: 0;
    }
    .step.done .step-line { background: var(--success); }

    .detail-grid {
      display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
      gap: 16px; margin-bottom: 20px;
    }
    .detail-item { display: flex; flex-direction: column; gap: 4px; }
    .detail-label { font-size: 0.75rem; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.5px; }
    .detail-value { font-weight: 500; }

    .failure-box {
      display: flex; gap: 12px; background: rgba(255,82,82,0.1);
      border: 1px solid rgba(255,82,82,0.2); border-radius: var(--radius-sm);
      padding: 16px; margin-bottom: 20px;
      mat-icon { color: var(--error); flex-shrink: 0; }
      strong { display: block; margin-bottom: 4px; color: var(--error); }
      p { color: var(--text-secondary); font-size: 0.88rem; }
    }
    .empty-state {
      display: flex; flex-direction: column; align-items: center; gap: 12px;
      padding: 48px; background: var(--surface-1); border: 1px solid var(--border);
      border-radius: var(--radius-md); text-align: center; max-width: 400px;
      mat-icon { font-size: 48px; color: var(--text-secondary); }
      p { color: var(--text-secondary); }
    }
  `]
})
export class KycStatusComponent implements OnInit {
  private kycService = inject(KycService);
  private auth = inject(AuthService);

  kyc = signal<KycRequest | null>(null);
  loading = signal(true);
  steps = STATUS_STEPS;

  stepIndex = () => {
    const k = this.kyc();
    if (!k) return 0;
    if (k.status === 'FAILED') return STATUS_STEPS.indexOf('PROCESSING');
    const idx = STATUS_STEPS.indexOf(k.status as KycStatus);
    return idx >= 0 ? idx : 0;
  };

  isFailed = () => this.kyc()?.status === 'FAILED';

  ngOnInit(): void {
    const userId = this.auth.currentUser()?.id;
    if (!userId) { this.loading.set(false); return; }
    this.kycService.getLatestStatus(userId).subscribe({
      next: (k) => { this.kyc.set(k); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }
}
