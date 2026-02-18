# KYC System

A robust **Know Your Customer (KYC)** management system built with **Spring Boot** and **PostgreSQL**. This application handles user registration, secure document uploads, OCR-based data extraction, and automated validation workflows.

## 🚀 Features

*   **Secure Authentication**: JWT-based stateless authentication (Login/Register).
*   **Role-Based Access Control (RBAC)**: Separate flows for Users and Admins.
*   **KYC Document Management**:
    *   Upload Identity Proofs (Passport, PAN, Aadhaar, etc.).
    *   Automatic Document Type Verification.
    *   OCR Data Extraction using **Tesseract**.
*   **Concurrency Handling**: Robust handling of simultaneous requests to prevent race conditions.
*   **Audit & Logging**: Detailed logs for all critical actions.
*   **Email Notifications**: SMTP integration for account alerts (Gmail).
*   **Rate Limiting**: Protection against abuse.

## 🛠️ Tech Stack

*   **Language**: Java 21
*   **Framework**: Spring Boot 3.4.2
*   **Database**: PostgreSQL
*   **OCR Engine**: Tesseract OCR
*   **Build Tool**: Maven
*   **Testing**: JUnit 5, Mockito, Python (for concurrency tests)

## 📋 Prerequisites

Ensure you have the following installed before running the application:

1.  **Java 21 SDK**
2.  **PostgreSQL** (Running on port `5433` by default)
3.  **Tesseract OCR**
    *   **Linux**: `sudo apt-get install tesseract-ocr`
    *   **Mac**: `brew install tesseract`
    *   **Windows**: Download installer from UB-Mannheim/tesseract/wiki

## ⚙️ Configuration

The application is configured via `src/main/resources/application.properties`.

### Database Configuration
```properties
spring.datasource.url=jdbc:postgresql://localhost:5433/kyc_db_copy
spring.datasource.username=test_user
spring.datasource.password=system
```

### Tesseract Path (Linux Default)
```properties
tesseract.datapath=/usr/share/tesseract-ocr/5/tessdata
```

### Mail Configuration
Configured to use Gmail SMTP on port `587`.

## 🏃‍♂️ Installation & Running

1.  **Clone the repository**:
    ```bash
    git clone <repository-url>
    cd kyc_system
    ```

2.  **Build the project**:
    ```bash
    ./mvnw clean install
    ```

3.  **Run the application**:
    ```bash
    ./mvnw spring-boot:run
    ```

The application will start on `http://localhost:8080`.

## 📖 API Documentation

The project includes Swagger UI for interactive API documentation.

*   **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
*   **OpenAPI JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### Key Endpoints
*   `POST /api/auth/register` - Register a new user
*   `POST /api/auth/login` - Login and get JWT
*   `POST /api/kyc/upload` - Upload KYC document
*   `GET /api/kyc/status` - Check KYC status

## 🧪 Testing

### Unit Reviews
Run standard Spring Boot unit tests:
```bash
./mvnw test
```

### Concurrency Testing
A Python script is provided to test system behavior under load (Race Conditions, Optimistic Locking).

1.  Install Python dependencies:
    ```bash
    pip install requests
    ```
2.  Run the test script:
    ```bash
    python3 concurrent_kyc_test.py
    ```

## 🔒 Security

*   **BCrypt** for password hashing.
*   **AES-256** (configured via `app.encryption-secret`) for sensitive data encryption.
*   **JWT** tokens with configurable expiration (`app.jwt-expiration-milliseconds`).
