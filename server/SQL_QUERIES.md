# 📊 SQLite Database Inspection - Quick Reference

## Database Location
```bash
/root/DragoniteClient/acdetect.db
```

## Quick Queries

### 1. Count Total Samples
```sql
SELECT COUNT(*) FROM samples;
```
Expected: Number increasing over time (e.g., 0 → 5 → 10 → 25...)

### 2. Count Samples by Label
```sql
SELECT label, COUNT(*) AS count 
FROM samples 
GROUP BY label 
ORDER BY count DESC;
```
Expected output:
```
POLAR|25
GRIM|10
VULCAN|8
```

### 3. Show Recent Samples
```sql
SELECT id, label, server, timestamp 
FROM samples 
ORDER BY timestamp DESC 
LIMIT 10;
```

### 4. Show Oldest Samples
```sql
SELECT id, label, server, timestamp 
FROM samples 
ORDER BY timestamp ASC 
LIMIT 10;
```

### 5. Check Timestamp Range
```sql
SELECT 
    MIN(timestamp) as first_sample,
    MAX(timestamp) as last_sample,
    MAX(timestamp) - MIN(timestamp) as duration_seconds
FROM samples;
```

### 6. Samples Per Hour
```sql
SELECT 
    datetime(timestamp, 'unixepoch') as hour,
    COUNT(*) as samples
FROM samples
GROUP BY strftime('%Y-%m-%d %H', datetime(timestamp, 'unixepoch'))
ORDER BY hour DESC
LIMIT 24;
```

### 7. Show Full Sample with Features
```sql
SELECT 
    id,
    label,
    server,
    license_hash,
    features,
    datetime(timestamp, 'unixepoch') as time
FROM samples
ORDER BY timestamp DESC
LIMIT 1;
```

### 8. Check for Specific Server
```sql
SELECT label, COUNT(*) 
FROM samples 
WHERE server LIKE '%hypixel%' 
GROUP BY label;
```

### 9. Check for Specific License
```sql
SELECT label, COUNT(*) 
FROM samples 
WHERE license_hash = 'your_hash_here' 
GROUP BY label;
```

### 10. Verify Feature Array Length
```sql
SELECT 
    id,
    label,
    json_array_length(features) as feature_count
FROM samples
LIMIT 10;
```
Expected: All should show 18 (N_FEATURES)

---

## Useful One-Liners

### Watch Database Grow
```bash
watch -n 2 'sqlite3 /root/DragoniteClient/acdetect.db "SELECT COUNT(*) FROM samples;"'
```

### Pretty Print Latest Sample
```bash
sqlite3 /root/DragoniteClient/acdetect.db \
  "SELECT features FROM samples ORDER BY timestamp DESC LIMIT 1;" \
  | python3 -m json.tool
```

### Export All Samples to JSON
```bash
sqlite3 /root/DragoniteClient/acdetect.db <<EOF
.mode json
.output samples_export.json
SELECT * FROM samples;
.output stdout
EOF
```

### Export Samples to CSV
```bash
sqlite3 /root/DragoniteClient/acdetect.db <<EOF
.mode csv
.headers on
.output samples_export.csv
SELECT id, label, server, timestamp FROM samples;
.output stdout
EOF
```

### Check Table Schema
```bash
sqlite3 /root/DragoniteClient/acdetect.db ".schema samples"
```

Expected output:
```sql
CREATE TABLE samples (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    license_hash TEXT NOT NULL,
    server TEXT,
    label TEXT,
    timestamp INTEGER,
    features TEXT
);
CREATE INDEX idx_samples_label ON samples(label);
CREATE INDEX idx_samples_license ON samples(license_hash);
```

---

## Diagnostic Queries

### Check for Duplicate Samples
```sql
SELECT 
    label,
    server,
    timestamp,
    COUNT(*) as duplicates
FROM samples
GROUP BY label, server, timestamp
HAVING COUNT(*) > 1;
```
Expected: Empty result (no duplicates)

### Check for NULL Values
```sql
SELECT 
    COUNT(*) as total,
    SUM(CASE WHEN label IS NULL THEN 1 ELSE 0 END) as null_labels,
    SUM(CASE WHEN features IS NULL THEN 1 ELSE 0 END) as null_features,
    SUM(CASE WHEN timestamp IS NULL THEN 1 ELSE 0 END) as null_timestamps
FROM samples;
```
Expected: All null counts should be 0

### Check Feature Value Ranges
```sql
-- Extract first feature (mean interval) from JSON
SELECT 
    id,
    label,
    json_extract(features, '$[0]') as mean_interval
FROM samples
ORDER BY timestamp DESC
LIMIT 10;
```
Expected: Values between 10-100ms

### Check for Malformed Features
```sql
SELECT 
    id,
    label,
    features
FROM samples
WHERE 
    json_valid(features) = 0
    OR json_array_length(features) != 18
LIMIT 10;
```
Expected: Empty result (all features should be valid)

---

## Model Parameters Queries

### View All Classes
```sql
SELECT label FROM model_params;
```

### View Class Sample Counts
```sql
SELECT 
    label,
    count as training_samples
FROM model_params
ORDER BY count DESC;
```

### View Mean Features for a Class
```sql
SELECT 
    label,
    means
FROM model_params
WHERE label = 'POLAR';
```

### Compare Class Means
```sql
SELECT 
    label,
    json_extract(means, '$[0]') as mean_interval,
    json_extract(means, '$[2]') as cv
FROM model_params;
```

---

## Maintenance Queries

### Delete Old Samples (older than 30 days)
```sql
DELETE FROM samples 
WHERE timestamp < strftime('%s', 'now', '-30 days');
```

### Delete Samples for Specific Label
```sql
DELETE FROM samples 
WHERE label = 'TEST_LABEL';
```

### Vacuum Database (reclaim space)
```sql
VACUUM;
```

### Analyze Database (optimize queries)
```sql
ANALYZE;
```

---

## Performance Queries

### Check Database Size
```bash
ls -lh /root/DragoniteClient/acdetect.db
```

### Count Rows Per Table
```sql
SELECT 
    'samples' as table_name, 
    COUNT(*) as rows 
FROM samples
UNION ALL
SELECT 
    'model_params' as table_name, 
    COUNT(*) as rows 
FROM model_params
UNION ALL
SELECT 
    'licenses' as table_name, 
    COUNT(*) as rows 
FROM licenses;
```

### Check Index Usage
```sql
SELECT * FROM sqlite_master WHERE type = 'index';
```

---

## Backup & Restore

### Backup Database
```bash
sqlite3 /root/DragoniteClient/acdetect.db ".backup backup_$(date +%Y%m%d_%H%M%S).db"
```

### Restore from Backup
```bash
cp backup_20260405_142300.db /root/DragoniteClient/acdetect.db
```

### Export Schema Only
```bash
sqlite3 /root/DragoniteClient/acdetect.db .schema > schema.sql
```

---

## Python Integration

### Query from Python Script
```python
import sqlite3
import json

conn = sqlite3.connect('/root/DragoniteClient/acdetect.db')
cursor = conn.cursor()

# Get recent samples
cursor.execute("""
    SELECT id, label, features, timestamp 
    FROM samples 
    ORDER BY timestamp DESC 
    LIMIT 10
""")

for row in cursor.fetchall():
    id, label, features_json, timestamp = row
    features = json.loads(features_json)
    print(f"ID: {id}, Label: {label}, Features: {len(features)}")

conn.close()
```

### Watch Database from Python
```python
import sqlite3
import time

conn = sqlite3.connect('/root/DragoniteClient/acdetect.db')
cursor = conn.cursor()

cursor.execute("SELECT COUNT(*) FROM samples")
last_count = cursor.fetchone()[0]
print(f"Initial count: {last_count}")

while True:
    time.sleep(2)
    cursor.execute("SELECT COUNT(*) FROM samples")
    current_count = cursor.fetchone()[0]
    
    if current_count > last_count:
        new_samples = current_count - last_count
        print(f"[+{new_samples}] Total: {current_count}")
        
        # Show latest
        cursor.execute("""
            SELECT label, timestamp 
            FROM samples 
            ORDER BY timestamp DESC 
            LIMIT 1
        """)
        label, ts = cursor.fetchone()
        print(f"    Latest: {label} at {ts}")
        
        last_count = current_count

conn.close()
```

---

## Troubleshooting Database Issues

### Database Locked Error
```bash
# Check for processes holding the database
lsof /root/DragoniteClient/acdetect.db

# Kill any stuck processes
kill -9 <PID>
```

### Corrupted Database
```bash
# Check integrity
sqlite3 /root/DragoniteClient/acdetect.db "PRAGMA integrity_check;"

# If corrupted, try to recover
sqlite3 /root/DragoniteClient/acdetect.db ".recover" | sqlite3 recovered.db
```

### Recreate Database
```bash
# Backup first!
cp acdetect.db acdetect.db.backup

# Remove and let server recreate
rm acdetect.db
python3 acdetect_server.py  # Will initialize fresh database
```

---

## Success Indicators

After 5 minutes of learning, you should see:

```sql
-- Should have 5-10+ samples
SELECT COUNT(*) FROM samples;  
-- Result: 10

-- Should show your label
SELECT DISTINCT label FROM samples;
-- Result: POLAR

-- Should have recent timestamps
SELECT MAX(timestamp) - MIN(timestamp) as duration FROM samples;
-- Result: ~300 (5 minutes)

-- Features should be valid JSON arrays of 18 numbers
SELECT json_valid(features), json_array_length(features) 
FROM samples 
LIMIT 1;
-- Result: 1|18
```

If all these queries return expected results, **learning is working! ✅**
