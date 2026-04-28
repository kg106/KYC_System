import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { KycService } from '../../../core/services/kyc.service';
import { AuthService } from '../../../core/auth/auth.service';
import { DocumentType } from '../../../core/models/models';

@Component({
  selector: 'app-kyc-upload',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule,
    MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <div class="page-container fade-in-up">
      <h1 class="page-title">Upload KYC Document</h1>
      <p class="page-subtitle">Submit your identity document for verification. Accepted: PAN, Aadhaar · Max 10MB · PDF/JPEG/PNG</p>

      <div class="upload-card">
        <form [formGroup]="form" (ngSubmit)="submit()">
          <div class="form-row">
            <mat-form-field appearance="outline">
              <mat-label>Document Type</mat-label>
              <mat-select formControlName="documentType">
                <mat-option value="PAN">PAN Card</mat-option>
                <mat-option value="AADHAAR">Aadhaar Card</mat-option>
              </mat-select>
              @if (form.get('documentType')?.invalid && form.get('documentType')?.touched) {
                <mat-error>Select a document type</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Document Number</mat-label>
              <input matInput formControlName="documentNumber" placeholder="e.g. ABCDE1234F">
              <mat-icon matSuffix>badge</mat-icon>
              @if (form.get('documentNumber')?.invalid && form.get('documentNumber')?.touched) {
                <mat-error>Document number is required</mat-error>
              }
            </mat-form-field>
          </div>

          <!-- Dropzone -->
          <div class="dropzone"
               [class.drag-over]="isDragging"
               (dragover)="onDragOver($event)"
               (dragleave)="isDragging = false"
               (drop)="onDrop($event)"
               (click)="fileInput.click()">
            @if (selectedFile) {
              <mat-icon class="file-icon">description</mat-icon>
              <p class="file-name">{{ selectedFile.name }}</p>
              <p class="file-size">{{ formatSize(selectedFile.size) }}</p>
            } @else {
              <mat-icon class="drop-icon">cloud_upload</mat-icon>
              <p class="drop-title">Drag & drop your document here</p>
              <p class="drop-sub">or click to browse</p>
              <p class="drop-hint">PDF, JPEG, PNG · Max 10MB</p>
            }
          </div>
          <input #fileInput type="file" accept=".pdf,.jpg,.jpeg,.png" hidden (change)="onFileSelect($event)">

          @if (fileError) { <div class="error-banner"><mat-icon>error_outline</mat-icon> {{ fileError }}</div> }
          @if (error) { <div class="error-banner"><mat-icon>error_outline</mat-icon> {{ error }}</div> }
          @if (success) {
            <div class="success-banner">
              <mat-icon>check_circle</mat-icon>
              {{ success }}
            </div>
          }

          <button mat-raised-button color="primary" class="submit-btn" [disabled]="loading || !selectedFile">
            @if (loading) { <mat-spinner diameter="20"></mat-spinner> }
            @else {
              <ng-container>
                <mat-icon>upload</mat-icon>
                <span>Submit for Verification</span>
              </ng-container>
            }
          </button>
        </form>
      </div>
    </div>
  `,
  styles: [`
    .upload-card {
      background: var(--surface-1);
      border: 1px solid var(--border);
      border-radius: var(--radius-md);
      padding: 32px;
      max-width: 680px;
    }
    .form-row {
      display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px;
      @media(max-width: 600px) { grid-template-columns: 1fr; }
    }
    mat-form-field { width: 100%; }
    .dropzone {
      border: 2px dashed var(--border);
      border-radius: var(--radius-md);
      padding: 40px 20px;
      text-align: center;
      cursor: pointer;
      transition: all var(--transition);
      margin-bottom: 16px;
      &:hover, &.drag-over {
        border-color: var(--primary);
        background: rgba(92,107,192,0.06);
      }
    }
    .drop-icon { font-size: 48px; width: 48px; height: 48px; color: var(--primary-light); margin-bottom: 12px; }
    .file-icon { font-size: 48px; width: 48px; height: 48px; color: var(--accent); margin-bottom: 8px; }
    .drop-title { font-weight: 600; font-size: 1rem; margin-bottom: 4px; }
    .drop-sub { color: var(--text-secondary); font-size: 0.85rem; }
    .drop-hint { color: var(--text-secondary); font-size: 0.75rem; margin-top: 8px; }
    .file-name { font-weight: 600; }
    .file-size { color: var(--text-secondary); font-size: 0.82rem; }
    .error-banner {
      display: flex; align-items: center; gap: 8px;
      background: rgba(255,82,82,0.12); color: var(--error);
      border: 1px solid rgba(255,82,82,0.3);
      border-radius: var(--radius-sm); padding: 10px 14px; font-size: 0.85rem; margin-bottom: 12px;
    }
    .success-banner {
      display: flex; align-items: center; gap: 8px;
      background: rgba(0,230,118,0.12); color: var(--success);
      border: 1px solid rgba(0,230,118,0.3);
      border-radius: var(--radius-sm); padding: 10px 14px; margin-bottom: 12px;
    }
    .submit-btn {
      height: 48px; display: flex; align-items: center; gap: 8px;
      padding: 0 24px; font-size: 1rem;
    }
  `]
})
export class KycUploadComponent {
  private fb = inject(FormBuilder);
  private kycService = inject(KycService);
  private auth = inject(AuthService);

  form = this.fb.group({
    documentType: ['', Validators.required],
    documentNumber: ['', Validators.required]
  });

  selectedFile: File | null = null;
  isDragging = false;
  fileError = '';
  error = '';
  success = '';
  loading = false;

  onDragOver(e: DragEvent): void { e.preventDefault(); this.isDragging = true; }

  onDrop(e: DragEvent): void {
    e.preventDefault(); this.isDragging = false;
    const file = e.dataTransfer?.files[0];
    if (file) this.validateAndSet(file);
  }

  onFileSelect(e: Event): void {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (file) this.validateAndSet(file);
  }

  private validateAndSet(file: File): void {
    this.fileError = '';
    const allowed = ['application/pdf', 'image/jpeg', 'image/png'];
    if (!allowed.includes(file.type)) { this.fileError = 'File type not allowed. Use PDF, JPEG or PNG.'; return; }
    if (file.size > 10 * 1024 * 1024) { this.fileError = 'File exceeds 10MB limit.'; return; }
    this.selectedFile = file;
  }

  formatSize(bytes: number): string {
    return bytes < 1024 * 1024 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / 1024 / 1024).toFixed(2)} MB`;
  }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    if (!this.selectedFile) { this.fileError = 'Please select a file.'; return; }
    const userId = this.auth.currentUser()?.id;
    if (!userId) return;
    this.loading = true; this.error = ''; this.success = '';
    this.kycService.uploadDocument(userId, this.form.value.documentType as DocumentType, this.selectedFile, this.form.value.documentNumber!).subscribe({
      next: (res) => {
        this.success = `Document submitted! Request ID: ${res.requestId}`;
        this.loading = false; this.selectedFile = null; this.form.reset();
      },
      error: (e) => { this.error = e.error?.error ?? 'Upload failed'; this.loading = false; }
    });
  }
}
