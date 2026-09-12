# 🔍 COMPLETE DEBUGGING & VERIFICATION GUIDE

## Quick Verification Commands

### On VPS (Server Side)

```bash
# 1. Check if server is running
pgrep -f acdetect_server.py
ps aux | grep acdetect_server

# 2. Check if port 5001 is listening
netstat -tuln | grep 5001
# or
ss -tuln | grep 5001

# 3. Test server endpoint
curl http://localhost:5001/api/stats

# 4. Check database
sqlite3 /root/DragoniteClient/acdetect.db "SELECT COUNT(*) FROM samples;"

# 5. Show recent samples
sqlite3 /root/DragoniteClient/acdetect.db \
  "SELECT id, label, timestamp FROM samples ORDER BY timestamp DESC LIMIT 10;"

# 6. Watch database in real-time
watch -n 2 'sqlite3 /root/DragoniteClient/acdetect.db "SELECT COUNT(*) FROM samples;"'

# 7. Monitor server logs (if running in background)
tail -f /path/to/server.log
# or if in screen/tmux
screen -r acdetect
```

### On Client (Minecraft Side)

```bash
# 1. Find Minecraft logs location
# Windows: %APPDATA%\.minecraft\logs\latest.log
# Linux: ~/.minecraft/logs/latest.log
# macOS: ~/Library/Application Support/minecraft/logs/latest.log

# 2. Watch logs in real-time
tail -f ~/.minecraft/logs/latest.log | grep ACDetect

# 3. Search for specific patterns
grep "\[ACDetect\]" latest.log | tail -20
```

---

## 📋 Expected Log Patterns

### ✅ SUCCESSFUL LEARNING - What You Should See

#### Minecraft Client Logs (latest.log)

```
[ACDetect] ✓ Initialized - accepting ALL packet types
[ACDetect] ✓ Mode: LEARNING
[ACDetect] ✓ Backend: SERVER
[ACDetect] ✓ Label: POLAR
[ACDetect] ✓ Server: hypixel.net
[ACDetect] #1 playerpositions2cpacket
[ACDetect] #2 entitypositions2cpacket
[ACDetect] #3 chatmessages2cpacket
[ACDetect] Progress: 25 packets, window: 25/100, mode: LEARNING, backend: SERVER
[ACDetect] Progress: 50 packets, window: 50/100, mode: LEARNING, backend: SERVER
[ACDetect] Progress: 75 packets, window: 75/100, mode: LEARNING, backend: SERVER
[ACDetect] Progress: 100 packets, window: 100/100, mode: LEARNING, backend: SERVER
[ACDetect] ▶ analyzeWindow: backend=SERVER, mode=LEARNING, label=POLAR, window=100
[ACDetect] ▶ analyzeServerSide: mode=LEARNING, label=POLAR
[ACDetect] learnAsync called: POLAR -> hypixel.net
[ACDetect] Posting to: http://YOUR_ACDETECT_HOST:5001/api/learn
[ACDetect] ▶ Submitting sample #1: POLAR (CV=0.35, mean=42.3ms)
[ACDetect] Learn response: {"status":"learned","label":"POLAR","sample_count":11}
[ACDetect] ✓ LEARNED #1: POLAR -> {"status":"learned","label":"POLAR","sample_count":11}
[ACDetect] ▶ Submitting sample #2: POLAR (CV=0.38, mean=45.1ms)
[ACDetect] ✓ LEARNED #2: POLAR -> {"status":"learned","label":"POLAR","sample_count":12}
```

**Key indicators of success:**
- ✅ "Progress: X packets" messages appearing
- ✅ "analyzeWindow" being called
- ✅ "Submitting sample #N" messages
- ✅ "LEARNED #N" confirmation messages
- ✅ Incrementing sample numbers (#1, #2, #3...)

#### Flask Server Output

```
[ACDetect] Server ready. Classes: ['POLAR', 'VULCAN', 'GRIM', 'MATRIX']
[ACDetect] Samples stored: 40
 * Running on all addresses (0.0.0.0)
 * Running on http://127.0.0.1:5001
 * Running on http://YOUR_ACDETECT_HOST:5001
127.0.0.1 - - [05/Apr/2026 14:23:45] "POST /api/learn HTTP/1.1" 200 -
[ACDetect] Learned: POLAR, total samples: 41
127.0.0.1 - - [05/Apr/2026 14:23:47] "POST /api/learn HTTP/1.1" 200 -
[ACDetect] Learned: POLAR, total samples: 42
127.0.0.1 - - [05/Apr/2026 14:23:49] "POST /api/learn HTTP/1.1" 200 -
[ACDetect] Learned: POLAR, total samples: 43
```

**Key indicators:**
- ✅ HTTP 200 responses (success)
- ✅ "Learned: POLAR" messages
- ✅ Incrementing sample counts

#### SQLite Database Queries

```bash
# Count total samples
sqlite3 /root/DragoniteClient/acdetect.db "SELECT COUNT(*) FROM samples;"
# Output: 43

# Count by label
sqlite3 /root/DragoniteClient/acdetect.db \
  "SELECT label, COUNT(*) FROM samples GROUP BY label;"
# Output:
# POLAR|25
# GRIM|10
# VULCAN|8

# Show recent samples with details
sqlite3 /root/DragoniteClient/acdetect.db \
  "SELECT id, label, server, timestamp FROM samples ORDER BY timestamp DESC LIMIT 5;"
# Output:
# 43|POLAR|hypixel.net|1712326980
# 42|POLAR|hypixel.net|1712326978
# 41|POLAR|hypixel.net|1712326976
```

---

## ❌ FAILURE PATTERNS - What Indicates Problems

### Problem 1: Packets Not Being Captured

**Symptoms:**
```
[ACDetect] ✓ Initialized
[ACDetect] #1 class_2672: confirmtransaction...
[ACDetect] #2 class_2653: keepalive...
[ACDetect] #3 class_2672: confirmtransaction...
... (stuck at low numbers, no "Progress" messages) ...
```

**Diagnosis:**
- Window not filling up (OLD CODE still in place)
- Still filtering UNKNOWN packets

**Fix:**
- Verify you replaced PacketPatternDetector.java with FIXED version
- Rebuild mod completely: `./gradlew clean build` 
- Check that line 145 does NOT have: `if (kind == PacketKind.UNKNOWN) return;` 

---

### Problem 2: Window Fills But No Analysis

**Symptoms:**
```
[ACDetect] Progress: 100 packets, window: 100/100
... (no "analyzeWindow" message appears) ...
```

**Diagnosis:**
- `ANALYSIS_INTERVAL` not triggering
- Backend or mode not set correctly

**Fix:**
```java
// Check these are set:
mode = Mode.LEARNING
backend = Backend.SERVER
learningLabel = "POLAR" (not null)
```

**Verify with:**
```
grep "Mode:" latest.log
grep "Backend:" latest.log
grep "Label:" latest.log
```

---

### Problem 3: Analysis Triggered But No Network Call

**Symptoms:**
```
[ACDetect] ▶ analyzeWindow: backend=SERVER, mode=LEARNING, label=POLAR
[ACDetect] ▶ analyzeServerSide: mode=LEARNING, label=POLAR
... (no "learnAsync called" message) ...
```

**Diagnosis:**
- `DetectionClient.INSTANCE.learnAsync()` not being called
- Check the condition in `analyzeServerSide()` 

**Fix:**
```java
// Verify this code exists:
if (mode == Mode.LEARNING && learningLabel != null) {
    DetectionClient.INSTANCE.learnAsync(...);
}
```

---

### Problem 4: Network Call Made But No Response

**Symptoms:**
```
[ACDetect] learnAsync called: POLAR -> hypixel.net
[ACDetect] Posting to: http://YOUR_ACDETECT_HOST:5001/api/learn
[ACDetect] ✗ Learn error: Connection refused
```

**Diagnosis:**
- Server not running
- Firewall blocking port 5001
- Wrong server URL

**Fix:**
```bash
# On VPS:
# 1. Check server is running
pgrep -f acdetect_server

# 2. Start if not running
cd /root/DragoniteClient
python3 acdetect_server.py &

# 3. Check firewall
sudo ufw status
sudo ufw allow 5001/tcp

# 4. Test connectivity from client
telnet YOUR_ACDETECT_HOST 5001
# or
nc -zv YOUR_ACDETECT_HOST 5001
```

---

### Problem 5: Network Call Succeeds But Encryption Error

**Symptoms:**
```
[ACDetect] Posting to: http://YOUR_ACDETECT_HOST:5001/api/learn
[ACDetect] ✗ Learn error: Decryption failed
```

**Server logs show:**
```
[ACDetect] Learn error: Decryption failed: Incorrect padding
```

**Diagnosis:**
- Encryption key mismatch
- Wrong encryption algorithm

**Fix:**
```java
// DetectionClient.java (line 12)
private static final String ENCRYPTION_KEY = "DragoniteAC2024SecretKey!!32!";
```

```python
# acdetect_server.py (line 21)
ENCRYPTION_KEY = b'DragoniteAC2024SecretKey!!32!'
```

**MUST BE EXACTLY THE SAME (32 characters)!**

---

### Problem 6: Server Receives But Doesn't Store

**Symptoms:**
```
[ACDetect] ✓ LEARNED #1: POLAR -> {"status":"learned","label":"POLAR"}
```

But database shows 0 samples:
```bash
sqlite3 /root/DragoniteClient/acdetect.db "SELECT COUNT(*) FROM samples;"
# 0
```

**Diagnosis:**
- Database write error
- Wrong database path
- Table doesn't exist

**Fix:**
```bash
# Check if database exists
ls -l /root/DragoniteClient/acdetect.db

# Check table schema
sqlite3 /root/DragoniteClient/acdetect.db ".schema samples"

# Recreate database if needed
cd /root/DragoniteClient
rm acdetect.db
python3 acdetect_server.py  # Will create fresh database
```

---

## 🧪 Step-by-Step Verification Procedure

### Step 1: Verify Server Is Running

```bash
# On VPS
cd /root/DragoniteClient

# Option A: Run in foreground (see logs directly)
python3 acdetect_server.py

# Option B: Run in background with screen
screen -S acdetect
python3 acdetect_server.py
# Press Ctrl+A then D to detach

# Option C: Run with nohup
nohup python3 acdetect_server.py > server.log 2>&1 &
```

**Expected output:**
```
[ACDetect] Server ready. Classes: ['POLAR', 'VULCAN', 'GRIM', 'MATRIX']
[ACDetect] Samples stored: 0
 * Running on all addresses (0.0.0.0)
 * Running on http://127.0.0.1:5001
```

---

### Step 2: Test Server With Manual Request

```bash
# On VPS
python3 test_learning.py --local

# Expected output:
# ✅ ALL TESTS PASSED
```

If this fails, server is broken. If it succeeds, client is the problem.

---

### Step 3: Check Minecraft Configuration

In Minecraft, run these commands:
```
/acdetect mode learning
/acdetect backend server
/acdetect label POLAR
/acdetect status
```

Check logs for:
```
[ACDetect] ✓ Mode: LEARNING
[ACDetect] ✓ Backend: SERVER
[ACDetect] ✓ Label: POLAR
```

---

### Step 4: Join Server and Monitor Logs

```bash
# On client machine
tail -f ~/.minecraft/logs/latest.log | grep ACDetect

# On VPS
watch -n 2 'sqlite3 /root/DragoniteClient/acdetect.db "SELECT COUNT(*) FROM samples;"'
```

Within 30 seconds of joining, you should see:
- Packets being collected (Progress messages)
- Analysis being triggered
- Samples being submitted
- Database count increasing

---

### Step 5: Verify Data Quality

```bash
# Check feature values are reasonable
sqlite3 /root/DragoniteClient/acdetect.db \
  "SELECT features FROM samples LIMIT 1;" | python3 -m json.tool

# Expected: 18 floating-point numbers
# [35.2, 14.8, 0.42, 25.3, 35.1, ...]
```

**Feature sanity checks:**
- Feature[0] (mean interval): 10-100ms (reasonable packet timing)
- Feature[2] (CV): 0.1-1.0 (reasonable variance)
- All features should be finite numbers (not NaN or Inf)

---

## 🎯 Success Criteria Checklist

After 5 minutes of gameplay:

- [ ] **500+ packets** recorded (check "Progress" messages)
- [ ] **5-10 samples** submitted (check "LEARNED #N" messages)
- [ ] **Database shows 5-10 new rows** (run count query)
- [ ] **No error messages** in client or server logs
- [ ] **HTTP 200 responses** from server
- [ ] **Feature values look reasonable** (check sample data)
- [ ] **Sample count incrementing** over time

---

## 🔧 Quick Debugging Commands

### One-Line Database Check
```bash
sqlite3 /root/DragoniteClient/acdetect.db \
  "SELECT COUNT(*), MIN(timestamp), MAX(timestamp) FROM samples;"
```

### Live Monitoring Setup
```bash
# Terminal 1: Watch database
watch -n 1 'sqlite3 /root/DragoniteClient/acdetect.db "SELECT label, COUNT(*) FROM samples GROUP BY label;"'

# Terminal 2: Watch server logs
tail -f server.log | grep "Learned:"

# Terminal 3: Watch client logs
tail -f ~/.minecraft/logs/latest.log | grep "ACDetect"
```

### Network Connectivity Test
```bash
# From client machine to server
curl -v http://YOUR_ACDETECT_HOST:5001/api/stats

# Expected: HTTP 200 with JSON response
```

### Complete System Test
```bash
# On VPS
./verification_checklist.sh

# Expected: All checks pass with ✓
```

---

## 📊 Troubleshooting Decision Tree

```
Is server running?
├─ NO → Start server: python3 acdetect_server.py
└─ YES → Is port 5001 listening?
    ├─ NO → Check firewall, restart server
    └─ YES → Can you curl /api/stats?
        ├─ NO → Server crashed, check python errors
        └─ YES → Are Minecraft logs showing packets?
            ├─ NO → Mod not loaded or broken build
            └─ YES → Window filling up (100/100)?
                ├─ NO → Still using OLD code, rebuild
                └─ YES → analyzeWindow() being called?
                    ├─ NO → Check mode/backend/label settings
                    └─ YES → learnAsync() being called?
                        ├─ NO → Check analyzeServerSide() logic
                        └─ YES → Getting response from server?
                            ├─ NO → Network issue, check connectivity
                            └─ YES → Database growing?
                                ├─ NO → Server DB write error
                                └─ YES → ✅ EVERYTHING WORKING!
```

---

## 🚨 Common Mistakes

1. **Encryption key mismatch** - Most common error
   - Java uses String, Python uses bytes
   - Must be EXACTLY 32 characters

2. **Wrong database path** - Server can't find DB
   - Check absolute path
   - Check file permissions

3. **Firewall blocking** - Can't reach server
   - Port 5001 must be open
   - Check with: `sudo ufw status` 

4. **Old code still running** - Window not filling
   - Must rebuild after fixing
   - Check jar file timestamp

5. **Mode not set** - Learning not triggered
   - Must explicitly set LEARNING mode
   - Must set label before starting

6. **Wrong server URL** - Client can't connect
   - Check DetectionClient.java line 11
   - Must match VPS IP

---

## 📞 Getting Help

If still stuck, collect this info:

```bash
# 1. Server status
pgrep -f acdetect_server
curl http://localhost:5001/api/stats

# 2. Database state
sqlite3 /root/DragoniteClient/acdetect.db \
  "SELECT COUNT(*) FROM samples; SELECT * FROM sqlite_master WHERE type='table';"

# 3. Last 50 client log lines
grep ACDetect latest.log | tail -50

# 4. Last 20 server log lines
tail -20 server.log

# 5. Network test
curl -v http://YOUR_ACDETECT_HOST:5001/api/stats
```

Include all of the above output when asking for help.
