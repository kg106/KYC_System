import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { KycService } from '../../../core/services/kyc.service';
import { KycRequest, Page } from '../../../core/models/models';

@Component({
  selector: 'app-kyc-search',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule,
    MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule,
    MatIconModule, MatTableModule, MatPaginatorModule, MatProgressSpinnerModule,
    MatDatepickerModule, MatNativeDateModule],
  template: `
    <div class="page-container fade-in-up">
      <h1 class="page-title">KYC Search</h1>
      <p class="page-subtitle">Search and filter KYC submissions across users</p>

      <!-- Filters -->
      <div class="filter-card">
        <form [formGroup]="form" (ngSubmit)="search()" class="filter-form">
          <mat-form-field appearance="outline">
            <mat-label>User ID</mat-label>
            <input matInput type="number" formControlName="userId">
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>User Name</mat-label>
            <input matInput formControlName="userName">
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Status</mat-label>
            <mat-select formControlName="status">
              <mat-option value="">All</mat-option>
              @for (s of statuses; track s) { <mat-option [value]="s">{{ s }}</mat-option> }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Document Type</mat-label>
            <mat-select formControlName="documentType">
              <mat-option value="">All</mat-option>
              <mat-option value="PAN">PAN</mat-option>
              <mat-option value="AADHAAR">Aadhaar</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Date From</mat-label>
            <input matInput [matDatepicker]="df" formControlName="dateFrom">
            <mat-datepicker-toggle matIconSuffix [for]="df"></mat-datepicker-toggle>
            <mat-datepicker #df></mat-datepicker>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Date To</mat-label>
            <input matInput [matDatepicker]="dt" formControlName="dateTo">
            <mat-datepicker-toggle matIconSuffix [for]="dt"></mat-datepicker-toggle>
            <mat-datepicker #dt></mat-datepicker>
          </mat-form-field>
          <div class="filter-actions">
            <button mat-raised-button color="primary" type="submit" [disabled]="loading()">
              <mat-icon>search</mat-icon> Search
            </button>
            <button mat-stroked-button type="button" (click)="reset()">
              <mat-icon>clear</mat-icon> Clear
            </button>
          </div>
        </form>
      </div>

      <!-- Results -->
      @if (loading()) {
        <div class="loading-state"><mat-spinner diameter="32"></mat-spinner></div>
      } @else if (result()) {
        <div class="results-header">
          <span>{{ result()!.totalElements }} result(s)</span>
        </div>
        <div class="table-wrapper">
          <table mat-table [dataSource]="result()!.content" class="mat-elevation-z0">
            <ng-container matColumnDef="requestId">
              <th mat-header-cell *matHeaderCellDef>ID</th>
              <td mat-cell *matCellDef="let r">#{{ r.requestId }}</td>
            </ng-container>
            <ng-container matColumnDef="docType">
              <th mat-header-cell *matHeaderCellDef>Doc Type</th>
              <td mat-cell *matCellDef="let r">{{ r.documentType ?? '–' }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let r">
                <span class="badge {{ r.status.toLowerCase() }}">{{ r.status }}</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="extractedName">
              <th mat-header-cell *matHeaderCellDef>Name (OCR)</th>
              <td mat-cell *matCellDef="let r">{{ r.extractedName ?? '–' }}</td>
            </ng-container>
            <ng-container matColumnDef="docNumber">
              <th mat-header-cell *matHeaderCellDef>Doc # (masked)</th>
              <td mat-cell *matCellDef="let r">{{ r.extractedDocumentNumber ?? '–' }}</td>
            </ng-container>
            <ng-container matColumnDef="submittedAt">
              <th mat-header-cell *matHeaderCellDef>Submitted</th>
              <td mat-cell *matCellDef="let r">{{ r.submittedAt | date:'short' }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns;"></tr>
          </table>
        </div>
        <mat-paginator
          [length]="result()!.totalElements"
          [pageSize]="pageSize"
          [pageSizeOptions]="[10, 25, 50]"
          (page)="onPage($event)">
        </mat-paginator>
      }
    </div>
  `,
  styles: [`
    .filter-card {
      background: var(--surface-1); border: 1px solid var(--border);
      border-radius: var(--radius-md); padding: 24px; margin-bottom: 24px;
    }
    .filter-form {
      display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
      gap: 12px; align-items: start;
    }
    .filter-actions { display: flex; gap: 8px; align-items: center; padding-top: 4px; }
    .loading-state { display: flex; justify-content: center; padding: 40px; }
    .results-header { margin-bottom: 12px; color: var(--text-secondary); font-size: 0.88rem; }
    .table-wrapper { border-radius: var(--radius-md); overflow: hidden; border: 1px solid var(--border); margin-bottom: 4px; }
    th.mat-mdc-header-cell { font-weight: 600; color: var(--text-secondary); font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.5px; }
    td.mat-mdc-cell { font-size: 0.88rem; }
  `]
})
export class KycSearchComponent {
  private fb = inject(FormBuilder);
  private kycService = inject(KycService);

  form = this.fb.group({
    userId: [null], userName: [''], status: [''], documentType: [''], dateFrom: [null], dateTo: [null]
  });

  result = signal<Page<KycRequest> | null>(null);
  loading = signal(false);
  statuses = ['PENDING', 'SUBMITTED', 'PROCESSING', 'VERIFIED', 'FAILED'];
  columns = ['requestId', 'docType', 'status', 'extractedName', 'docNumber', 'submittedAt'];
  pageSize = 10;
  currentPage = 0;

  search(page = 0): void {
    this.loading.set(true);
    const v = this.form.value;
    this.kycService.searchRequests({
      userId: v.userId ?? undefined,
      userName: v.userName || undefined,
      status: v.status || undefined,
      documentType: v.documentType || undefined,
      dateFrom: v.dateFrom ? new Date(v.dateFrom).toISOString() : undefined,
      dateTo: v.dateTo ? new Date(v.dateTo).toISOString() : undefined,
      page, size: this.pageSize
    }).subscribe({
      next: (r) => { this.result.set(r); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  reset(): void { this.form.reset(); this.result.set(null); }

  onPage(e: PageEvent): void { this.currentPage = e.pageIndex; this.pageSize = e.pageSize; this.search(e.pageIndex); }
}
