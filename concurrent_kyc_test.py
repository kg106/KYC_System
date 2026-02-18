import requests
import concurrent.futures
import time
import uuid
import os
import random
import string

BASE_URL = "http://localhost:8080/api"

def generate_random_string(length=10):
    return ''.join(random.choices(string.ascii_letters, k=length))

def generate_mobile():
    return f"9{random.randint(100000000, 999999999)}"

# Helper to create a unique user
def register_and_login(prefix):
    unique_string = generate_random_string(6)
    username = f"{prefix} {unique_string}"
    email = f"{username.lower().replace(' ', '')}@example.com"
    password = "Password@123"
    mobile = generate_mobile()
    isActive = True
    dob = "1990-01-01"
    
    # Register
    reg_payload = {
        "name": username,
        "email": email,
        "mobileNumber": mobile,
        "password": password,
        "isActive": isActive,
        "dob": dob
    }
    
    try:
        reg_resp = requests.post(f"{BASE_URL}/auth/register", json=reg_payload)
        if reg_resp.status_code not in [200, 201]:
            print(f"Registration failed for {username}: {reg_resp.text}")
            return None, None, None
        
        user_data = reg_resp.json()
        user_id = user_data.get("id")

        # Login
        login_payload = {
            "email": email,
            "password": password
        }
        login_resp = requests.post(f"{BASE_URL}/auth/login", json=login_payload)
        
        if login_resp.status_code != 200:
            print(f"Login failed for {username}: {login_resp.text}")
            return None, None, None
            
        token = login_resp.json().get("accessToken")
        return token, user_id, email
    except Exception as e:
        print(f"Auth error: {e}")
        return None, None, None

def create_dummy_file():
    filename = "test_doc.png"
    # Minimal valid 1x1 pixel PNG
    # Signature: 89 50 4E 47 0D 0A 1A 0A
    # IHDR: ...
    # IDAT: ...
    # IEND: ...
    content = b'\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x02\x00\x00\x00\x90wS\xde\x00\x00\x00\x0cIDAT\x08\xd7c\xf8\xff\xff?\x00\x05\xfe\x02\xfe\xdc\xcc\x59\xe7\x00\x00\x00\x00IEND\xaeB`\x82'
    
    with open(filename, "wb") as f:
        f.write(content)
    return filename

def submit_kyc(token, user_id, doc_type, doc_num):
    url = f"{BASE_URL}/kyc/upload"
    headers = {'Authorization': f'Bearer {token}'}
    
    # Use real file if exists to simulate processing time, otherwise robust dummy
    if os.path.exists("test_doc.png"):
        with open("test_doc.png", "rb") as f:
            file_content = f.read()
    else:
        # Minimal valid PNG header to trick file type checks
        file_content = b'\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15\xc4\x89\x00\x00\x00\nIDATx\x9cc\x00\x01\x00\x00\x05\x00\x01\r\n-\xb4\x00\x00\x00\x00IEND\xaeB`\x82'

    files = {'file': ('test_doc.png', file_content, 'image/png')}
    data = {
        'userId': user_id,
        'documentType': doc_type,
        'documentNumber': doc_num
    }
    
    try:
        start = time.time() # Added back for duration calculation
        response = requests.post(url, headers=headers, files=files, data=data)
        duration = time.time() - start # Added back for duration calculation
        return response.status_code, response.text, duration # Return duration
    except Exception as e:
        return 500, str(e), 0

def run_test():
    print("--- Starting Concurrent KYC Submission Test ---")
    create_dummy_file()
    
    # 1. Setup Users
    print("\n[Setup] Creating users...")
    token_a, id_a, email_a = register_and_login("UserA")
    token_b, id_b, email_b = register_and_login("UserB")
    
    if not token_a or not token_b:
        print("Failed to create users. Exiting.")
        return

    print(f"User A: ID={id_a}, Email={email_a}")
    print(f"User B: ID={id_b}, Email={email_b}")

    # Test 1: Race Condition (Same User, Same Doc Type)
    print("\n[Test 1] Race Condition: 5 concurrent requests (User A, PAN)")
    doc_number_1 = f"PAN{uuid.uuid4().hex[:5].upper()}"
    
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as executor:
        futures = [
            executor.submit(submit_kyc, token_a, id_a, "PAN", doc_number_1)
            for _ in range(5)
        ]
        
        results = []
        for f in concurrent.futures.as_completed(futures):
            results.append(f.result())
            
    success_count = sum(1 for r in results if r[0] == 200 or r[0] == 202)
    fail_count = sum(1 for r in results if r[0] != 200 and r[0] != 202)
    print(f"Results: {success_count} Success, {fail_count} Failed")
    for code, text, dur in results:
        print(f"  -> {code} ({dur:.2f}s): {text[:100]}...")

    # Test 2: Sequential Retry (Same User, Same Doc Type, DIFFERENT Doc Number)
    # Note: If Test 1 succeeded once, there is a PENDING/PROCESSING request.
    # The system should block a NEW request for same doc type.
    print("\n[Test 2] Sequential Retry: New request (User A, PAN, New Doc Number)")
    doc_number_2 = f"PAN{uuid.uuid4().hex[:5].upper()}"
    code, text, dur = submit_kyc(token_a, id_a, "PAN", doc_number_2)
    print(f"Result: {code} ({dur:.2f}s): {text}")
    
    # Test 3: Parallel Different Docs (Same User)
    print("\n[Test 3] Parallel Different Docs: User A submitting PASSPORT and AADHAAR simultaneously")
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        f1 = executor.submit(submit_kyc, token_a, id_a, "PASSPORT", f"PASS{uuid.uuid4().hex[:5]}")
        f2 = executor.submit(submit_kyc, token_a, id_a, "AADHAAR", f"AAD{uuid.uuid4().hex[:5]}")
        
        r1 = f1.result()
        r2 = f2.result()
        
    print(f"Passport Result: {r1[0]} - {r1[1][:100]}")
    print(f"Aadhaar Result: {r2[0]} - {r2[1][:100]}")

    # Test 4: Different Users (Same Doc Type)
    print("\n[Test 4] Different Users: User A (LICENSE) vs User B (LICENSE)")
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        f1 = executor.submit(submit_kyc, token_a, id_a, "LICENSE", f"LIC{uuid.uuid4().hex[:5]}")
        f2 = executor.submit(submit_kyc, token_b, id_b, "LICENSE", f"LIC{uuid.uuid4().hex[:5]}")
        
        r1 = f1.result()
        r2 = f2.result()
        
    print(f"User A Result: {r1[0]} - {r1[1][:100]}")
    print(f"User B Result: {r2[0]} - {r2[1][:100]}")

    # Test 5: High Concurrency (100 Different Users)
    print("\n[Test 5] High Concurrency: 100 different users submitting simultaneously")
    users = []
    print("  Creating 100 users (this may take a moment)...")
    for i in range(100):
        # Use a short delay to avoid overwhelming auth registration if it's slow, 
        # though we want to test that too. Let's just do it sequentially for setup.
        token, uid, email = register_and_login("UserD")
        if token:
            users.append((token, uid))
        else:
            print(f"  Failed to create UserD_{i}")

    if not users:
        print("No users created for Test 5. Skipping.")
        return

    print(f"  Submitting requests for {len(users)} users concurrently...")
    with concurrent.futures.ThreadPoolExecutor(max_workers=100) as executor:
        futures = []
        for i, (token, uid) in enumerate(users):
            doc_num = f"VOTE{uuid.uuid4().hex[:5].upper()}"
            futures.append(executor.submit(submit_kyc, token, uid, "VOTER_ID", doc_num))
            
        results = [f.result() for f in concurrent.futures.as_completed(futures)]
        
    success_count = sum(1 for r in results if r[0] == 200 or r[0] == 202)
    fail_count = sum(1 for r in results if r[0] != 200 and r[0] != 202)
    
    print(f"Results: {success_count}/{len(users)} Success")
    if fail_count > 0:
        print(f"Failures: {fail_count}")
        for code, text, dur in results:
            if code != 200 and code != 202:
                print(f"  -> {code} ({dur:.2f}s): {text[:100]}...")

    # Cleanup
    if os.path.exists("test_doc.png"):
        os.remove("test_doc.png")

if __name__ == "__main__":
    run_test()
