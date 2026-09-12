#!/bin/bash
# quick_fix.sh - Fix encryption key and restart server

set -e  # Exit on error

echo "=============================================="
echo "QUICK FIX: Encryption Key & Server Restart"
echo "=============================================="

cd /root/DragoniteClient

# Backup files first
echo "[1/5] Creating backups..."
cp acdetect_server.py acdetect_server.py.backup
cp test_learning.py test_learning.py.backup
echo "✓ Backups created"

# Fix encryption key in server (31 chars → 32 chars)
echo "[2/5] Fixing encryption key in acdetect_server.py..."
sed -i "s/ENCRYPTION_KEY = b'DragoniteAC2024SecretKey!!32!!!'/ENCRYPTION_KEY = b'DragoniteAC2024SecretKey!!32!!!!'/g" acdetect_server.py
echo "✓ Server key fixed"

# Fix encryption key in test script
echo "[3/5] Fixing encryption key in test_learning.py..."
sed -i "s/ENCRYPTION_KEY = b'DragoniteAC2024SecretKey!!32!!!'/ENCRYPTION_KEY = b'DragoniteAC2024SecretKey!!32!!!!'/g" test_learning.py
echo "✓ Test script key fixed"

# Verify the changes
echo "[4/5] Verifying encryption key length..."
KEY_LENGTH=$(python3 -c "key = b'DragoniteAC2024SecretKey!!32!!!!'; print(len(key))")
if [ "$KEY_LENGTH" -eq 32 ]; then
    echo "✓ Encryption key is exactly 32 bytes"
else
    echo "✗ ERROR: Key is $KEY_LENGTH bytes (should be 32)"
    exit 1
fi

# Kill any existing server process
echo "[5/5] Stopping any existing server..."
pkill -f acdetect_server.py || true
sleep 2

# Start server in background
echo "Starting server..."
nohup python3 acdetect_server.py > server.log 2>&1 &
sleep 3

# Check if server started successfully
if pgrep -f acdetect_server.py > /dev/null; then
    echo "✓ Server started successfully"
    echo ""
    echo "Server PID: $(pgrep -f acdetect_server.py)"
    echo "Log file: /root/DragoniteClient/server.log"
    echo ""
    echo "View logs: tail -f /root/DragoniteClient/server.log"
else
    echo "✗ Server failed to start"
    echo "Check logs: cat /root/DragoniteClient/server.log"
    exit 1
fi

echo ""
echo "=============================================="
echo "✓ QUICK FIX COMPLETE"
echo "=============================================="
