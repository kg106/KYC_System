import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { KycService } from '../../../core/services/kyc.service';
import { AuthService } from '../../../core/auth/auth.service';
import { KycRequest } from '../../../core/models/models';

@Component({
  selector: 'app-kyc-history',
  standalone: true,
  imports: [CommonModule, RouterLink, MatTableModule, MatIconModule, MatButtonModule, MatProgressSpinnerModule, MatChipsModule],
  template: `
    <div class="page-container fade-in-up">
      <h1 class="page-title">KYC History</h1>
      <p class="page-subtitle">All your previous verification attempts</p>

      @if (loading()) {
        <div class="loading-state"><mat-spinner diameter="36"></mat-spinner></div>
      } @else if (requests().length === 0) {
        <div class="empty-state">
          <mat-icon>history</mat-icon>
          <p>No history found.</p>
          <a mat-raised-button color="primary" routerLink="/kyc/upload">Upload Document</a>
        </div>
      } @else {
        <div class="table-wrapper">
          <table mat-table [dataSource]="requests()" class="mat-elevation-z0">
            <ng-container matColumnDef="attempt">
              <th mat-header-cell *matHeaderCellDef>Attempt</th>
              <td mat-cell *matCellDef="let r">#{{ r.attemptNumber }}</td>
            </ng-container>
            <ng-container matColumnDef="docType">
              <th mat-header-cell *matHeaderCellDef>Document</th>
              <td mat-cell *matCellDef="let r">{{ r.documentType ?? '–' }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let r">
                <span class="badge {{ r.status.toLowerCase() }}">{{ r.status }}</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="reason">
              <th mat-header-cell *matHeaderCellDef>Failure Reason</th>
              <td mat-cell *matCellDef="let r">{{ r.failureReason || '–' }}</td>
            </ng-container>
            <ng-container matColumnDef="submittedAt">
              <th mat-header-cell *matHeaderCellDef>Submitted</th>
              <td mat-cell *matCellDef="let r">{{ r.submittedAt | date:'short' }}</td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns;"></tr>
          </table>
        </div>
      }
    </div>
  `,
  styles: [`
    .loading-state { display: flex; justify-content: center; padding: 60px; }
    .table-wrapper { border-radius: var(--radius-md); overflow: hidden; border: 1px solid var(--border); }
    th.mat-mdc-header-cell { font-weight: 600; color: var(--text-secondary); font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.5px; }
    td.mat-mdc-cell { font-size: 0.88rem; }
    .empty-state {
      display: flex; flex-direction: column; align-items: center; gap: 12px;
      padding: 48px; background: var(--surface-1); border: 1px solid var(--border);
      border-radius: var(--radius-md); text-align: center;
      mat-icon { font-size: 48px; color: var(--text-secondary); }
      p { color: var(--text-secondary); }
    }
  `]
})
export class KycHistoryComponent implements OnInit {
  private kycService = inject(KycService);
  private auth = inject(AuthService);

  requests = signal<KycRequest[]>([]);
  loading = signal(true);
  columns = ['attempt', 'docType', 'status', 'reason', 'submittedAt'];

  ngOnInit(): void {
    const userId = this.auth.currentUser()?.id;
    if (!userId) { this.loading.set(false); return; }
    this.kycService.getAllStatus(userId).subscribe({
      next: (r) => { this.requests.set(r); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }
}
