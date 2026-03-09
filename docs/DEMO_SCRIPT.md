# KYC System — Demo Script (Dummy Data & All Scenarios)

> **Base URL:** `http://localhost:8080`  
> **Pre-seeded accounts:** `superadmin@kyc.com / SuperAdmin@123` (system), `admin@kyc.com / Password@123` (default)  
> **Pre-requisites:** Application running, PostgreSQL & Redis up, Tesseract installed  
> **Tip:** Run each section in order — later sections depend on data created in earlier ones.

---

## 📋 Variable Cheatsheet

Throughout this script, replace variables like `$SA_TOKEN`, `$ACME_ADMIN_TOKEN`, etc., with the actual values from the responses.

```
$SA_TOKEN           → Super Admin access token
$ACME_ADMIN_TOKEN   → Acme Bank tenant admin token
$DEFAULT_ADMIN_TOKEN→ Default tenant admin token
$USER_TOKEN         → Regular user token
$USER_ID            → Regular user's ID
$ACME_USER_TOKEN    → Acme tenant regular user token
$ACME_USER_ID       → Acme tenant regular user's ID
```

---

## 1️⃣ Authentication — Super Admin Login

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "superadmin@kyc.com",
    "password": "SuperAdmin@123"
  }' | jq .
```

> 💾 Save `accessToken` as `$SA_TOKEN`

---

## 2️⃣ Multi-Tenancy — Tenant Management (Super Admin)

### 2.1 Create Tenant: Acme Bank (with auto-provisioned admin)

```bash
curl -s -X POST http://localhost:8080/api/tenants \
  -H "Authorization: Bearer $SA_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "acme_bank",
    "name": "Acme Bank Limited",
    "email": "kyc-ops@acmebank.com",
    "maxDailyAttempts": 10,
    "allowedDocumentTypes": ["PAN", "AADHAAR", "PASSPORT"],
    "adminEmail": "admin@acmebank.com",
    "adminPassword": "AcmeAdmin@123"
  }' | jq .
```

### 2.2 Create Tenant: Nova Finance (without admin — minimal setup)

```bash
curl -s -X POST http://localhost:8080/api/tenants \
  -H "Authorization: Bearer $SA_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "nova_finance",
    "name": "Nova Finance Pvt Ltd",
    "email": "compliance@novafinance.in",
    "maxDailyAttempts": 3,
    "allowedDocumentTypes": ["PAN", "AADHAAR"]
  }' | jq .
```

### 2.3 List All Tenants (paginated)

```bash
curl -s -X GET "http://localhost:8080/api/tenants?page=0&size=10" \
  -H "Authorization: Bearer $SA_TOKEN" | jq .
```

### 2.4 Get Specific Tenant Details

```bash
curl -s -X GET http://localhost:8080/api/tenants/acme_bank \
  -H "Authorization: Bearer $SA_TOKEN" | jq .
```

### 2.5 Update Tenant Configuration

```bash
curl -s -X PATCH http://localhost:8080/api/tenants/acme_bank \
  -H "Authorization: Bearer $SA_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Acme Bank International",
    "maxDailyAttempts": 15,
    "allowedDocumentTypes": ["PAN", "AADHAAR", "PASSPORT", "LICENSE"]
  }' | jq .
```

### 2.6 Rotate API Key

```bash
curl -s -X POST http://localhost:8080/api/tenants/acme_bank/rotate-api-key \
  -H "Authorization: Bearer $SA_TOKEN" | jq .
```

### 2.7 Deactivate Tenant (blocks all users)

```bash
curl -s -X PATCH http://localhost:8080/api/tenants/nova_finance/deactivate \
  -H "Authorization: Bearer $SA_TOKEN"
```

> **Expected:** `"Tenant deactivated: nova_finance"`

### 2.8 Deactivate Again — Idempotent Check

```bash
curl -s -X PATCH http://localhost:8080/api/tenants/nova_finance/deactivate \
  -H "Authorization: Bearer $SA_TOKEN"
```

> **Expected:** `"Tenant is already inactive: nova_finance"`

### 2.9 Re-Activate Tenant

```bash
curl -s -X PATCH http://localhost:8080/api/tenants/nova_finance/activate \
  -H "Authorization: Bearer $SA_TOKEN"
```

> **Expected:** `"Tenant activated: nova_finance"`

### 2.10 Get Tenant Stats

```bash
curl -s -X GET http://localhost:8080/api/tenants/acme_bank/stats \
  -H "Authorization: Bearer $SA_TOKEN" | jq .
```

---

## 3️⃣ Authentication — Tenant Admin Login

### 3.1 Acme Bank Admin Login

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@acmebank.com",
    "password": "AcmeAdmin@123"
  }' | jq .
```

> 💾 Save `accessToken` as `$ACME_ADMIN_TOKEN`

### 3.2 Default Tenant Admin Login

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@kyc.com",
    "password": "Password@123"
  }' | jq .
```

> 💾 Save `accessToken` as `$DEFAULT_ADMIN_TOKEN`

---

## 4️⃣ User Management — Registration & CRUD

### 4.1 Register Regular User (Default Tenant)

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: default" \
  -d '{
    "name": "Rahul Sharma",
    "email": "rahul.sharma@gmail.com",
    "mobileNumber": "9876543210",
    "password": "Rahul@12345",
    "dob": "1995-06-15"
  }' | jq .
```

> 💾 Save `id` as `$USER_ID`

### 4.2 Register Another User (Default Tenant)

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: default" \
  -d '{
    "name": "Priya Patel",
    "email": "priya.patel@yahoo.com",
    "mobileNumber": "8765432109",
    "password": "Priya@54321",
    "dob": "1992-11-20"
  }' | jq .
```

### 4.3 Register User Under Acme Bank Tenant

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: acme_bank" \
  -d '{
    "name": "Amit Kumar",
    "email": "amit.kumar@outlook.com",
    "mobileNumber": "7654321098",
    "password": "Amit@67890",
    "dob": "1988-03-25"
  }' | jq .
```

> 💾 Save `id` as `$ACME_USER_ID`

### 4.4 Register User Under Acme Bank (second user)

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: acme_bank" \
  -d '{
    "name": "Sneha Reddy",
    "email": "sneha.reddy@gmail.com",
    "mobileNumber": "6543210987",
    "password": "Sneha@11223",
    "dob": "2000-08-10"
  }' | jq .
```

### 4.5 Login as Regular User

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "rahul.sharma@gmail.com",
    "password": "Rahul@12345"
  }' | jq .
```

> 💾 Save `accessToken` as `$USER_TOKEN`

### 4.6 Login as Acme Regular User

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "amit.kumar@outlook.com",
    "password": "Amit@67890"
  }' | jq .
```

> 💾 Save `accessToken` as `$ACME_USER_TOKEN`

### 4.7 Get Own Profile (Self-Service)

```bash
curl -s -X GET http://localhost:8080/api/users/$USER_ID \
  -H "Authorization: Bearer $USER_TOKEN" | jq .
```

### 4.8 Update Own Profile (Self-Service)

```bash
curl -s -X PATCH http://localhost:8080/api/users/$USER_ID \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Rahul K Sharma",
    "mobileNumber": "9876543211"
  }' | jq .
```

### 4.9 List All Users (Admin)

```bash
curl -s -X GET http://localhost:8080/api/users \
  -H "Authorization: Bearer $DEFAULT_ADMIN_TOKEN" | jq .
```

### 4.10 Search Users with Filters (Admin)

```bash
# Search by name
curl -s -X GET "http://localhost:8080/api/users/search?name=Rahul&page=0&size=5" \
  -H "Authorization: Bearer $DEFAULT_ADMIN_TOKEN" | jq .

# Search by active status
curl -s -X GET "http://localhost:8080/api/users/search?isActive=true&page=0&size=10" \
  -H "Authorization: Bearer $DEFAULT_ADMIN_TOKEN" | jq .

# Search by email pattern
curl -s -X GET "http://localhost:8080/api/users/search?email=gmail.com&page=0&size=10" \
  -H "Authorization: Bearer $DEFAULT_ADMIN_TOKEN" | jq .
```

### 4.11 Admin Updates a User's Profile

```bash
curl -s -X PATCH http://localhost:8080/api/users/$USER_ID \
  -H "Authorization: Bearer $DEFAULT_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "isActive": false
  }' | jq .
```

> **Scenario:** Admin deactivates a user

### 4.12 Re-Activate User

```bash
curl -s -X PATCH http://localhost:8080/api/users/$USER_ID \
  -H "Authorization: Bearer $DEFAULT_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "isActive": true
  }' | jq .
```

---

## 5️⃣ KYC Document Upload & Processing

> **⚠️ Note:** For the demo, create a sample document image first:
> ```bash
> # Create a dummy PAN card image for testing
> convert -size 400x250 xc:white \
>   -font Helvetica -pointsize 20 \
>   -draw "text 30,40 'INCOME TAX DEPARTMENT'" \
>   -draw "text 30,80 'PERMANENT ACCOUNT NUMBER'" \
>   -draw "text 30,120 'ABCPK1234R'" \
>   -draw "text 30,160 'Name: Rahul Sharma'" \
>   -draw "text 30,200 'DOB: 15/06/1995'" \
>   /tmp/pan_card_rahul.png
>
> # Create a dummy Aadhaar card image
> convert -size 400x250 xc:white \
>   -font Helvetica -pointsize 20 \
>   -draw "text 30,40 'GOVERNMENT OF INDIA'" \
>   -draw "text 30,80 'AADHAAR'" \
>   -draw "text 30,120 '1234 5678 9012'" \
>   -draw "text 30,160 'Name: Amit Kumar'" \
>   -draw "text 30,200 'DOB: 25/03/1988'" \
>   /tmp/aadhaar_amit.png
> ```

### 5.1 Upload PAN Card — Regular User (Default Tenant)

```bash
curl -s -X POST http://localhost:8080/api/kyc/upload \
  -H "Authorization: Bearer $USER_TOKEN" \
  -F "userId=$USER_ID" \
  -F "documentType=PAN" \
  -F "documentNumber=ABCPK1234R" \
  -F "file=@/tmp/pan_card_rahul.png" | jq .
```

> **Expected:** `202 Accepted` — `"KYC request submitted successfully"`

### 5.2 Upload Aadhaar — Acme Tenant User

```bash
curl -s -X POST http://localhost:8080/api/kyc/upload \
  -H "Authorization: Bearer $ACME_USER_TOKEN" \
  -F "userId=$ACME_USER_ID" \
  -F "documentType=AADHAAR" \
  -F "documentNumber=123456789012" \
  -F "file=@/tmp/aadhaar_amit.png" | jq .
```

### 5.3 Check KYC Status (Latest)

```bash
# Wait ~5 seconds for async processing, then:
curl -s -X GET http://localhost:8080/api/kyc/status/$USER_ID \
  -H "Authorization: Bearer $USER_TOKEN" | jq .
```

### 5.4 Get Full KYC History

```bash
curl -s -X GET http://localhost:8080/api/kyc/status/all/$USER_ID \
  -H "Authorization: Bearer $USER_TOKEN" | jq .
```

### 5.5 ❌ Duplicate Upload — Same Document Type (Should Fail)

```bash
curl -s -X POST http://localhost:8080/api/kyc/upload \
  -H "Authorization: Bearer $USER_TOKEN" \
  -F "userId=$USER_ID" \
  -F "documentType=PAN" \
  -F "documentNumber=ABCPK1234R" \
  -F "file=@/tmp/pan_card_rahul.png" | jq .
```

> **Expected:** Error — duplicate active KYC request or already verified

### 5.6 Upload Different Document Type (Aadhaar for same user)

```bash
curl -s -X POST http://localhost:8080/api/kyc/upload \
  -H "Authorization: Bearer $USER_TOKEN" \
  -F "userId=$USER_ID" \
  -F "documentType=AADHAAR" \
  -F "documentNumber=987654321098" \
  -F "file=@/tmp/aadhaar_amit.png" | jq .
```

> **Expected:** `202 Accepted` — different doc type is allowed

### 5.7 ❌ Upload Oversized File (Should Fail — >10MB)

```bash
# Create a large dummy file
dd if=/dev/zero of=/tmp/large_file.png bs=1M count=11 2>/dev/null

curl -s -X POST http://localhost:8080/api/kyc/upload \
  -H "Authorization: Bearer $USER_TOKEN" \
  -F "userId=$USER_ID" \
  -F "documentType=PASSPORT" \
  -F "documentNumber=K1234567" \
  -F "file=@/tmp/large_file.png" | jq .
```

> **Expected:** Error — file exceeds max size

### 5.8 ❌ Upload Invalid File Type (Should Fail)

```bash
echo "This is not an image" > /tmp/fake_doc.txt

curl -s -X POST http://localhost:8080/api/kyc/upload \
  -H "Authorization: Bearer $USER_TOKEN" \
  -F "userId=$USER_ID" \
  -F "documentType=PASSPORT" \
  -F "documentNumber=K1234567" \
  -F "file=@/tmp/fake_doc.txt" | jq .
```

> **Expected:** Error — invalid file type

---

## 6️⃣ KYC Search (Admin)

### 6.1 Search All KYC Requests

```bash
curl -s -X GET "http://localhost:8080/api/kyc/search?page=0&size=10" \
  -H "Authorization: Bearer $DEFAULT_ADMIN_TOKEN" | jq .
```

### 6.2 Search by Status

```bash
curl -s -X GET "http://localhost:8080/api/kyc/search?status=VERIFIED&page=0&size=10" \
  -H "Authorization: Bearer $DEFAULT_ADMIN_TOKEN" | jq .

curl -s -X GET "http://localhost:8080/api/kyc/search?status=FAILED&page=0&size=10" \
  -H "Authorization: Bearer $DEFAULT_ADMIN_TOKEN" | jq .
```

### 6.3 Search by Document Type

```bash
curl -s -X GET "http://localhost:8080/api/kyc/search?documentType=PAN&page=0&size=10" \
  -H "Authorization: Bearer $DEFAULT_ADMIN_TOKEN" | jq .
```

### 6.4 Search by User ID

```bash
curl -s -X GET "http://localhost:8080/api/kyc/search?userId=$USER_ID&page=0&size=10" \
  -H "Authorization: Bearer $DEFAULT_ADMIN_TOKEN" | jq .
```

### 6.5 Combined Filters (Status + Document Type + Date Range)

```bash
curl -s -X GET "http://localhost:8080/api/kyc/search?status=VERIFIED&documentType=PAN&dateFrom=2026-01-01T00:00:00&dateTo=2026-12-31T23:59:59&page=0&size=10" \
  -H "Authorization: Bearer $DEFAULT_ADMIN_TOKEN" | jq .
```

### 6.6 Acme Admin — Can Only See Acme Tenant Data (Tenant Isolation)

```bash
curl -s -X GET "http://localhost:8080/api/kyc/search?page=0&size=10" \
  -H "Authorization: Bearer $ACME_ADMIN_TOKEN" | jq .
```

> **Expected:** Only shows Acme Bank's KYC requests, NOT default tenant's

---

## 7️⃣ Password Reset Flow

### 7.1 Step 1 — Request Reset Token

```bash
curl -s -X POST http://localhost:8080/api/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{
    "email": "rahul.sharma@gmail.com"
  }'
```

> **Expected:** Reset token sent via email (check email/logs for the 6-char token)

### 7.2 Step 2 — Reset Password

```bash
curl -s -X POST http://localhost:8080/api/auth/change-password \
  -H "Content-Type: application/json" \
  -d '{
    "email": "rahul.sharma@gmail.com",
    "token": "REPLACE_WITH_ACTUAL_TOKEN",
    "newPassword": "NewRahul@123",
    "confirmPassword": "NewRahul@123"
  }'
```

> **Expected:** `"Password successfully reset"`

### 7.3 Login with New Password

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "rahul.sharma@gmail.com",
    "password": "NewRahul@123"
  }' | jq .
```

---

## 8️⃣ Token Management — Refresh & Logout

### 8.1 Refresh Token (uses HttpOnly cookie)

```bash
curl -s -X POST http://localhost:8080/api/auth/refresh \
  -H "Cookie: refreshToken=$REFRESH_TOKEN_VALUE" | jq .
```

> **Expected:** New access token with rotated refresh token

### 8.2 Logout

```bash
curl -s -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Cookie: refreshToken=$REFRESH_TOKEN_VALUE"
```

> **Expected:** `"Logged out successfully"` — access token blacklisted, refresh family revoked

### 8.3 ❌ Use Blacklisted Token (Should Fail)

```bash
curl -s -X GET http://localhost:8080/api/users/$USER_ID \
  -H "Authorization: Bearer $USER_TOKEN" | jq .
```

> **Expected:** 401 Unauthorized — token is blacklisted

---

## 9️⃣ Reporting

### 9.1 Trigger Manual KYC Report (Current Month)

```bash
curl -s -X POST http://localhost:8080/api/kyc/report \
  -H "Authorization: Bearer $DEFAULT_ADMIN_TOKEN"
```

### 9.2 Trigger Report for Specific Month

```bash
curl -s -X POST "http://localhost:8080/api/kyc/report?yearMonth=2026-02" \
  -H "Authorization: Bearer $DEFAULT_ADMIN_TOKEN"
```

---

## 🔟 Error Scenarios & Edge Cases

### 10.1 ❌ Login with Wrong Password

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "rahul.sharma@gmail.com",
    "password": "WrongPassword@123"
  }' | jq .
```

> **Expected:** 401 — Bad credentials

### 10.2 ❌ Login with Non-Existent Email

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "nonexistent@example.com",
    "password": "Password@123"
  }' | jq .
```

> **Expected:** 401 — User not found

### 10.3 ❌ Register with Duplicate Email

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: default" \
  -d '{
    "name": "Ghost User",
    "email": "rahul.sharma@gmail.com",
    "mobileNumber": "1111111111",
    "password": "Ghost@12345",
    "dob": "1990-01-01"
  }' | jq .
```

> **Expected:** Error — email already exists

### 10.4 ❌ Register with Weak Password

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: default" \
  -d '{
    "name": "Weak Pass User",
    "email": "weakpass@example.com",
    "mobileNumber": "2222222222",
    "password": "12345",
    "dob": "1990-01-01"
  }' | jq .
```

> **Expected:** Validation error — password too weak

### 10.5 ❌ Register with Invalid Mobile Number

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: default" \
  -d '{
    "name": "Bad Mobile User",
    "email": "badmobile@example.com",
    "mobileNumber": "123",
    "password": "Valid@12345",
    "dob": "1990-01-01"
  }' | jq .
```

> **Expected:** Validation error — must be 10 digits

### 10.6 ❌ Access Admin Endpoint as Regular User (403 Forbidden)

```bash
curl -s -X GET http://localhost:8080/api/users \
  -H "Authorization: Bearer $USER_TOKEN" | jq .
```

> **Expected:** 403 — Access Denied (only ADMIN can list all users)

### 10.7 ❌ Access Tenant Management as Non-Super-Admin (403 Forbidden)

```bash
curl -s -X GET "http://localhost:8080/api/tenants?page=0&size=10" \
  -H "Authorization: Bearer $DEFAULT_ADMIN_TOKEN" | jq .
```

> **Expected:** 403 — Only SUPER_ADMIN can access tenant management

### 10.8 ❌ Access Without Token (401 Unauthorized)

```bash
curl -s -X GET http://localhost:8080/api/users | jq .
```

> **Expected:** 401 — No token provided

### 10.9 ❌ Cross-Tenant Data Access (Tenant Isolation)

```bash
# Try to view a default-tenant user using Acme Admin token
curl -s -X GET http://localhost:8080/api/users/$USER_ID \
  -H "Authorization: Bearer $ACME_ADMIN_TOKEN" | jq .
```

> **Expected:** Error — user not found in Acme tenant (tenant isolation enforced)

---

## 1️⃣1️⃣ User Deletion (Admin)

### 11.1 Delete User

```bash
# First register a throwaway user
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: default" \
  -d '{
    "name": "Disposable User",
    "email": "disposable@example.com",
    "mobileNumber": "3333333333",
    "password": "Dispose@123",
    "dob": "1985-07-20"
  }' | jq .
```

> 💾 Save `id` as `$DISPOSABLE_USER_ID`

```bash
curl -s -X DELETE http://localhost:8080/api/users/$DISPOSABLE_USER_ID \
  -H "Authorization: Bearer $DEFAULT_ADMIN_TOKEN"
```

> **Expected:** `"User deleted successfully"`

### 11.2 ❌ Delete Non-Existent User

```bash
curl -s -X DELETE http://localhost:8080/api/users/99999 \
  -H "Authorization: Bearer $DEFAULT_ADMIN_TOKEN" | jq .
```

> **Expected:** Error — user not found

---

## 1️⃣2️⃣ Tenant Deactivation — Blocks User Access

### 12.1 Deactivate Nova Finance

```bash
curl -s -X PATCH http://localhost:8080/api/tenants/nova_finance/deactivate \
  -H "Authorization: Bearer $SA_TOKEN"
```

### 12.2 Register User in Nova Finance

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: nova_finance" \
  -d '{
    "name": "Nova User",
    "email": "nova.user@novafinance.in",
    "mobileNumber": "5555555555",
    "password": "NovaUser@123",
    "dob": "1993-04-18"
  }' | jq .
```

> **Expected:** Error — tenant is inactive, registration blocked

### 12.3 Re-Activate & Retry

```bash
curl -s -X PATCH http://localhost:8080/api/tenants/nova_finance/activate \
  -H "Authorization: Bearer $SA_TOKEN"

# Now register succeeds
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: nova_finance" \
  -d '{
    "name": "Nova User",
    "email": "nova.user@novafinance.in",
    "mobileNumber": "5555555555",
    "password": "NovaUser@123",
    "dob": "1993-04-18"
  }' | jq .
```

---

## 📊 Demo Data Summary

### Tenants Created

| Tenant ID | Name | Plan | Status | Admin Email |
|---|---|---|---|---|
| `system` | System (pre-seeded) | BASIC | Active | superadmin@kyc.com |
| `default` | Default Tenant (pre-seeded) | BASIC | Active | admin@kyc.com |
| `acme_bank` | Acme Bank International | BASIC | Active | admin@acmebank.com |
| `nova_finance` | Nova Finance Pvt Ltd | BASIC | Active | — (no admin) |

### Users Created

| Name | Email | Tenant | Role | Password |
|---|---|---|---|---|
| Super Admin | superadmin@kyc.com | system | SUPER_ADMIN | SuperAdmin@123 |
| Default Admin | admin@kyc.com | default | TENANT_ADMIN | Password@123 |
| Acme Admin | admin@acmebank.com | acme_bank | TENANT_ADMIN | AcmeAdmin@123 |
| Rahul K Sharma | rahul.sharma@gmail.com | default | USER | NewRahul@123 |
| Priya Patel | priya.patel@yahoo.com | default | USER | Priya@54321 |
| Amit Kumar | amit.kumar@outlook.com | acme_bank | USER | Amit@67890 |
| Sneha Reddy | sneha.reddy@gmail.com | acme_bank | USER | Sneha@11223 |
| Nova User | nova.user@novafinance.in | nova_finance | USER | NovaUser@123 |

### Scenarios Covered

| # | Scenario | Section |
|---|---|---|
| 1 | Super Admin login | §1 |
| 2 | Tenant CRUD (create, list, get, update) | §2.1–2.5 |
| 3 | API Key rotation | §2.6 |
| 4 | Tenant activate/deactivate (idempotent) | §2.7–2.9 |
| 5 | Tenant statistics | §2.10 |
| 6 | Tenant Admin login (multiple tenants) | §3 |
| 7 | User registration (multiple tenants) | §4.1–4.4 |
| 8 | Regular user login | §4.5–4.6 |
| 9 | Self-service profile view & update | §4.7–4.8 |
| 10 | Admin list/search users | §4.9–4.10 |
| 11 | Admin activate/deactivate user | §4.11–4.12 |
| 12 | KYC PAN upload + async processing | §5.1 |
| 13 | KYC Aadhaar upload (cross-tenant) | §5.2 |
| 14 | KYC status check (latest & history) | §5.3–5.4 |
| 15 | Duplicate document rejection | §5.5 |
| 16 | Multiple doc types per user | §5.6 |
| 17 | File size validation (>10MB) | §5.7 |
| 18 | File type validation | §5.8 |
| 19 | KYC search — all, by status, by doc type | §6.1–6.5 |
| 20 | Tenant data isolation (search) | §6.6 |
| 21 | Password reset (forgot → token → change) | §7 |
| 22 | Token refresh & rotation | §8.1 |
| 23 | Logout + token blacklisting | §8.2–8.3 |
| 24 | Manual KYC report trigger | §9 |
| 25 | Wrong password login | §10.1 |
| 26 | Non-existent user login | §10.2 |
| 27 | Duplicate email registration | §10.3 |
| 28 | Weak password validation | §10.4 |
| 29 | Invalid mobile number | §10.5 |
| 30 | Role-based access (403 Forbidden) | §10.6–10.7 |
| 31 | Missing token (401 Unauthorized) | §10.8 |
| 32 | Cross-tenant data isolation | §10.9 |
| 33 | User deletion | §11 |
| 34 | Inactive tenant blocks operations | §12 |

---

> **Total: 34 scenarios** covering authentication, authorization, multi-tenancy, KYC lifecycle, validation, error handling, and data isolation.
