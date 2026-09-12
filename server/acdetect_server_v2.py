"""
Anticheat Detection Server v2.0 - IMPROVED
Adds event ID monitoring for better classification

New Features:
- 21 features (was 18) - added event ID mean/variance/range
- Fixed encryption key (exactly 32 bytes)
- Better error handling and logging
- Crash recovery
"""

from flask import Flask, request, jsonify
import sqlite3
import numpy as np
import json
import hashlib
import os
import traceback
from datetime import datetime
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad, unpad

app = Flask(__name__)

DATABASE = 'acdetect.db'
N_FEATURES = 21  # ✅ CHANGED: Was 18, now 21

# ✅ FIXED: Exactly 32 characters (was 31 - missing one '!')
_key = os.environ.get('ACDETECT_ENCRYPTION_KEY', '')
if len(_key) != 32:
    raise RuntimeError('Set ACDETECT_ENCRYPTION_KEY to exactly 32 characters on the VPS')
ENCRYPTION_KEY = _key.encode('utf-8')

# Verify key length on startup
assert len(ENCRYPTION_KEY) == 32, f"Key must be 32 bytes, got {len(ENCRYPTION_KEY)}"

print(f"[ACDetect] ✓ Encryption key: {len(ENCRYPTION_KEY)} bytes")

# === DATABASE ===

def init_db():
    conn = sqlite3.connect(DATABASE)
    c = conn.cursor()
    
    c.execute('''
        CREATE TABLE IF NOT EXISTS samples (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            license_hash TEXT NOT NULL,
            server TEXT,
            label TEXT,
            timestamp INTEGER,
            features TEXT
        )
    ''')
    
    c.execute('''
        CREATE TABLE IF NOT EXISTS model_params (
            label TEXT PRIMARY KEY,
            count INTEGER,
            means TEXT,
            variances TEXT
        )
    ''')
    
    c.execute('''
        CREATE TABLE IF NOT EXISTS licenses (
            license_hash TEXT PRIMARY KEY,
            active INTEGER DEFAULT 1,
            created INTEGER
        )
    ''')
    
    c.execute('CREATE INDEX IF NOT EXISTS idx_samples_label ON samples(label)')
    c.execute('CREATE INDEX IF NOT EXISTS idx_samples_license ON samples(license_hash)')
    
    conn.commit()
    conn.close()
    print("[ACDetect] ✓ Database initialized")

def get_db():
    return sqlite3.connect(DATABASE)

# === ENCRYPTION ===

def decrypt_request(data):
    try:
        if len(data) < 16:
            raise ValueError(f"Ciphertext too short: {len(data)} bytes")
        
        iv = data[:16]
        ciphertext = data[16:]
        cipher = AES.new(ENCRYPTION_KEY, AES.MODE_CBC, iv)
        decrypted = unpad(cipher.decrypt(ciphertext), AES.block_size)
        return json.loads(decrypted.decode('utf-8'))
    except Exception as e:
        print(f"[ACDetect] ✗ Decryption error: {e}")
        raise ValueError(f"Decryption failed: {e}")

def encrypt_response(data):
    try:
        json_str = json.dumps(data)
        iv = os.urandom(16)
        cipher = AES.new(ENCRYPTION_KEY, AES.MODE_CBC, iv)
        padded = pad(json_str.encode('utf-8'), AES.block_size)
        encrypted = cipher.encrypt(padded)
        return iv + encrypted
    except Exception as e:
        print(f"[ACDetect] ✗ Encryption error: {e}")
        raise ValueError(f"Encryption failed: {e}")

# === GAUSSIAN NAIVE BAYES ===

class GaussianNB:
    def __init__(self):
        self.classes = {}
        self.class_counts = {}
        self.class_means = {}
        self.class_vars = {}
    
    def load_from_db(self):
        try:
            conn = get_db()
            c = conn.cursor()
            c.execute('SELECT label, count, means, variances FROM model_params')
            
            for row in c.fetchall():
                label, count, means, variances = row
                self.classes[label] = True
                self.class_counts[label] = count
                self.class_means[label] = np.array(json.loads(means))
                self.class_vars[label] = np.array(json.loads(variances))
            
            conn.close()
            print(f"[ACDetect] ✓ Loaded {len(self.classes)} classes")
        except Exception as e:
            print(f"[ACDetect] ⚠ Load error: {e}")
    
    def save_to_db(self):
        try:
            conn = get_db()
            c = conn.cursor()
            
            for label in self.classes:
                c.execute('''
                    INSERT OR REPLACE INTO model_params (label, count, means, variances)
                    VALUES (?, ?, ?, ?)
                ''', (
                    label,
                    self.class_counts[label],
                    json.dumps(self.class_means[label].tolist()),
                    json.dumps(self.class_vars[label].tolist())
                ))
            
            conn.commit()
            conn.close()
        except Exception as e:
            print(f"[ACDetect] ✗ Save error: {e}")
    
    def partial_fit(self, X, y):
        try:
            X = np.array(X)
            
            if len(X) != N_FEATURES:
                raise ValueError(f"Expected {N_FEATURES} features, got {len(X)}")
            
            if y not in self.classes:
                self.classes[y] = True
                self.class_counts[y] = 0
                self.class_means[y] = np.zeros(N_FEATURES)
                self.class_vars[y] = np.ones(N_FEATURES)
            
            n = self.class_counts[y] + 1
            
            delta = X - self.class_means[y]
            self.class_means[y] += delta / n
            delta2 = X - self.class_means[y]
            self.class_vars[y] += (delta * delta2 - self.class_vars[y]) / n
            
            self.class_counts[y] = n
        except Exception as e:
            print(f"[ACDetect] ✗ Fit error: {e}")
            raise
    
    def predict(self, X):
        try:
            X = np.array(X)
            
            if len(X) != N_FEATURES:
                raise ValueError(f"Expected {N_FEATURES} features, got {len(X)}")
            
            if not self.classes:
                raise ValueError("Model not trained")
            
            total = sum(self.class_counts.values())
            
            log_probs = {}
            for label in self.classes:
                log_p = np.log(self.class_counts[label] / total)
                
                means = self.class_means[label]
                vars = np.maximum(self.class_vars[label], 1e-6)
                
                diff = X - means
                log_p -= 0.5 * np.sum(np.log(2 * np.pi * vars))
                log_p -= 0.5 * np.sum(diff ** 2 / vars)
                
                log_probs[label] = log_p
            
            max_log = max(log_probs.values())
            exp_sum = sum(np.exp(lp - max_log) for lp in log_probs.values())
            
            probs = {label: np.exp(lp - max_log) / exp_sum 
                     for label, lp in log_probs.items()}
            
            best = max(probs, key=probs.get)
            
            return best, probs[best], probs
        except Exception as e:
            print(f"[ACDetect] ✗ Predict error: {e}")
            raise

model = GaussianNB()

def load_model():
    model.load_from_db()
    if not model.classes:
        print("[ACDetect] ℹ Seeding model...")
        seed_synthetic()
        model.load_from_db()

def seed_synthetic():
    """Seed with 21-feature synthetic data (added event ID features)"""
    synthetic = {
        # [0-17]: Original features
        # [18]: Event ID mean
        # [19]: Event ID variance  
        # [20]: Event ID range (max-min)
        'POLAR': [35, 15, 0.43, 25, 35, 45, 20, 0.6, 0.3, 0.3, 0.8, 3.5, 2.8, 0.4, 5, 0.3, 0.15, 0.01, -1.0, 0.1, 2.0],
        'VULCAN': [20, 5, 0.25, 15, 20, 25, 10, 0.9, 0.7, 0.1, 0.5, 2.0, 1.5, 0.2, 20, 0.1, 0.05, 0.005, 100.0, 50.0, 200.0],
        'GRIM': [50, 25, 0.5, 30, 50, 75, 45, 0.85, 0.75, 0.15, 0.6, 1.8, 0.5, 0.1, 30, 0.05, 0.02, 0.002, 0.0, 1.0, 10.0],
        'MATRIX': [40, 20, 0.5, 25, 40, 55, 30, 0.5, 0.3, 0.2, 0.7, 2.5, 2.0, 0.3, 10, 0.2, 0.1, 0.01, 50.0, 25.0, 100.0],
    }
    
    for label, features in synthetic.items():
        for _ in range(10):
            model.partial_fit(features, label)
    
    model.save_to_db()
    print(f"[ACDetect] ✓ Seeded {len(synthetic)} classes")

# === API ENDPOINTS ===

@app.route('/api/detect', methods=['POST'])
def detect():
    try:
        data = decrypt_request(request.get_data())
        
        features = data.get('features', [])
        
        if len(features) != N_FEATURES:
            return encrypt_response({'error': f'Expected {N_FEATURES} features'}), 400
        
        anticheat, confidence, probabilities = model.predict(features)
        
        print(f"[ACDetect] ✓ Detect: {anticheat} ({confidence:.2%})")
        
        return encrypt_response({
            'anticheat': anticheat,
            'confidence': float(confidence),
            'confirmed': confidence >= 0.6,
            'mode': 'DETECTION',
            'probabilities': {k: float(v) for k, v in probabilities.items()}
        })
    
    except Exception as e:
        print(f"[ACDetect] ✗ Detect error: {e}")
        return encrypt_response({'error': str(e)}), 400

@app.route('/api/learn', methods=['POST'])
def learn():
    try:
        data = decrypt_request(request.get_data())
        
        license_hash = data.get('license', '')
        server = data.get('server', 'unknown')
        label = data.get('label', 'UNKNOWN')
        features = data.get('features', [])
        
        if len(features) != N_FEATURES:
            return encrypt_response({'error': f'Expected {N_FEATURES} features'}), 400
        
        # Store sample
        conn = get_db()
        c = conn.cursor()
        c.execute('''
            INSERT INTO samples (license_hash, server, label, timestamp, features)
            VALUES (?, ?, ?, ?, ?)
        ''', (license_hash, server, label, int(datetime.now().timestamp()), json.dumps(features)))
        conn.commit()
        conn.close()
        
        # Update model
        model.partial_fit(features, label)
        model.save_to_db()
        
        count = model.class_counts.get(label, 0)
        
        print(f"[ACDetect] ✓ Learned: {label} (samples: {count})")
        
        return encrypt_response({
            'status': 'learned',
            'label': label,
            'sample_count': count
        })
    
    except Exception as e:
        print(f"[ACDetect] ✗ Learn error: {e}")
        return encrypt_response({'error': str(e)}), 400

@app.route('/api/stats', methods=['GET'])
def stats():
    try:
        conn = get_db()
        c = conn.cursor()
        
        c.execute('SELECT COUNT(*) FROM samples')
        total = c.fetchone()[0]
        
        c.execute('SELECT label, COUNT(*) FROM samples GROUP BY label')
        by_label = dict(c.fetchall())
        
        c.execute('SELECT COUNT(DISTINCT server) FROM samples')
        servers = c.fetchone()[0]
        
        conn.close()
        
        return jsonify({
            'total_samples': total,
            'by_label': by_label,
            'unique_servers': servers,
            'classes': list(model.classes.keys()),
            'n_features': N_FEATURES,
            'version': '2.0'
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/health', methods=['GET'])
def health():
    return jsonify({
        'status': 'healthy',
        'version': '2.0',
        'n_features': N_FEATURES,
        'classes': list(model.classes.keys())
    })

# === MAIN ===

if __name__ == '__main__':
    try:
        print("=" * 60)
        print("ANTICHEAT DETECTION SERVER v2.0")
        print("=" * 60)
        
        print(f"[ACDetect] Key: {len(ENCRYPTION_KEY)} bytes")
        
        init_db()
        load_model()
        
        print(f"[ACDetect] ✓ Features: {N_FEATURES}")
        print(f"[ACDetect] ✓ Classes: {list(model.classes.keys())}")
        print(f"[ACDetect] ✓ Samples: {sum(model.class_counts.values())}")
        print("=" * 60)
        
        app.run(host='0.0.0.0', port=5001, debug=False, threaded=True)
        
    except Exception as e:
        print(f"[ACDetect] ✗ FATAL: {e}")
        print(traceback.format_exc())
        exit(1)
