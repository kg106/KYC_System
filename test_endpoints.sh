#!/bin/bash

BASE_URL="http://localhost:8080/api"

echo "--------------------------------------------------"
echo "1. Testing Admin Login..."
ADMIN_LOGIN_RES=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email": "admin@kyc.com", "password": "admin123"}')

ADMIN_TOKEN=$(echo $ADMIN_LOGIN_RES | grep -oP '(?<="accessToken":")[^"]*')

if [ -z "$ADMIN_TOKEN" ]; then
  echo "Admin Login Failed!"
  echo "Response: $ADMIN_LOGIN_RES"
  exit 1
fi
echo "Admin Login Successful. Token obtained."

echo "--------------------------------------------------"
echo "2. Listing all users as Admin (Should be Allowed)..."
curl -s -o /dev/null -w "Status: %{http_code}\n" -X GET "$BASE_URL/users" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

echo "--------------------------------------------------"
echo "3. Registering a new test user..."
REG_RES=$(curl -s -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test User",
    "email": "testuser@email.com",
    "mobileNumber": "1234567890",
    "password": "password123",
    "dob": "1990-01-01"
  }')
echo "Registration Response: $REG_RES"
USER_ID=$(echo $REG_RES | grep -oP '(?<="id":)[^,]*')

echo "--------------------------------------------------"
echo "4. Logging in as new test user..."
USER_LOGIN_RES=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email": "testuser@email.com", "password": "password123"}')

USER_TOKEN=$(echo $USER_LOGIN_RES | grep -oP '(?<="accessToken":")[^"]*')
echo "User Login Successful. Token obtained."

echo "--------------------------------------------------"
echo "5. Listing all users as Regular User (Should be Forbidden)..."
curl -s -o /dev/null -w "Status: %{http_code}\n" -X GET "$BASE_URL/users" \
  -H "Authorization: Bearer $USER_TOKEN"

echo "--------------------------------------------------"
echo "6. Getting self profile as Regular User (Should be Allowed)..."
curl -s -o /dev/null -w "Status: %{http_code}\n" -X GET "$BASE_URL/users/$USER_ID" \
  -H "Authorization: Bearer $USER_TOKEN"

echo "--------------------------------------------------"
echo "7. Getting another user's profile as Regular User (Should be Forbidden)..."
# Admin ID is likely 1
curl -s -o /dev/null -w "Status: %{http_code}\n" -X GET "$BASE_URL/users/1" \
  -H "Authorization: Bearer $USER_TOKEN"

echo "--------------------------------------------------"
echo "8. Testing Forgot Password (Should be Public)..."
curl -s -X POST "$BASE_URL/users/$USER_ID/forgot-password"
echo ""

echo "--------------------------------------------------"
echo "Tests Completed."
