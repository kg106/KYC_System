// ─────────────────── Auth ───────────────────
export interface LoginRequest {
  email: string;
  password: string;
}

export interface JwtAuthResponse {
  accessToken: string;
  refreshToken: string;
}

export interface PasswordResetRequestDTO {
  email: string;
}

export interface PasswordResetDTO {
  email: string;
  token: string;
  newPassword: string;
  confirmPassword: string;
}

// ─────────────────── User ───────────────────
export interface User {
  id?: number;
  name: string;
  email: string;
  mobileNumber: string;
  password?: string;
  tenantId?: string;
  isActive?: boolean;
  dob?: string;
}

export interface UserUpdate {
  name?: string;
  email?: string;
  mobileNumber?: string;
  isActive?: boolean;
  dob?: string;
}

export interface UserSearchParams {
  name?: string;
  email?: string;
  mobileNumber?: string;
  isActive?: boolean;
  page?: number;
  size?: number;
}

// ─────────────────── KYC ────────────────────
export type DocumentType = 'PAN' | 'AADHAAR';
export type KycStatus = 'PENDING' | 'SUBMITTED' | 'PROCESSING' | 'VERIFIED' | 'FAILED';

export interface KycRequest {
  requestId: number;
  status: KycStatus;
  failureReason?: string;
  attemptNumber: number;
  submittedAt: string;
  documentType?: DocumentType;
  extractedName?: string;
  extractedDob?: string;
  extractedDocumentNumber?: string;
}

export interface KycSearchParams {
  userId?: number;
  userName?: string;
  status?: string;
  documentType?: string;
  dateFrom?: string;
  dateTo?: string;
  page?: number;
  size?: number;
}

// ─────────────────── Tenant ─────────────────
export interface Tenant {
  id?: number;
  tenantId: string;
  name: string;
  email: string;
  plan?: string;
  isActive?: boolean;
  maxDailyAttempts?: number;
  allowedDocumentTypes?: string;
  apiKey?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TenantCreate {
  tenantId: string;
  name: string;
  email: string;
  plan?: string;
  maxDailyAttempts?: number;
  allowedDocumentTypes?: string;
  adminEmail?: string;
  adminPassword?: string;
  adminName?: string;
}

export interface TenantUpdate {
  name?: string;
  email?: string;
  plan?: string;
  maxDailyAttempts?: number;
  allowedDocumentTypes?: string[];
}

export interface TenantStats {
  tenantId: string;
  tenantName?: string;
  totalKycRequests: number;
  verified: number;
  failed: number;
  pending: number;
  passRate?: number;
}

// ─────────────────── Pagination ─────────────
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
