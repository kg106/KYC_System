# KYC System — Technical Documentation

> **Version:** 0.0.1-SNAPSHOT  
> **Framework:** Spring Boot 3.4.2 · Java 21  
> **Last Updated:** March 2026

---

## Table of Contents

1. [Functionality Summary](#1-functionality-summary)
2. [System Overview](#2-system-overview)
3. [Technology Stack](#3-technology-stack)
4. [Architecture Overview](#4-architecture-overview)
5. [Project Structure](#5-project-structure)
6. [Data Model](#6-data-model)
7. [API Reference](#7-api-reference)
8. [Authentication & Security](#8-authentication--security)
9. [Multi-Tenancy](#9-multi-tenancy)
10. [KYC Processing Pipeline](#10-kyc-processing-pipeline)
11. [OCR & Document Verification](#11-ocr--document-verification)
12. [Reporting & Scheduling](#12-reporting--scheduling)
13. [Configuration Reference](#13-configuration-reference)
14. [Database Migrations](#14-database-migrations)
15. [Getting Started](#15-getting-started)
16. [Default Credentials](#16-default-credentials)

---

## 1. Functionality Summary

### ✅ Implemented Features

#### Authentication & Access Control
- User registration with email, mobile number, and password validation.
- User login with JWT access token generation and HttpOnly refresh token cookie.
- Refresh token rotation with family-based tracking stored in Redis.
- Secure logout with access token blacklisting and refresh token family revocation.
- Forgot password flow with a 6-character email-based reset token (15-min expiry).
- Password reset using the emailed token with new password confirmation.
- Role-based access control with a three-level hierarchy: SUPER_ADMIN > TENANT_ADMIN > ADMIN.
- Method-level authorization using `@PreAuthorize` with custom SpEL expressions.
- Custom JSON error responses for 401 (Unauthorized) and 403 (Forbidden).

#### KYC Processing
- KYC document upload with file type, size, and MIME validation (up to 10MB).
- Asynchronous KYC processing via an in-memory queue with 10 concurrent worker threads.
- OCR-based data extraction from identity documents using Tesseract (Tess4j).
- Automated identity verification by matching extracted name, DOB, and document number against user profile.
- Duplicate submission prevention via a partial unique database index on active KYC requests.
- Already-verified document detection to block redundant re-uploads.
- KYC request reuse for re-submissions on previously failed attempts.
- Atomic status transitions using Check-And-Set (CAS) to prevent double processing.
- Optimistic locking on KYC requests for concurrent access safety.

#### KYC Status & Search
- Retrieve latest KYC verification status for a specific user.
- Retrieve full KYC request history for a user.
- Admin search of KYC requests with filters (user, status, document type, date range) and pagination.

#### User Management
- List all registered users (Admin only).
- Get user profile by ID (self-service or Admin).
- Partial update of user profile (self-service or Admin).
- Delete user permanently (Admin only).
- Search users with filters (name, email, mobile, active status) and pagination.

#### Multi-Tenancy
- Tenant creation with optional admin user provisioning by Super Admin.
- Paginated listing of all tenants.
- Tenant configuration update (max daily attempts, allowed document types, plan).
- Tenant activation and deactivation with idempotent feedback messages.
- API key generation and rotation per tenant.
- Tenant-level KYC statistics retrieval.
- Tenant resolution from JWT claim, X-Tenant-ID header, or X-API-Key header.
- Tenant-scoped data isolation via tenant_id discriminator on all entities.
- Super Admin bypass of tenant scoping for cross-tenant operations.
- Inactive tenant blocking — immediately prevents all user access.

#### Data Security
- AES-256 encryption at rest for sensitive fields (document numbers) via JPA AttributeConverter.
- Document number masking (e.g., XXXX1234) when viewed by Admin users.
- SHA-256 file hash computation for uploaded document integrity verification.
- BCrypt password hashing for all stored credentials.

#### Reporting & Audit
- Automated monthly KYC report generation and email delivery (1st of every month, 8 AM IST).
- Manual/on-demand report trigger for any specific month by Admin.
- Comprehensive audit logging with old/new values, IP address, user-agent, and correlation ID.

#### Infrastructure
- Database schema version control via Flyway migrations (V1–V6).
- Auto-seeding of default roles, tenants, and admin users on first startup.
- Interactive API documentation via Swagger UI (SpringDoc OpenAPI).

---

### 🔄 Current Status

All planned core features and the multi-tenancy module have been **completed and integrated**. The system is fully functional with tenant-scoped data isolation, role hierarchy, and async KYC processing in place.

---

### 🚀 Future Scope & Enhancements

| Area | Enhancement | Description |
|---|---|---|
| **Tenant Plans** | Plan-based feature gating | Implement logic for the existing `plan` field (BASIC/PRO/ENTERPRISE) to control feature access, rate limits, and document type restrictions per plan tier. |
| **Queue Infrastructure** | Message broker integration | Replace the in-memory `BlockingQueue` with RabbitMQ or Apache Kafka for durability, retry mechanisms, and horizontal scalability. |
| **OCR** | External OCR API integration | Integrate with cloud OCR services (Google Vision, AWS Textract) as alternatives to local Tesseract for improved accuracy and scalability. |
| **Notifications** | SMS and push notifications | Notify users of KYC status changes via SMS (Twilio) or push notifications in addition to email. |
| **Deployment** | Docker & Kubernetes | Containerize the application with Docker and create Helm charts for Kubernetes deployment with auto-scaling. |
| **Monitoring** | Observability stack | Add Spring Boot Actuator, Prometheus metrics, and Grafana dashboards for system health and KYC pipeline monitoring. |
| **Rate Limiting** | API rate limiting | Enforce the existing `maxDailyAttempts` per tenant and add global API rate limiting using Redis-backed counters. |
| **File Storage** | Cloud storage migration | Move document storage from local filesystem to AWS S3 or MinIO for durability, backup, and CDN support. |
| **Testing** | Integration test suite | Expand test coverage with Testcontainers-based integration tests for the full KYC pipeline, multi-tenancy, and auth flows. |
| **Liveness Verification** | Face match / liveness | Add face recognition and liveness detection as an additional KYC verification step. |
| **Webhook** | Status change callbacks | Allow tenants to register webhook URLs that receive real-time KYC status change notifications. |
| **Admin Dashboard** | Web-based UI | Build a frontend admin dashboard for tenant management, KYC monitoring, and analytics visualization. |

---

## 2. System Overview

The **KYC System** is a multi-tenant Know Your Customer verification platform built with Spring Boot. It allows organizations (tenants) to onboard users and verify their identity documents (PAN, Aadhaar, Passport, etc.) through an automated pipeline that includes:

- **Document upload** with file validation
- **OCR extraction** using Tesseract
- **Automated verification** with name/DOB/document-number matching
- **Asynchronous processing** via an in-memory queue with 10 concurrent workers
- **Multi-tenant isolation** with a hierarchical role model
- **JWT-based stateless authentication** with refresh token rotation
- **Monthly KYC reporting** via email

---

## 3. Technology Stack

| Layer | Technology |
|---|---|
| **Language** | Java 21 |
| **Framework** | Spring Boot 3.4.2 |
| **Security** | Spring Security + JWT (jjwt 0.11.5) |
| **Database** | PostgreSQL |
| **Cache / Token Store** | Redis (Valkey) |
| **ORM** | Spring Data JPA / Hibernate |
| **Migrations** | Flyway |
| **OCR Engine** | Tesseract (Tess4j 5.11.0) |
| **API Docs** | SpringDoc OpenAPI 2.8.3 (Swagger UI) |
| **Email** | Spring Mail (SMTP / Gmail) |
| **Build Tool** | Maven |
| **Code Generation** | Lombok |
| **Testing** | JUnit 5, Spring Security Test, Testcontainers (PostgreSQL, Redis) |

---

## 4. Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│                         CLIENT (Browser / API)                   │
└──────────────────────────┬───────────────────────────────────────┘
                           │ HTTP / REST
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                     SPRING SECURITY FILTER CHAIN                 │
│  ┌────────────────────┐   ┌──────────────────────────────┐       │
│  │ JwtAuthFilter      │──▶│ TenantResolutionFilter       │       │
│  │ (Extract JWT,      │   │ (Resolve tenant from JWT /   │       │
│  │  Authenticate)     │   │  Header / API Key, validate) │       │
│  └────────────────────┘   └──────────────────────────────┘       │
└──────────────────────────┬───────────────────────────────────────┘
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                      REST CONTROLLERS                            │
│  AuthController · KycController · UserController · TenantCtrl    │
└──────────────────────────┬───────────────────────────────────────┘
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                      SERVICE LAYER                               │
│  KycOrchestrationService ─── KycRequestService                   │
│  KycDocumentService ──────── OcrService                          │
│  KycExtractionService ────── KycVerificationService              │
│  UserService ─────────────── TenantService                       │
│  PasswordResetService ────── RefreshTokenService                 │
│  AuditLogService ─────────── TokenBlacklistService               │
│  KycReportService ────────── SecurityService                     │
└──────────────────────────┬───────────────────────────────────────┘
                           ▼
┌────────────────────┐  ┌─────────────┐  ┌───────────────────────┐
│  QUEUE (In-Memory) │  │   REDIS     │  │    POSTGRESQL         │
│  KycQueueService   │  │  (Refresh   │  │  (All entities,       │
│  KycWorker (×10)   │  │   Tokens,   │  │   Flyway migrations)  │
│                    │  │   Blacklist) │  │                       │
└────────────────────┘  └─────────────┘  └───────────────────────┘
                                              ▲
                                              │
                                    ┌─────────┴─────────┐
                                    │  Flyway Migrations │
                                    │  V1 → V6           │
                                    └───────────────────┘
```

### Key Design Patterns

| Pattern | Implementation |
|---|---|
| **Orchestration** | `KycOrchestrationService` coordinates the full KYC flow |
| **CAS (Check-And-Set)** | Atomic status transitions via `updateStatusIfPending()` |
| **Queue-based async** | `KycQueueService` + `KycWorker` (10-thread pool) |
| **ThreadLocal tenant** | `TenantContext` with `InheritableThreadLocal` |
| **Optimistic locking** | `@Version` on `KycRequest` entity |
| **Encryption at rest** | `KycEncryptionConverter` for PII fields |
| **Role hierarchy** | `SUPER_ADMIN > TENANT_ADMIN > ADMIN` |

---

## 5. Project Structure

```
src/main/java/com/example/kyc_system/
├── KycSystemApplication.java          # Main entry point
├── config/
│   ├── DataInitializer.java           # Seeds roles, tenants, default users
│   ├── KycProperties.java             # Custom config properties
│   ├── SecurityConfig.java            # Security filter chain, role hierarchy
│   └── SwaggerConfig.java             # OpenAPI/Swagger configuration
├── context/
│   └── TenantContext.java             # ThreadLocal tenant holder
├── controller/
│   ├── AuthController.java            # Login, register, password reset, logout
│   ├── KycController.java             # Upload, status, search, report
│   ├── TenantController.java          # CRUD, activate/deactivate tenants
│   └── UserController.java            # CRUD, search users
├── converter/
│   └── KycEncryptionConverter.java    # AES encryption for DB fields
├── dto/                               # Data Transfer Objects (15 classes)
├── entity/                            # JPA entities (11 classes)
├── enums/
│   ├── DocumentType.java              # PAN, AADHAAR, PASSPORT, LICENSE, etc.
│   ├── KycStatus.java                 # PENDING → SUBMITTED → PROCESSING → VERIFIED/FAILED
│   └── VerificationStatus.java        # PENDING, VERIFIED, REJECTED
├── exception/
│   ├── BusinessException.java         # Custom business error
│   └── GlobalExceptionHandler.java    # Centralized error handling
├── filter/
│   └── TenantResolutionFilter.java    # Multi-tenant resolution filter
├── queue/
│   ├── KycQueueService.java           # In-memory BlockingQueue
│   └── KycWorker.java                 # 10-thread consumer pool
├── repository/                        # Spring Data JPA repositories (9+)
│   └── specification/                 # Dynamic query specifications
├── scheduler/
│   └── KycReportScheduler.java        # Monthly report cron job
├── security/
│   ├── CustomAccessDeniedHandler.java
│   ├── CustomAuthenticationEntryPoint.java
│   ├── JwtAuthenticationFilter.java   # Extracts & validates JWT from requests
│   ├── JwtTokenProvider.java          # JWT creation, validation, parsing
│   ├── SecurityService.java           # canAccessUser() authorization logic
│   └── UserDetailsServiceImpl.java    # Loads user from DB for Spring Security
├── service/                           # Business logic (22 interface + impl)
│   └── impl/                          # Additional implementations
│       ├── AuditLogServiceImpl.java
│       ├── KycReportEmailServiceImpl.java
│       ├── RefreshTokenServiceImpl.java
│       └── TokenBlacklistServiceImpl.java
└── util/
    ├── CookieUtil.java                # Refresh token HttpOnly cookie
    ├── EncryptionUtil.java            # AES-256 encryption/decryption
    ├── KycFileValidator.java          # File type and size validation
    ├── MaskingUtil.java               # PII masking (e.g., XXXX1234)
    └── PasswordUtil.java             # Password strength validation
```

---

## 6. Data Model

### Entity Relationship Diagram

```
┌──────────┐     1:N     ┌──────────┐     1:N     ┌──────────────┐
│  Tenant  │────────────▶│   User   │────────────▶│  KycRequest  │
│----------│             │----------|             │--------------|
│ tenantId │             │ id       │             │ id           │
│ name     │             │ name     │             │ tenantId     │
│ email    │             │ email    │             │ status       │
│ plan     │             │ tenantId │             │ documentType │
│ isActive │             │ dob      │             │ attemptNumber│
│ maxDaily │             │ isActive │             │ failureReason│
│ apiKey   │             └──────────┘             │ version (OL) │
│ allowed  │                  │                   └──────────────┘
│ DocTypes │                  │                      │         │
└──────────┘                  │                      │         │
                              │ N:M                  │ 1:N     │ 1:N
                         ┌────┴─────┐                │         │
                         │ UserRole │     ┌──────────┴──┐   ┌──┴──────────────────┐
                         │----------|     │ KycDocument  │   │ KycVerificationResult│
                         │ user_id  │     │-------------|   │---------------------|
                         │ role_id  │     │ tenantId    │   │ nameMatchScore      │
                         └──────────┘     │ documentNum │   │ dobMatch            │
                              │           │ documentHash│   │ documentNumberMatch │
                         ┌────┴─────┐     │ mimeType    │   │ finalStatus         │
                         │   Role   │     │ isEncrypted │   │ decisionReason      │
                         │----------|     └─────────────┘   └─────────────────────┘
                         │ name     │          │         │
                         └──────────┘          │ 1:N     │ 1:N
                                               │         │
                                    ┌──────────┴──┐   ┌──┴───────────────────────┐
                                    │KycExtracted │   │ KycDocumentVerification  │
                                    │   Data      │   │--------------------------|
                                    │-------------|   │ status                   │
                                    │ extractedNam│   │ rejectedReason           │
                                    │ extractedDob│   │ verifiedAt               │
                                    │ extractedDoc│   └──────────────────────────┘
                                    │ rawOcrResp  │
                                    └─────────────┘

                         ┌───────────────┐
                         │   AuditLog    │  (Standalone)
                         │---------------|
                         │ entityType    │
                         │ action        │
                         │ performedBy   │
                         │ oldValue (JSON)│
                         │ newValue (JSON)│
                         │ tenantId      │
                         │ ipAddress     │
                         └───────────────┘
```

### Key Entity Details

#### `Tenant`
| Field | Type | Description |
|---|---|---|
| `tenantId` | String(50) | Unique business identifier (e.g., `"default"`, `"acme"`) |
| `plan` | String(20) | Future use: `BASIC`, `PRO`, etc. |
| `maxDailyAttempts` | Integer | Rate limit for KYC submissions (default: 5) |
| `allowedDocumentTypes` | String(200) | Comma-separated: `"PAN,AADHAAR"` |
| `apiKey` | String | Auto-generated, rotatable |

#### `User`
| Field | Type | Description |
|---|---|---|
| `tenantId` | String(50) | FK to tenant; scopes all data access |
| `passwordHash` | String | BCrypt-encoded password |
| `userRoles` | Set\<UserRole\> | Many-to-many via join entity |

#### `KycRequest`
| Field | Type | Description |
|---|---|---|
| `status` | String(20) | One of: `PENDING`, `SUBMITTED`, `PROCESSING`, `VERIFIED`, `FAILED` |
| `attemptNumber` | Integer | Tracks submission retries |
| `version` | Long | `@Version` — optimistic locking for concurrent access |

#### `KycDocument`
| Field | Type | Description |
|---|---|---|
| `documentNumber` | String | **Encrypted at rest** via `KycEncryptionConverter` |
| `documentHash` | String(64) | SHA-256 hash of the uploaded file |
| `encrypted` | Boolean | Flag indicating field-level encryption status |

#### `KycExtractedData`
| Field | Type | Description |
|---|---|---|
| `extractedDocumentNumber` | String | **Encrypted at rest** |
| `rawOcrResponse` | Map (JSONB) | Full OCR engine output stored as PostgreSQL `jsonb` |

---

## 7. API Reference

### 7.1 Authentication (`/api/auth`)

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/auth/login` | Public | Returns JWT access token + sets refresh token cookie |
| `POST` | `/api/auth/register` | Public | Creates a new user account |
| `POST` | `/api/auth/forgot-password` | Public | Sends 6-char reset token to email (15-min expiry) |
| `POST` | `/api/auth/change-password` | Public | Resets password using the email token |
| `POST` | `/api/auth/refresh` | Cookie | Rotates access token using HttpOnly refresh cookie |
| `POST` | `/api/auth/logout` | Bearer | Blacklists access token, revokes refresh family, clears cookie |

#### Login — Request Body
```json
{
  "email": "admin@kyc.com",
  "password": "Password@123"
}
```

#### Login — Response
```json
{
  "accessToken": "eyJhbGciOiJIUzM4NC...",
  "refreshToken": "family-id:token-value"
}
```

---

### 7.2 KYC Operations (`/api/kyc`)

| Method | Endpoint | Auth | Role | Description |
|---|---|---|---|---|
| `POST` | `/api/kyc/upload` | Bearer | Self/Admin | Upload KYC document for verification |
| `GET` | `/api/kyc/status/{userId}` | Bearer | Self/Admin | Get latest KYC status for a user |
| `GET` | `/api/kyc/status/all/{userId}` | Bearer | Self/Admin | Get full KYC history for a user |
| `GET` | `/api/kyc/search` | Bearer | Admin | Search KYC requests with filters & pagination |
| `POST` | `/api/kyc/report` | Bearer | Admin | Trigger monthly KYC report email |

#### Upload — Request (`multipart/form-data`)
| Parameter | Type | Required | Description |
|---|---|---|---|
| `userId` | Long | Yes | Target user ID |
| `documentType` | Enum | Yes | `PAN`, `AADHAAR`, `PASSPORT`, `LICENSE`, `VOTER_ID`, `RATION_CARD` |
| `file` | File | Yes | Document image/PDF (max 10MB) |
| `documentNumber` | String | Yes | Official document number |

#### Upload — Response (`202 Accepted`)
```json
{
  "message": "KYC request submitted successfully",
  "requestId": 42
}
```

#### Search — Query Parameters
| Parameter | Type | Required | Description |
|---|---|---|---|
| `userId` | Long | No | Filter by user |
| `userName` | String | No | Partial name match |
| `status` | String | No | KYC status filter |
| `documentType` | String | No | Document type filter |
| `dateFrom` | DateTime | No | Start date |
| `dateTo` | DateTime | No | End date |
| `page`, `size`, `sort` | — | No | Pagination parameters |

---

### 7.3 User Management (`/api/users`)

| Method | Endpoint | Auth | Role | Description |
|---|---|---|---|---|
| `GET` | `/api/users` | Bearer | Admin | List all users |
| `GET` | `/api/users/{userId}` | Bearer | Self/Admin | Get user by ID |
| `PATCH` | `/api/users/{userId}` | Bearer | Self/Admin | Update user profile |
| `DELETE` | `/api/users/{userId}` | Bearer | Admin | Delete user |
| `GET` | `/api/users/search` | Bearer | Admin | Search users with filters |

---

### 7.4 Tenant Management (`/api/tenants`) — Super Admin Only

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/tenants` | Create new tenant (optionally provisions admin) |
| `GET` | `/api/tenants` | List all tenants (paginated) |
| `GET` | `/api/tenants/{tenantId}` | Get tenant details |
| `PATCH` | `/api/tenants/{tenantId}` | Update tenant configuration |
| `PATCH` | `/api/tenants/{tenantId}/deactivate` | Deactivate tenant (blocks all users) |
| `PATCH` | `/api/tenants/{tenantId}/activate` | Re-activate a deactivated tenant |
| `POST` | `/api/tenants/{tenantId}/rotate-api-key` | Generate new API key |
| `GET` | `/api/tenants/{tenantId}/stats` | Get KYC statistics for tenant |

---

## 8. Authentication & Security

### 8.1 JWT Authentication Flow

```
1. Client → POST /api/auth/login { email, password }
2. Server validates credentials, generates JWT with tenantId claim
3. Server returns { accessToken } + sets HttpOnly refresh cookie
4. Client includes "Authorization: Bearer <token>" on subsequent requests
5. JwtAuthenticationFilter extracts/validates token on each request
6. TenantResolutionFilter resolves tenantId from JWT claim
```

### 8.2 Token Specifications

| Property | Value |
|---|---|
| **Algorithm** | HMAC (via jjwt) |
| **Access Token Expiry** | 15 minutes (900,000 ms) |
| **Refresh Token Expiry** | 7 days (604,800,000 ms) |
| **Refresh Token Storage** | Redis |
| **Refresh Strategy** | Token rotation with family tracking |
| **Blacklist Storage** | Redis (via `TokenBlacklistService`) |

### 8.3 Role Hierarchy

```
ROLE_SUPER_ADMIN
    └── ROLE_TENANT_ADMIN
            └── ROLE_ADMIN
                    └── (ROLE_USER — implicit base)
```

- **SUPER_ADMIN** — Manages all tenants; bypasses tenant scoping
- **TENANT_ADMIN** — Manages their tenant's users and configuration
- **ADMIN** — Manages KYC operations, generates reports, searches users
- **USER** — Submits KYC documents and views own status

### 8.4 Security Configuration

- **CSRF** — Disabled (stateless JWT APIs)
- **Session** — Stateless (`SessionCreationPolicy.STATELESS`)
- **Password Encoding** — BCrypt
- **Method Security** — `@PreAuthorize` with SpEL (`@securityService.canAccessUser()`)
- **Custom Error Handlers** — `CustomAuthenticationEntryPoint` (401), `CustomAccessDeniedHandler` (403)

### 8.5 Password Reset Flow

```
1. POST /api/auth/forgot-password { email }
   → Sends 6-character token to email (valid for 15 minutes)
2. POST /api/auth/change-password { email, token, newPassword, confirmPassword }
   → Validates token and updates password
```

---

## 9. Multi-Tenancy

### 9.1 Strategy

The system uses a **shared database, shared schema** model with a `tenant_id` discriminator column present on all main entities (`User`, `KycRequest`, `KycDocument`, `AuditLog`).

### 9.2 Tenant Resolution — Priority Order

| Priority | Source | Use Case |
|---|---|---|
| 1 | **JWT `tenantId` claim** | Authenticated users |
| 2 | **`X-Tenant-ID` header** | API-key based access |
| 3 | **`X-API-Key` header** | External system integration (resolves to tenant) |

### 9.3 Tenant Context

```java
// ThreadLocal-based tenant propagation
TenantContext.setTenant("acme");        // Set for current thread
TenantContext.getTenant();              // Read in service layer
TenantContext.isSuperAdmin();           // Check bypass flag
TenantContext.clear();                  // Cleanup (always in finally)
```

- Uses `InheritableThreadLocal` — propagates tenant to child threads (e.g., `KycWorker` async processing)
- Superadmin sets `TenantContext.SUPER_ADMIN_TENANT` which bypasses tenant scoping

### 9.4 Excluded Paths (No Tenant Required)

- `/api/auth/login`
- `/api/auth/forgot-password`
- `/api/auth/change-password`
- `/swagger-ui/**`
- `/v3/api-docs/**`

### 9.5 Tenant Lifecycle

1. **Creation** — Super Admin creates tenant via `POST /api/tenants` with optional admin provisioning
2. **Configuration** — Set `maxDailyAttempts`, `allowedDocumentTypes`, `plan`
3. **API Key** — Auto-generated; can be rotated via `POST /api/tenants/{id}/rotate-api-key`
4. **Deactivation** — Blocks all login/access for that tenant's users immediately
5. **Re-activation** — Restores access

---

## 10. KYC Processing Pipeline

### 10.1 End-to-End Flow

```
┌─────────┐    ┌──────────────────────┐    ┌──────────────┐    ┌───────────────┐
│ UPLOAD  │───▶│ SUBMIT (Sync)        │───▶│ QUEUE.push() │───▶│ WORKER (Async)│
│ API call│    │ • Duplicate check    │    │ In-memory    │    │ 10 threads    │
│         │    │ • Create KycRequest  │    │ BlockingQueue│    │               │
│         │    │ • Save document file │    │              │    │               │
└─────────┘    │ • Status: SUBMITTED  │    └──────────────┘    └───────┬───────┘
               └──────────────────────┘                                │
                                                                       ▼
                                                        ┌──────────────────────┐
                                                        │ PROCESS ASYNC        │
                                                        │ 1. CAS: SUBMITTED →  │
                                                        │    PROCESSING        │
                                                        │ 2. Fetch document    │
                                                        │    metadata          │
                                                        │ 3. OCR (Tesseract)   │
                                                        │    — NO TRANSACTION  │
                                                        │ 4. Save extracted    │
                                                        │    data              │
                                                        │ 5. Verify (name,     │
                                                        │    DOB, doc number)  │
                                                        │ 6. Status: VERIFIED  │
                                                        │    or FAILED         │
                                                        └──────────────────────┘
```

### 10.2 Status Lifecycle

```
PENDING → SUBMITTED → PROCESSING → VERIFIED
                          │
                          └──────→ FAILED
```

### 10.3 Concurrency Safeguards

| Mechanism | Purpose |
|---|---|
| **Optimistic Locking** (`@Version`) | Prevents conflicting writes on `KycRequest` |
| **CAS Update** (`updateStatusIfPending`) | Atomic state transition, prevents duplicate processing |
| **Unique Partial Index** | `unique_active_kyc` on `(user_id, document_type)` where status is active — prevents duplicate submissions |
| **Transaction Boundaries** | Short transactions around DB ops; OCR runs outside any transaction |

### 10.4 Duplicate Prevention

- If a document is already **VERIFIED** for a user, re-upload is blocked with a message
- Active (PENDING/SUBMITTED/PROCESSING) requests are enforced unique per `(user_id, document_type)` via a partial DB index

---

## 11. OCR & Document Verification

### 11.1 OCR Engine

- **Tesseract** via `Tess4j 5.11.0`
- Tessdata path: `/usr/share/tesseract-ocr/5/tessdata`
- Supports: PDF, JPEG, PNG (up to 10MB)

### 11.2 Supported Document Types

| Document | Enum Value |
|---|---|
| PAN Card | `PAN` |
| Aadhaar Card | `AADHAAR` |
| Passport | `PASSPORT` |
| Driving License | `LICENSE` |
| Voter ID | `VOTER_ID` |
| Ration Card | `RATION_CARD` |

### 11.3 Extraction & Verification

The `KycExtractionService` extracts structured data (name, DOB, document number) from OCR raw text. The `KycVerificationService` then compares extracted data against the user's profile:

| Check | Field | Criteria |
|---|---|---|
| **Name Match** | `extractedName` vs `user.name` | Scored 0–100 (BigDecimal) |
| **DOB Match** | `extractedDob` vs `user.dob` | Boolean exact match |
| **Document Number** | `extractedDocumentNumber` vs submitted `documentNumber` | Boolean exact match |

### 11.4 Data Security

- **Document numbers** are encrypted at rest using AES (`KycEncryptionConverter`)
- **Admin viewing** masks document numbers (e.g., `XXXX1234`) via `MaskingUtil`
- **File integrity** is tracked via SHA-256 hash (`documentHash`)

---

## 12. Reporting & Scheduling

### 12.1 Monthly KYC Report

- **Schedule:** 8:00 AM IST on the 1st of every month (`0 0 8 1 * *`)
- **Content:** Monthly KYC statistics (generated by `KycReportService`)
- **Delivery:** Email via `KycReportEmailServiceImpl`
- **Recipients:** Configured via `kyc.report.recipients` property

### 12.2 Manual Report Trigger

Admins can trigger ad-hoc reports:

```
POST /api/kyc/report?yearMonth=2025-01
Authorization: Bearer <admin-token>
```

If `yearMonth` is omitted, the current month is used.

---

## 13. Configuration Reference

### `application.properties`

| Property | Default | Description |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5433/kyc_db_copy` | PostgreSQL connection URL |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Schema validated by Flyway |
| `spring.jpa.open-in-view` | `false` | Disabled for performance |
| `kyc.storage.base-path` | `/data/kyc` | File upload storage directory |
| `kyc.file.max-size` | `10MB` | Max upload file size |
| `kyc.file.allowed-types` | `application/pdf,image/jpeg,image/png` | Allowed MIME types |
| `tesseract.datapath` | `/usr/share/tesseract-ocr/5/tessdata` | Tesseract OCR data path |
| `app.encryption-secret` | *(configured)* | AES-256 encryption key (32 chars) |
| `app.jwt-secret` | *(configured)* | Base64-encoded JWT signing key |
| `app.jwt-expiration-milliseconds` | `900000` | Access token TTL (15 min) |
| `app.jwt-refresh-expiration-milliseconds` | `604800000` | Refresh token TTL (7 days) |
| `spring.data.redis.host` | `localhost` | Redis host |
| `spring.data.redis.port` | `6379` | Redis port |
| `kyc.report.recipients` | *(configured)* | Comma-separated report email addresses |

---

## 14. Database Migrations

The system uses **Flyway** for schema version control. Migrations are located in `src/main/resources/db/migration/`.

| Version | File | Description |
|---|---|---|
| V1 | `V1__baseline.sql` | Initial schema (users, roles, KYC tables) |
| V2 | `V2__add_tenants_table.sql` | Adds `tenants` table |
| V3 | `V3__add_tenant_id_columns.sql` | Adds `tenant_id` to existing tables |
| V4 | `V4__backfill_default_tenant.sql` | Backfills existing data with `"default"` tenant |
| V5 | `V5__add_tenant_constraints.sql` | Adds NOT NULL and FK constraints |
| V6 | `V6__fix_audit_logs_tenant_constraint.sql` | Fixes audit_logs tenant constraint |

### Runtime Index

The `DataInitializer` creates a partial unique index at startup:

```sql
CREATE UNIQUE INDEX IF NOT EXISTS unique_active_kyc
ON kyc_requests(user_id, document_type)
WHERE status IN ('PENDING', 'SUBMITTED', 'PROCESSING');
```

---

## 15. Getting Started

### Prerequisites

- **Java 21** (JDK)
- **PostgreSQL** (running on port 5433)
- **Redis** (running on port 6379)
- **Tesseract OCR** installed with English language pack
- **Maven** 3.x

### Setup Steps

```bash
# 1. Clone the repository
git clone <repo-url>
cd kyc_system

# 2. Create the database
psql -U test_user -h localhost -p 5433 -c "CREATE DATABASE kyc_db_copy;"

# 3. Run the application (Flyway auto-migrates)
./mvnw spring-boot:run

# 4. Access Swagger UI
open http://localhost:8080/swagger-ui.html
```

### Startup Behaviour (`DataInitializer`)

On first launch, the application automatically:

1. **Creates roles:** `ROLE_SUPER_ADMIN`, `ROLE_TENANT_ADMIN`, `ROLE_USER`
2. **Creates `system` tenant** (for superadmin)
3. **Creates superadmin user** (`superadmin@kyc.com`)
4. **Creates `default` tenant** (for regular operations)
5. **Creates default admin** (`admin@kyc.com`)
6. **Creates unique partial index** on `kyc_requests`

---

## 16. Default Credentials

| User | Email | Password | Role | Tenant |
|---|---|---|---|---|
| Super Admin | `superadmin@kyc.com` | `SuperAdmin@123` | `ROLE_SUPER_ADMIN` | `system` |
| Default Admin | `admin@kyc.com` | `Password@123` | `ROLE_TENANT_ADMIN` | `default` |

> ⚠️ **Important:** Change these credentials in production environments.

---

## Appendix: Audit Logging

All significant operations are tracked in the `audit_logs` table with:
- **Entity type and ID** — What was affected
- **Action** — What happened
- **Performer** — Who did it
- **Old/New values** — Stored as PostgreSQL `jsonb`
- **IP address and User-Agent** — Request metadata
- **Tenant ID** — Multi-tenant scoping
- **Correlation/Request ID** — For distributed tracing

---