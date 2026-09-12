"""
Anticheat Detection Server
Run on VPS with: python3 server.py

Requirements:
  pip install flask sqlite3 numpy pycryptodome

Endpoints:
  POST /api/detect  - Detect anticheat from features
  POST /api/learn   - Submit learning sample
  GET  /api/stats   - Get statistics
  GET  /api/model   - Get model parameters
"""

from flask import Flask, request, jsonify
import sqlite3
import numpy as np
import json
import hashlib
import os
from datetime import datetime
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad, unpad

app = Flask(__name__)

DATABASE = 'acdetect.db'
N_FEATURES = 18

_key = os.environ.get('ACDETECT_ENCRYPTION_KEY', '')
if len(_key) != 32:
    raise RuntimeError('Set ACDETECT_ENCRYPTION_KEY to exactly 32 characters on the VPS')
ENCRYPTION_KEY = _key.encode('utf-8')

# === DATABASE ===

def init_db():
    conn = sqlite3.connect(DATABASE)
    c = conn.cursor()
    
    # Samples table
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
    
    # Model parameters table
    c.execute('''
        CREATE TABLE IF NOT EXISTS model_params (
            label TEXT PRIMARY KEY,
            count INTEGER,
            means TEXT,
            variances TEXT
        )
    ''')
    
    # Licenses table
    c.execute('''
        CREATE TABLE IF NOT EXISTS licenses (
            license_hash TEXT PRIMARY KEY,
            active INTEGER DEFAULT 1,
            created INTEGER
        )
    ''')
    
    # Indexes
    c.execute('CREATE INDEX IF NOT EXISTS idx_samples_label ON samples(label)')
    c.execute('CREATE INDEX IF NOT EXISTS idx_samples_license ON samples(license_hash)')
    
    conn.commit()
    conn.close()

def get_db():
    return sqlite3.connect(DATABASE)

# === ENCRYPTION ===

def decrypt_request(data):
    """Decrypt incoming request data"""
    try:
        iv = data[:16]
        ciphertext = data[16:]
        cipher = AES.new(ENCRYPTION_KEY, AES.MODE_CBC, iv)
        decrypted = unpad(cipher.decrypt(ciphertext), AES.block_size)
        return json.loads(decrypted.decode('utf-8'))
    except Exception as e:
        raise ValueError(f"Decryption failed: {e}")

def encrypt_response(data):
    """Encrypt outgoing response data"""
    try:
        json_str = json.dumps(data)
        iv = os.urandom(16)
        cipher = AES.new(ENCRYPTION_KEY, AES.MODE_CBC, iv)
        padded = pad(json_str.encode('utf-8'), AES.block_size)
        encrypted = cipher.encrypt(padded)
        return iv + encrypted
    except Exception as e:
        raise ValueError(f"Encryption failed: {e}")

# === GAUSSIAN NAIVE BAYES ===

class GaussianNB:
    def __init__(self):
        self.classes = {}
        self.class_counts = {}
        self.class_means = {}
        self.class_vars = {}
    
    def load_from_db(self):
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
    
    def save_to_db(self):
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
    
    def partial_fit(self, X, y):
        """Online learning - update model with new sample"""
        X = np.array(X)
        
        if y not in self.classes:
            self.classes[y] = True
            self.class_counts[y] = 0
            self.class_means[y] = np.zeros(N_FEATURES)
            self.class_vars[y] = np.ones(N_FEATURES)
        
        n = self.class_counts[y] + 1
        
        # Welford's online algorithm
        delta = X - self.class_means[y]
        self.class_means[y] += delta / n
        delta2 = X - self.class_means[y]
        self.class_vars[y] += (delta * delta2 - self.class_vars[y]) / n
        
        self.class_counts[y] = n
    
    def predict(self, X):
        """Predict class probabilities"""
        X = np.array(X)
        total = sum(self.class_counts.values())
        
        log_probs = {}
        for label in self.classes:
            # Prior
            log_p = np.log(self.class_counts[label] / total)
            
            # Likelihood (Gaussian)
            means = self.class_means[label]
            vars = self.class_vars[label]
            
            # Avoid division by zero
            vars = np.maximum(vars, 1e-6)
            
            # Log probability
            diff = X - means
            log_p -= 0.5 * np.sum(np.log(2 * np.pi * vars))
            log_p -= 0.5 * np.sum(diff ** 2 / vars)
            
            log_probs[label] = log_p
        
        # Softmax
        max_log = max(log_probs.values())
        exp_sum = sum(np.exp(lp - max_log) for lp in log_probs.values())
        
        probs = {label: np.exp(lp - max_log) / exp_sum 
                 for label, lp in log_probs.items()}
        
        # Best prediction
        best = max(probs, key=probs.get)
        
        return best, probs[best], probs

# Initialize model
model = GaussianNB()

def load_model():
    model.load_from_db()
    if not model.classes:
        # Seed with synthetic data
        seed_synthetic()
        model.load_from_db()

def seed_synthetic():
    """Seed model with initial synthetic data"""
    synthetic = {
        'POLAR': [35, 15, 0.43, 25, 35, 45, 20, 0.6, 0.3, 0.3, 0.8, 3.5, 2.8, 0.4, 5, 0.3, 0.15, 0.01],
        'VULCAN': [20, 5, 0.25, 15, 20, 25, 10, 0.9, 0.7, 0.1, 0.5, 2.0, 1.5, 0.2, 20, 0.1, 0.05, 0.005],
        'GRIM': [50, 25, 0.5, 30, 50, 75, 45, 0.85, 0.75, 0.15, 0.6, 1.8, 0.5, 0.1, 30, 0.05, 0.02, 0.002],
        'MATRIX': [40, 20, 0.5, 25, 40, 55, 30, 0.5, 0.3, 0.2, 0.7, 2.5, 2.0, 0.3, 10, 0.2, 0.1, 0.01],
    }
    
    for label, features in synthetic.items():
        for _ in range(10):
            model.partial_fit(features, label)
    
    model.save_to_db()

# === API ENDPOINTS ===

@app.route('/api/detect', methods=['POST'])
def detect():
    """Detect anticheat from feature vector"""
    try:
        data = decrypt_request(request.get_data())
    except ValueError as e:
        return encrypt_response({'error': str(e)}), 400
    
    license_hash = data.get('license', '')
    server = data.get('server', 'unknown')
    features = data.get('features', [])
    
    # Validate
    if len(features) != N_FEATURES:
        return encrypt_response({'error': f'Expected {N_FEATURES} features, got {len(features)}'}), 400
    
    # Predict
    anticheat, confidence, probabilities = model.predict(features)
    
    confirmed = confidence >= 0.6
    
    return encrypt_response({
        'anticheat': anticheat,
        'confidence': float(confidence),
        'confirmed': confirmed,
        'mode': 'DETECTION',
        'probabilities': {k: float(v) for k, v in probabilities.items()}
    })

@app.route('/api/learn', methods=['POST'])
def learn():
    """Submit learning sample"""
    try:
        data = decrypt_request(request.get_data())
    except ValueError as e:
        return encrypt_response({'error': str(e)}), 400
    
    license_hash = data.get('license', '')
    server = data.get('server', 'unknown')
    label = data.get('label', 'UNKNOWN')
    features = data.get('features', [])
    
    # Validate
    if len(features) != N_FEATURES:
        return encrypt_response({'error': f'Expected {N_FEATURES} features, got {len(features)}'}), 400
    
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
    
    return encrypt_response({
        'status': 'learned',
        'label': label,
        'sample_count': count
    })

@app.route('/api/stats', methods=['GET'])
def stats():
    """Get statistics"""
    conn = get_db()
    c = conn.cursor()
    
    # Total samples
    c.execute('SELECT COUNT(*) FROM samples')
    total = c.fetchone()[0]
    
    # Samples per label
    c.execute('SELECT label, COUNT(*) FROM samples GROUP BY label')
    by_label = dict(c.fetchall())
    
    # Unique servers
    c.execute('SELECT COUNT(DISTINCT server) FROM samples')
    servers = c.fetchone()[0]
    
    conn.close()
    
    return jsonify({
        'total_samples': total,
        'by_label': by_label,
        'unique_servers': servers,
        'classes': list(model.classes.keys())
    })

@app.route('/api/model', methods=['GET'])
def get_model():
    """Get model parameters"""
    params = {}
    for label in model.classes:
        params[label] = {
            'count': model.class_counts[label],
            'means': model.class_means[label].tolist(),
            'variances': model.class_vars[label].tolist()
        }
    return jsonify(params)

@app.route('/api/export', methods=['GET'])
def export_model():
    """Export model as JSON for backup"""
    return jsonify({
        'classes': list(model.classes.keys()),
        'params': {
            label: {
                'count': model.class_counts[label],
                'means': model.class_means[label].tolist(),
                'variances': model.class_vars[label].tolist()
            }
            for label in model.classes
        }
    })

@app.route('/api/import', methods=['POST'])
def import_model():
    """Import model from JSON backup"""
    data = request.get_json()
    
    for label, params in data.get('params', {}).items():
        model.classes[label] = True
        model.class_counts[label] = params['count']
        model.class_means[label] = np.array(params['means'])
        model.class_vars[label] = np.array(params['variances'])
    
    model.save_to_db()
    
    return jsonify({'status': 'imported', 'classes': list(model.classes.keys())})

# === MAIN ===

if __name__ == '__main__':
    init_db()
    load_model()
    
    print(f"[ACDetect] Server ready. Classes: {list(model.classes.keys())}")
    print(f"[ACDetect] Samples stored: {sum(model.class_counts.values())}")
    
    # Run on port 5001 (5000 often used by other services)
    app.run(host='0.0.0.0', port=5001, debug=False)
