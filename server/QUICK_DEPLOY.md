# 🚀 QUICK DEPLOYMENT GUIDE

## Issue 1: Server Crashes (Encryption Key)

### Root Cause
```python
# WRONG (31 characters):
ENCRYPTION_KEY = b'DragoniteAC2024SecretKey!!32!!!'

# CORRECT (32 characters):
ENCRYPTION_KEY = b'DragoniteAC2024SecretKey!!32!!!!'
#                                            ^ Added one more '!'
```

### Quick Fix (Run on VPS)
```bash
cd /root/DragoniteClient

# Option 1: Use the fix script
chmod +x quick_fix.sh
./quick_fix.sh

# Option 2: Manual sed commands
sed -i "s/DragoniteAC2024SecretKey!!32!!!/DragoniteAC2024SecretKey!!32!!!!/g" acdetect_server.py
sed -i "s/DragoniteAC2024SecretKey!!32!!!/DragoniteAC2024SecretKey!!32!!!!/g" test_learning.py

# Verify the fix
python3 -c "key = b'DragoniteAC2024SecretKey!!32!!!!'; print('Key length:', len(key), '✓' if len(key)==32 else '✗')"

# Start server
pkill -f acdetect_server.py  # Kill old process
python3 acdetect_server.py &  # Start new process

# Check if running
pgrep -f acdetect_server && echo "✓ Server running" || echo "✗ Server not running"
```

---

## Issue 2: Upgrade to v2.0 (Event ID Monitoring)

### What's New
- **21 features** instead of 18 (added event ID tracking)
- **Better AC classification** (Polar uses -1, Vulcan uses 50-200, etc.)
- **Fixed encryption key** (exactly 32 bytes)
- **Better error handling** and logging

### Deployment Steps

#### On VPS:
```bash
cd /root/DragoniteClient

# 1. Backup current server
cp acdetect_server.py acdetect_server.py.old

# 2. Upload new server v2
# (Copy acdetect_server_v2.py from outputs folder)

# 3. Replace old server
cp acdetect_server_v2.py acdetect_server.py

# 4. Restart server
pkill -f acdetect_server.py
sleep 2
python3 acdetect_server.py &

# 5. Verify it's working
sleep 3
curl http://localhost:5001/api/health | python3 -m json.tool

# Expected output:
# {
#   "status": "healthy",
#   "version": "2.0",
#   "n_features": 21,
#   "classes": ["POLAR", "VULCAN", "GRIM", "MATRIX"]
# }
```

#### On Client (Java):
```bash
# Update PacketPatternDetector.java with event ID extraction
# See EVENT_ID_GUIDE.md for detailed changes

# Key changes:
# 1. N_FEATURES = 21 (was 18)
# 2. Add eventId to PacketEvent record
# 3. Add extractEventId() method
# 4. Update extractFeatures() to calculate event ID features

# Rebuild mod
./gradlew clean build

# Copy to Minecraft
cp build/libs/your-mod.jar ~/.minecraft/mods/
```

---

## Verification Checklist

### Server-Side (VPS)
```bash
# 1. Check encryption key
grep ENCRYPTION_KEY acdetect_server.py
# Should show: ENCRYPTION_KEY = b'DragoniteAC2024SecretKey!!32!!!!' (32 chars)

# 2. Check N_FEATURES
grep N_FEATURES acdetect_server.py
# Should show: N_FEATURES = 21

# 3. Check server is running
pgrep -f acdetect_server && echo "✓ Running" || echo "✗ Not running"

# 4. Test health endpoint
curl http://localhost:5001/api/health

# 5. Check stats
curl http://localhost:5001/api/stats | python3 -m json.tool

# 6. View server logs
tail -20 server.log
```

### Client-Side (Minecraft)
```bash
# 1. Check logs for feature count
grep "N_FEATURES" PacketPatternDetector.java
# Should show: private static final int N_FEATURES = 21;

# 2. After joining server, check Minecraft logs
grep "Progress:" latest.log | tail -5

# 3. Look for event ID logging
grep "Event ID" latest.log | tail -10

# 4. Check if samples are being sent
grep "LEARNED" latest.log | tail -5
```

---

## Testing Event ID Extraction

### Method 1: Debug Logs
Add to PacketPatternDetector.java:
```java
if (totalPacketsRecorded <= 20) {
    System.out.println(String.format("[ACDetect] #%d EventID=%d Type=%s", 
        totalPacketsRecorded, eventId, kind));
}
```

### Method 2: Database Inspection
```bash
# On VPS - check recent features
sqlite3 /root/DragoniteClient/acdetect.db \
  "SELECT json_extract(features, '$[18]') as event_mean,
          json_extract(features, '$[19]') as event_var,
          json_extract(features, '$[20]') as event_range
   FROM samples ORDER BY timestamp DESC LIMIT 5;"

# Expected for Polar:
# -1.0|0.1|2.0
# -0.8|0.2|1.5
```

---

## Common Issues & Fixes

| Problem | Symptom | Fix |
|---------|---------|-----|
| **Key too short** | `ValueError: Incorrect AES key length` | Use 32-char key with 4 '!' at end |
| **Wrong N_FEATURES** | `Expected 21 features, got 18` | Update both server and client to 21 |
| **Server won't start** | `Address already in use` | Kill old process: `pkill -f acdetect_server` |
| **No event IDs** | All event IDs are 0 | Check `extractEventId()` implementation |
| **Old code running** | Still getting 18 features | Rebuild client: `./gradlew clean build` |

---

## Quick Commands Reference

```bash
# === VPS Commands ===

# Fix encryption key
sed -i "s/!!32!!!/!!32!!!!/g" acdetect_server.py

# Restart server
pkill -f acdetect_server && python3 acdetect_server.py &

# Check server health
curl localhost:5001/api/health | python3 -m json.tool

# Watch database grow
watch -n 2 'sqlite3 acdetect.db "SELECT COUNT(*) FROM samples;"'

# View recent event IDs
sqlite3 acdetect.db "SELECT json_extract(features, '\$[18]') FROM samples ORDER BY timestamp DESC LIMIT 10;"

# Check server logs
tail -f server.log


# === Client Commands ===

# Rebuild mod
./gradlew clean build

# Watch Minecraft logs
tail -f ~/.minecraft/logs/latest.log | grep ACDetect

# Check feature count in code
grep "N_FEATURES" src/main/java/*/PacketPatternDetector.java
```

---

## Expected Results After Fix

### Server Startup:
```
============================================================
ANTICHEAT DETECTION SERVER v2.0
============================================================
[ACDetect] Encryption key: 32 bytes
[ACDetect] ✓ Database initialized
[ACDetect] ✓ Loaded 4 classes
[ACDetect] ✓ Features: 21
[ACDetect] ✓ Classes: ['POLAR', 'VULCAN', 'GRIM', 'MATRIX']
[ACDetect] ✓ Samples: 40
============================================================
 * Running on all addresses (0.0.0.0)
 * Running on http://127.0.0.1:5001
```

### Client Logs:
```
[ACDetect] Progress: 100 packets, window: 100/100
[ACDetect] ▶ analyzeWindow: backend=SERVER, mode=LEARNING, label=POLAR
[ACDetect] ▶ Submitting sample #1: POLAR (CV=0.35, mean=42.3ms)
[ACDetect] Event ID stats: mean=-0.8, range=1.5
[ACDetect] ✓ LEARNED #1: POLAR -> {"status":"learned","sample_count":11}
```

### Database Check:
```bash
sqlite3 acdetect.db "SELECT label, COUNT(*) FROM samples GROUP BY label;"
# POLAR|15
# GRIM|5
# VULCAN|3
```

---

## Rollback Plan

If something breaks:

```bash
# On VPS
cd /root/DragoniteClient
cp acdetect_server.py.old acdetect_server.py
pkill -f acdetect_server
python3 acdetect_server.py &

# On Client
# Use old mod jar from before changes
```

---

## Success Indicators

- ✅ Server starts without errors
- ✅ `curl localhost:5001/api/health` returns `"version": "2.0"` and `"n_features": 21` 
- ✅ Minecraft logs show packets being collected
- ✅ Event ID values appear in features (index 18-20)
- ✅ Database sample count increases
- ✅ No "Expected 18 features" errors

---

## Getting Help

If stuck, provide:
1. Server startup logs
2. Output of: `curl localhost:5001/api/health` 
3. Minecraft latest.log (last 50 lines with [ACDetect])
4. Database query: `SELECT COUNT(*) FROM samples;` 
5. Encryption key check: `grep ENCRYPTION_KEY acdetect_server.py`
