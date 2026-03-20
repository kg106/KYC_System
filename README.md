# KYC System

A robust, enterprise-ready **Know Your Customer (KYC)** management system built with **Spring Boot** and **PostgreSQL**. This application features a multi-tenant architecture, secure document handling, OCR-based data extraction, and a full observability stack.

---

## 🚀 Features

*   **Multi-tenant Architecture**: Isolated data and configuration per tenant (JWT or Header based).
*   **Secure Authentication**: JWT-based stateless authentication with token blacklisting.
*   **Role-Based Access Control (RBAC)**: Comprehensive role hierarchy (`SUPER_ADMIN`, `TENANT_ADMIN`, `ADMIN`, `USER`).
*   **KYC Document Management**:
    *   Upload Identity Proofs (Passport, PAN, Aadhaar, etc.).
    *   OCR Data Extraction using **Tesseract**.
    *   Automated validation and masking of sensitive info for admins.
*   **Observability Stack**: Integrated with **Prometheus**, **Grafana**, **Loki**, and **Promtail**.
*   **Advanced Reporting**: Automated and manual KYC stats reporting with email integration.
*   **Dynamic Search & Filtering**: Rich search capabilities for KYC requests with pagination.
*   **Concurrency Handling**: Robust handling of simultaneous requests using optimistic locking.
*   **Audit & Logging**: Detailed logs for all critical system actions.

---

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

---

## 🛠️ Tech Stack

*   **Language**: Java 21
*   **Framework**: Spring Boot 3.4.2
*   **Database**: PostgreSQL
*   **Caching/Session**: Redis
*   **OCR Engine**: Tesseract OCR
*   **Monitoring**: Prometheus, Grafana, Loki, Promtail
*   **Build Tool**: Maven
*   **Security**: Spring Security 6 (JWT, RBAC, AES-256 Encryption)

---

## 📋 Prerequisites

1.  **Java 21 SDK** (for local development)
2.  **Docker & Docker Compose** (recommended)
3.  **PostgreSQL** (if running locally without Docker)
4.  **Tesseract OCR** (`sudo apt-get install tesseract-ocr`)

---

## ⚙️ Configuration

1.  Copy `.env.example` to `.env`:
    ```bash
    cp .env.example .env
    ```
2.  Update the values in `.env` with your specific configuration (Database, JWT secrets, Mail settings).

---

## 🏃‍♂️ Running the Application

### Using Docker (Recommended)

The easiest way to run the entire stack is using Docker Compose. Use the provided `Makefile` for convenience:

```bash
make build   # Build the Docker image
make up      # Start all services (App, DB, Redis, Monitoring)
```

**Access Points:**
*   **Application**: `http://localhost:8080`
*   **Swagger UI**: `http://localhost:8080/swagger-ui.html`
*   **Prometheus**: `http://localhost:9090`
*   **Grafana**: `http://localhost:3000` (Login: `admin/admin`)
*   **Loki**: `http://localhost:3100`

### Running Locally

1.  **Build**: `./mvnw clean install`
2.  **Run**: `./mvnw spring-boot:run`

---

## 🛠️ Makefile Commands

| Command | Description |
| :--- | :--- |
| `make build` | Build the application Docker image |
| `make up` | Start all services (detached) |
| `make down` | Stop and remove all containers |
| `make restart` | Restart the application container |
| `make logs` | Tail logs from all services |
| `make monitoring` | Start only the monitoring stack (Grafana, Loki, etc.) |
| `make status` | Show status of running containers |
| `make clean` | Remove containers, volumes, and images |

---

## 📊 Monitoring & Observability

The application is instrumented for comprehensive monitoring:
- **Metrics**: Exported via Micrometer to Prometheus.
- **Logs**: Gathered by Promtail and aggregated in Loki.
- **Dashboards**: Pre-configured Grafana dashboards for visualizing system health and logs.

---

## 🧪 Testing

### Unit & Integration Tests
Run the full test suite (including 16+ integration tests):
```bash
./mvnw test
```

### Concurrency Testing
Execute the concurrency simulation script to verify optimistic locking:
```bash
./kyc_concurrency_test.sh
```

---

## 🔒 Security

*   **BCrypt**: Password hashing.
*   **AES-256**: Sensitive data encryption for stored KYC documents.
*   **JWT**: Stateless auth with tenant-scoped claims.
*   **Rate Limiting**: (Planned/Implemented via Spring Security/Redis).
