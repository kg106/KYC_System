import requests
import concurrent.futures
import time
import uuid
import os

BASE_URL = "http://localhost:8080/api"

# Helper to create a unique user
def register_and_login(prefix):
    username = "Gondaliya Karan"
    email = "abcxyz6510@gmail.com"
    password = "Password@123"
    dob = "2005-06-10"
    mobileNumber = "9876543210"
    isActive = True
    
    # Register
    reg_payload = {
        "username": username,
        "email": email,
        "password": password,
        "dob": dob,
        "mobileNumber": mobileNumber,
        "isActive": isActive
    }
    # Assuming there's a register endpoint, adapting based on typical flow
    # If explicit register isn't available, we might need to rely on pre-existing or admin creation
    # Let's try standard registration path if it exists, or check User controller.
    # checking previous context, User CRUD exists. 
    # Let's assume /auth/register or /users/register. 
    # I will assume /api/users based on CRUD description, but usually auth is separate.
    # Let's try /api/auth/register based on common patterns, if fails I'll check controllers.
    
    # Actually, let's just use the known endpoint from context if possible or best guess.
    # I'll try to create via the open endpoint if I can find it.
    # For now, I'll assume I can create users.
    
    reg_resp = requests.post(f"{BASE_URL}/auth/register", json=reg_payload)
    if reg_resp.status_code not in [200, 201]:
        print(f"Registration failed for {username}: {reg_resp.text}")
        return None, None

    # Login
    login_payload = {
        "email": email,
        "password": password
    }
    login_resp = requests.post(f"{BASE_URL}/auth/login", json=login_payload)
    
    if login_resp.status_code != 200:
        print(f"Login failed for {username}: {login_resp.text}")
        return None, None
        
    token = login_resp.json().get("token")
    #user_id = login_resp.json().get("id") # Assuming login returns ID, if not we search
    return token, username

# If registration/login fails due to my endpoint assumptions, I might need to adjust.
# I will use a dummy file for upload.
def create_dummy_file():
    filename = "test_doc.txt"
    with open(filename, "w") as f:
        f.write("This is a test document for KYC verification.")
    return filename

user_id = 218

def submit_kyc(token, user_id, doc_type, doc_number):
    url = f"{BASE_URL}/kyc/upload"
    headers = {"Authorization": f"Bearer {token}"}
    
    try:
        # Re-open file for each request strictly to avoid closed file errors in threads
        with open("test_doc.txt", "rb") as f:
            files = {"file": ("test_doc.txt", f, "text/plain")}
            data = {
                "userId": user_id,
                "documentType": doc_type,
                "documentNumber": doc_number
            }
            start = time.time()
            response = requests.post(url, headers=headers, files=files, data=data)
            duration = time.time() - start
            return response.status_code, response.text, duration
    except Exception as e:
        return 0, str(e), 0

def run_test():
    print("--- Starting Concurrent KYC Submission Test ---")
    create_dummy_file()
    
    # 1. Setup Users
    print("\n[Setup] creating users...")
    # NOTE: I need to verify endpoints. 
    # Based on file list `AuthController` exists. `UserController` exists.
    # I'll check `AuthController` first to be sure about login/register.
    # BUT for now I will write the script assuming standard /auth/register or similar. 
    # If 404, I will debug.
    
    # Placeholder for user creation - will verify in next step if this script fails.
    # For now, I'll rely on the user to have some way to get a token, 
    # OR I'll assume the script is run in an env where I can check the endpoints.
    # Actually, I am an agent, I should check the endpoints FIRST.
    # But I am writing the file now. I'll add a check in the script?
    # No, I will just view the AuthController after this write.
    pass

if __name__ == "__main__":
    # This is a template. I will fill the logic after verifying Auth endpoints.
    print("Please run the refined script after I verify Auth endpoints.")
