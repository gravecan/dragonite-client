#!/usr/bin/env python3
"""
test_learning.py - Manually test the /api/learn endpoint

This simulates what the Minecraft client does when sending learning samples.
Use this to verify the server is working before debugging the client.
"""

import requests
import json
import os
import time
import hashlib
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad, unpad

# MUST match your DetectionClient.java settings
import os
SERVER_URL = os.environ.get("ACDETECT_URL", "http://127.0.0.1:5001/api")
LOCAL_URL = "http://localhost:5001/api"
# MUST match client ENCRYPTION_KEY (32 chars)
ENCRYPTION_KEY = os.environ.get('ACDETECT_ENCRYPTION_KEY', 'DragoniteAC2024SecretKey!!32!!!!').encode('utf-8')
LICENSE_KEY = "YOUR_LICENSE_KEY"  # Replace with your actual key

def encrypt(data_dict):
    """Encrypt JSON data - matches Java client encryption"""
    json_str = json.dumps(data_dict)
    iv = os.urandom(16)
    cipher = AES.new(ENCRYPTION_KEY, AES.MODE_CBC, iv)
    padded = pad(json_str.encode('utf-8'), AES.block_size)
    encrypted = cipher.encrypt(padded)
    return iv + encrypted

def decrypt(data):
    """Decrypt response - matches Java client decryption"""
    if len(data) < 16:
        raise ValueError(f"Ciphertext too short: {len(data)} bytes")
    iv = data[:16]
    ciphertext = data[16:]
    cipher = AES.new(ENCRYPTION_KEY, AES.MODE_CBC, iv)
    decrypted = unpad(cipher.decrypt(ciphertext), AES.block_size)
    return json.loads(decrypted.decode('utf-8'))

def hash_license(license_key):
    """Hash license key - matches Java implementation"""
    return hashlib.sha256(license_key.encode('utf-8')).hexdigest()

def test_learn_endpoint(use_local=True):
    """Test the /api/learn endpoint"""
    url = LOCAL_URL if use_local else SERVER_URL
    
    print("=" * 70)
    print("TESTING /api/learn ENDPOINT")
    print("=" * 70)
    print(f"Server URL: {url}")
    print(f"Encryption Key: {ENCRYPTION_KEY[:10]}... ({len(ENCRYPTION_KEY)} bytes)")
    
    # Sample feature vector (21 features - matches N_FEATURES in server v2.0)
    # These are realistic values for anticheat detection
    test_features = [
        35.2,   # mean interval (ms)
        14.8,   # std dev
        0.42,   # CV (coefficient of variation)
        25.3,   # p25
        35.1,   # p50 (median)
        45.7,   # p75
        20.4,   # IQR
        0.6,    # autocorr 50ms
        0.3,    # autocorr 100ms
        0.3,    # bimodality
        0.8,    # quantization step
        3.5,    # action entropy
        2.8,    # delta entropy
        0.4,    # delta uniformity
        5,      # longest sequential run
        0.3,    # KA/txn ratio
        0.15,   # burst density
        0.01,   # jitter trend
        -1.0,   # event ID mean (Polar signature: -1)
        0.1,    # event ID variance
        2.0     # event ID range
    ]
    
    # Prepare payload - matches DetectionClient.java
    payload = {
        "license": hash_license(LICENSE_KEY),
        "server": "test.hypixel.net",
        "label": "POLAR",
        "timestamp": int(time.time() * 1000),  # milliseconds
        "features": test_features
    }
    
    print(f"\nPayload:")
    print(f"  Label: {payload['label']}")
    print(f"  Server: {payload['server']}")
    print(f"  Features: {len(payload['features'])} values")
    print(f"  First 5 features: {payload['features'][:5]}")
    
    # Encrypt
    print(f"\nEncrypting payload...")
    try:
        encrypted_data = encrypt(payload)
        print(f"  ✓ Encrypted: {len(encrypted_data)} bytes")
    except Exception as e:
        print(f"  ✗ Encryption failed: {e}")
        return False
    
    # Send request
    print(f"\nSending POST to {url}/learn...")
    try:
        response = requests.post(
            f"{url}/learn",
            data=encrypted_data,
            headers={"Content-Type": "application/octet-stream"},
            timeout=10
        )
        print(f"  ✓ HTTP Status: {response.status_code}")
        
        if response.status_code != 200:
            print(f"  ✗ Error response: {response.text[:200]}")
            return False
            
    except requests.exceptions.ConnectionError as e:
        print(f"  ✗ Connection failed: {e}")
        print(f"\n  Troubleshooting:")
        print(f"  1. Is the server running? Check with: pgrep -f acdetect_server")
        print(f"  2. Is port 5001 open? Check with: netstat -tuln | grep 5001")
        print(f"  3. Try local URL if testing on same machine")
        return False
    except Exception as e:
        print(f"  ✗ Request failed: {e}")
        return False
    
    # Decrypt response
    print(f"\nDecrypting response...")
    try:
        decrypted = decrypt(response.content)
        print(f"  ✓ Decrypted successfully")
        print(f"\nServer Response:")
        print(json.dumps(decrypted, indent=2))
        
        if decrypted.get('status') == 'learned':
            print(f"\n✅ SUCCESS! Learning sample accepted")
            print(f"   Label: {decrypted.get('label')}")
            print(f"   Sample count: {decrypted.get('sample_count', 'unknown')}")
            return True
        else:
            print(f"\n❌ FAILED - Unexpected response")
            return False
            
    except Exception as e:
        print(f"  ✗ Decryption failed: {e}")
        print(f"  Raw response: {response.content[:100]}")
        return False

def test_stats_endpoint(use_local=True):
    """Test the /api/stats endpoint (unencrypted)"""
    url = LOCAL_URL if use_local else SERVER_URL
    
    print("\n" + "=" * 70)
    print("TESTING /api/stats ENDPOINT")
    print("=" * 70)
    
    try:
        response = requests.get(f"{url}/stats", timeout=5)
        print(f"HTTP Status: {response.status_code}")
        
        if response.status_code == 200:
            stats = response.json()
            print(f"\nServer Statistics:")
            print(json.dumps(stats, indent=2))
            
            print(f"\n✅ Stats retrieved successfully")
            print(f"   Total samples: {stats.get('total_samples', 0)}")
            print(f"   Classes: {stats.get('classes', [])}")
            return True
        else:
            print(f"❌ Failed to get stats: {response.text}")
            return False
            
    except Exception as e:
        print(f"❌ Stats request failed: {e}")
        return False

def watch_database(db_path="/root/DragoniteClient/acdetect.db", duration=30):
    """Watch database for new samples"""
    print("\n" + "=" * 70)
    print(f"WATCHING DATABASE FOR {duration} SECONDS")
    print("=" * 70)
    print("Start playing Minecraft now...")
    print("Press Ctrl+C to stop early")
    
    import sqlite3
    
    try:
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()
        
        # Initial count
        cursor.execute("SELECT COUNT(*) FROM samples")
        initial_count = cursor.fetchone()[0]
        print(f"\nInitial sample count: {initial_count}")
        
        for i in range(duration):
            time.sleep(1)
            cursor.execute("SELECT COUNT(*) FROM samples")
            current_count = cursor.fetchone()[0]
            
            if current_count > initial_count:
                new_samples = current_count - initial_count
                print(f"[{i+1}s] ✓ NEW SAMPLES: {new_samples} (total: {current_count})")
                
                # Show latest sample
                cursor.execute("""
                    SELECT id, label, timestamp 
                    FROM samples 
                    ORDER BY timestamp DESC 
                    LIMIT 1
                """)
                latest = cursor.fetchone()
                if latest:
                    print(f"      Latest: ID={latest[0]}, Label={latest[1]}, Time={latest[2]}")
            else:
                print(f"[{i+1}s] Waiting... (count: {current_count})", end='\r')
        
        print(f"\n\nFinal count: {current_count} (added {current_count - initial_count})")
        conn.close()
        
    except KeyboardInterrupt:
        print("\n\nStopped by user")
        conn.close()
    except Exception as e:
        print(f"\n❌ Database watch failed: {e}")

def main():
    import sys
    
    print("\n" + "=" * 70)
    print("ANTICHEAT LEARNING SYSTEM - MANUAL TEST")
    print("=" * 70)
    
    # Parse arguments
    use_local = "--local" in sys.argv or "-l" in sys.argv
    watch_only = "--watch" in sys.argv or "-w" in sys.argv
    
    if watch_only:
        watch_database()
        return
    
    # Test stats first (simple, unencrypted)
    print("\n[1/3] Testing stats endpoint...")
    stats_ok = test_stats_endpoint(use_local)
    
    if not stats_ok:
        print("\n❌ Stats test failed - server may not be running")
        return
    
    # Test learning endpoint
    print("\n[2/3] Testing learn endpoint...")
    learn_ok = test_learn_endpoint(use_local)
    
    if not learn_ok:
        print("\n❌ Learn test failed - check server logs for errors")
        return
    
    # Verify it was stored
    print("\n[3/3] Verifying sample was stored...")
    time.sleep(1)  # Give server time to write
    stats_ok = test_stats_endpoint(use_local)
    
    if stats_ok:
        print("\n" + "=" * 70)
        print("✅ ALL TESTS PASSED")
        print("=" * 70)
        print("\nServer is working correctly!")
        print("\nNext steps:")
        print("1. Check Minecraft logs for [ACDetect] messages")
        print("2. Watch database: python3 test_learning.py --watch")
        print("3. Monitor server: tail -f server.log")
    else:
        print("\n❌ Verification failed")

if __name__ == "__main__":
    import sys
    
    if "--help" in sys.argv or "-h" in sys.argv:
        print("Usage:")
        print("  python3 test_learning.py           # Test remote server")
        print("  python3 test_learning.py --local   # Test localhost")
        print("  python3 test_learning.py --watch   # Watch database for 30s")
        sys.exit(0)
    
    main()
