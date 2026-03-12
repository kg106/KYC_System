# KYC System

A robust, enterprise-ready **Know Your Customer (KYC)** management system built with **Spring Boot** and **PostgreSQL**. This application features a multi-tenant architecture, secure document handling, OCR-based data extraction, and deep audit capabilities.

## 🚀 Features

*   **Multi-tenant Architecture**: Isolated data and configuration per tenant.
*   **Secure Authentication**: JWT-based stateless authentication with token blacklisting.
*   **Role-Based Access Control (RBAC)**: Comprehensive role hierarchy (`SUPER_ADMIN`, `TENANT_ADMIN`, `ADMIN`, `USER`).
*   **KYC Document Management**:
    *   Upload Identity Proofs (Passport, PAN, Aadhaar, etc.).
    *   OCR Data Extraction using **Tesseract**.
    *   Automated validation and masking of sensitive info for admins.
*   **Advanced Reporting**: Automated and manual KYC stats reporting with email integration.
*   **Dynamic Search & Filtering**: Rich search capabilities for KYC requests with pagination.
*   **Concurrency Handling**: Robust handling of simultaneous requests using optimistic locking.
*   **Audit & Logging**: Detailed logs for all critical system actions.

## 🏗️ Multi-Tenancy & Security

The system supports multiple resolution strategies for tenant isolation:
1.  **JWT Claim**: `tenantId` is embedded in the JWT for authenticated users.
2.  **X-Tenant-ID Header**: Used for tenant-scoped operations.
3.  **X-API-Key Header**: Allows external integrations to access tenant data securely.

**Role Hierarchy:**
- `ROLE_SUPER_ADMIN`: Global access across all tenants. Manages tenant lifecycles.
- `ROLE_TENANT_ADMIN`: Full access to users and data within their specific tenant.
- `ROLE_ADMIN`: Operational access within a tenant (view/search KYC).
- `ROLE_USER`: Access only to their own profile and KYC documents.

## 🛠️ Tech Stack

*   **Language**: Java 21
*   **Framework**: Spring Boot 3.4.2
*   **Database**: PostgreSQL
*   **OCR Engine**: Tesseract OCR
*   **Build Tool**: Maven
*   **Security**: Spring Security 6 (JWT, RBAC, AES-256 Encryption)

## 📋 Prerequisites

1.  **Java 21 SDK**
2.  **PostgreSQL** (Running on port `5433` by default)
3.  **Tesseract OCR** (`sudo apt-get install tesseract-ocr`)

## ⚙️ Configuration

Configured via `src/main/resources/application.properties`.

### Database
```properties
spring.datasource.url=jdbc:postgresql://localhost:5433/kyc_db_copy
spring.datasource.username=test_user
spring.datasource.password=system
```

### OCR (Linux Default)
```properties
tesseract.datapath=/usr/share/tesseract-ocr/5/tessdata
```

## 🏃‍♂️ Installation & Running

1.  **Build**: `./mvnw clean install`
2.  **Run**: `./mvnw spring-boot:run`

The application starts on `http://localhost:8080`.

## 📖 API Documentation

*   **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
*   **OpenAPI JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### Key Endpoint Groups
*   `POST /api/auth/` - Authentication (Register, Login, Logout)
*   `POST /api/tenants/` - Tenant Management (Super Admin only)
*   `POST /api/kyc/upload` - KYC Submission
*   `GET  /api/kyc/search` - Advanced KYC Search (Admin)
*   `POST /api/kyc/report` - Manual Report Trigger

## 🧪 Testing

*   **Unit Tests**: `./mvnw test`

## 🔒 Security

*   **BCrypt**: Password hashing.
*   **AES-256**: Sensitive data encryption.
*   **JWT**: Stateless auth with tenant-scoped claims.
