# 📝 EVENT ID MONITORING - Implementation Guide

## What Are Event IDs?

Event IDs are identifiers in packet data that anticheats use to track actions:

- **Polar AC**: Uses event ID **-1** (or negative values) for transaction packets
- **Vulcan AC**: Uses specific positive event IDs (50-200 range)
- **Grim AC**: Uses event ID **0** or small positive values
- **Matrix AC**: Uses mixed event ID patterns

These patterns help distinguish different anticheats.

---

## Java Client Changes

### 1. Update N_FEATURES Constant

**File:** `PacketPatternDetector.java`  
**Line:** ~24

```java
// OLD:
private static final int N_FEATURES = 18;

// NEW:
private static final int N_FEATURES = 21;  // Added 3 event ID features
```

---

### 2. Update PacketEvent Record

**File:** `PacketPatternDetector.java`  
**Line:** ~680

```java
// OLD:
private record PacketEvent(long nanoTime, int actionId, PacketKind kind) {}

// NEW:
private record PacketEvent(long nanoTime, int actionId, int eventId, PacketKind kind) {}
```

---

### 3. Update recordRawPacket Method

**File:** `PacketPatternDetector.java`  
**Line:** ~145

```java
public void recordRawPacket(Object packet) {
    if (packet == null) return;
    
    String className = packet.getClass().getSimpleName().toLowerCase();
    String packetStr = packet.toString().toLowerCase();
    
    if (logCounter < 20) {
        logCounter++;
        System.out.println(String.format("[ACDetect] #%d %s", logCounter, className));
    }
    
    PacketKind kind = PacketKind.fromClassName(className);
    
    // Extract action ID (existing code)
    int actionId = extractActionNumber(packetStr);
    
    // ✅ NEW: Extract event ID
    int eventId = extractEventId(packet, packetStr, className);
    
    long timestamp = System.nanoTime();
    
    // ✅ CHANGED: Add eventId parameter
    PacketEvent event = new PacketEvent(timestamp, actionId, eventId, kind);
    slidingWindow.addLast(event);
    if (slidingWindow.size() > WINDOW_SIZE) slidingWindow.pollFirst();
    
    totalPacketsRecorded++;
    
    if (totalPacketsRecorded % 25 == 0) {
        System.out.println(String.format("[ACDetect] Progress: %d packets, window: %d/%d",
            totalPacketsRecorded, slidingWindow.size(), WINDOW_SIZE));
    }
    
    if (++packetsSinceAnalysis >= ANALYSIS_INTERVAL && slidingWindow.size() >= MIN_SAMPLES) {
        packetsSinceAnalysis = 0;
        analyzeWindow();
    }
}
```

---

### 4. Add extractEventId Method

**File:** `PacketPatternDetector.java`  
**Add after extractActionNumber method (~170)**

```java
/**
 * Extract event ID from packet
 * Different anticheats use different event ID patterns:
 * - Polar: -1 or negative
 * - Vulcan: 50-200 range
 * - Grim: 0 or small positive
 * - Matrix: mixed patterns
 */
private int extractEventId(Object packet, String packetStr, String className) {
    try {
        // Method 1: Look for "id=" or "eventid=" in packet string
        String[] patterns = {"id=", "eventid=", "event=", "windowid=", "uid="};
        for (String pattern : patterns) {
            int idx = packetStr.indexOf(pattern);
            if (idx >= 0) {
                String sub = packetStr.substring(idx + pattern.length());
                StringBuilder num = new StringBuilder();
                for (char c : sub.toCharArray()) {
                    if (Character.isDigit(c) || c == '-') {
                        num.append(c);
                    } else {
                        break;
                    }
                }
                if (num.length() > 0) {
                    return Integer.parseInt(num.toString());
                }
            }
        }
        
        // Method 2: Use reflection to get event/window ID field
        // Transaction packets often have "windowId" or "actionNumber" fields
        try {
            // Try getting windowId field
            java.lang.reflect.Field windowField = packet.getClass().getDeclaredField("windowId");
            windowField.setAccessible(true);
            Object value = windowField.get(packet);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (NoSuchFieldException ignored) {}
        
        try {
            // Try getting id field
            java.lang.reflect.Field idField = packet.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            Object value = idField.get(packet);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (NoSuchFieldException ignored) {}
        
        // Method 3: Check packet type for default values
        if (className.contains("transaction") || className.contains("class_2672")) {
            // Transaction packets - look for window ID
            // Polar typically uses -1 or negative values
            // Check if packet string contains negative number
            if (packetStr.contains("=-")) {
                return -1;  // Likely Polar
            }
        }
        
        // Default: return 0 (no event ID found)
        return 0;
        
    } catch (Exception e) {
        // Failed to extract - return 0
        return 0;
    }
}
```

---

### 5. Update extractFeatures Method

**File:** `PacketPatternDetector.java`  
**Line:** ~240

```java
private float[] extractFeatures(List<PacketEvent> events) {
    float[] features = new float[N_FEATURES];  // Now 21 features
    if (events.size() < 2) return features;
    
    // Calculate intervals
    List<Float> intervals = new ArrayList<>();
    List<Integer> actionIds = new ArrayList<>();
    List<Integer> eventIds = new ArrayList<>();  // ✅ NEW
    int keepaliveCount = 0, transactionCount = 0;
    
    for (int i = 1; i < events.size(); i++) {
        float intervalMs = (events.get(i).nanoTime - events.get(i-1).nanoTime) / 1_000_000f;
        intervals.add(intervalMs);
        actionIds.add(events.get(i).actionId);
        eventIds.add(events.get(i).eventId);  // ✅ NEW
    }
    
    for (PacketEvent e : events) {
        if (e.kind == PacketKind.KEEPALIVE) keepaliveCount++;
        else if (e.kind == PacketKind.TRANSACTION) transactionCount++;
    }
    
    // Sort for quartiles
    List<Float> sorted = new ArrayList<>(intervals);
    Collections.sort(sorted);
    int n = sorted.size();
    
    // Features 0-17: Original timing and action features
    features[0] = calculateMean(intervals);
    features[1] = calculateStdDev(intervals, features[0]);
    features[2] = features[0] > 0 ? features[1] / features[0] : 0;
    features[3] = sorted.get((int)(n * 0.25));
    features[4] = sorted.get(n / 2);
    features[5] = sorted.get((int)(n * 0.75));
    features[6] = features[5] - features[3];
    features[7] = autocorrelation(intervals, 50f);
    features[8] = autocorrelation(intervals, 100f);
    features[9] = bimodalityCoefficient(intervals);
    features[10] = estimateQuantizationStep(intervals);
    
    List<Integer> deltas = calculateDeltas(actionIds);
    features[11] = entropy(actionIds);
    features[12] = entropy(deltas);
    features[13] = deltaUniformity(deltas);
    features[14] = longestSequentialRun(deltas);
    features[15] = transactionCount > 0 ? (float) keepaliveCount / transactionCount : 0f;
    features[16] = burstDensity(intervals);
    features[17] = jitterTrend(intervals);
    
    // ✅ NEW: Features 18-20: Event ID features
    features[18] = calculateMeanEventId(eventIds);      // Mean event ID
    features[19] = calculateEventIdVariance(eventIds);   // Event ID variance
    features[20] = calculateEventIdRange(eventIds);      // Event ID range (max - min)
    
    return features;
}
```

---

### 6. Add Event ID Calculation Methods

**File:** `PacketPatternDetector.java`  
**Add after existing statistical methods (~600)**

```java
/**
 * Calculate mean event ID
 */
private float calculateMeanEventId(List<Integer> eventIds) {
    if (eventIds.isEmpty()) return 0f;
    
    float sum = 0f;
    for (int id : eventIds) {
        sum += id;
    }
    return sum / eventIds.size();
}

/**
 * Calculate event ID variance
 */
private float calculateEventIdVariance(List<Integer> eventIds) {
    if (eventIds.isEmpty()) return 0f;
    
    float mean = calculateMeanEventId(eventIds);
    float sumSq = 0f;
    
    for (int id : eventIds) {
        float diff = id - mean;
        sumSq += diff * diff;
    }
    
    return sumSq / eventIds.size();
}

/**
 * Calculate event ID range (max - min)
 * This helps identify AC patterns:
 * - Polar: small range (around -1)
 * - Vulcan: large range (50-200)
 * - Grim: small range (0-10)
 */
private float calculateEventIdRange(List<Integer> eventIds) {
    if (eventIds.isEmpty()) return 0f;
    
    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    
    for (int id : eventIds) {
        if (id < min) min = id;
        if (id > max) max = id;
    }
    
    return (float)(max - min);
}
```

---

## Testing Event ID Extraction

### Test on Different Servers

1. **Polar Server** - Look for:
   ```
   [ACDetect] Event IDs: [-1, -1, -1, -1, 0, -1, ...]
   [ACDetect] Event ID mean: -0.8
   [ACDetect] Event ID range: 1.0
   ```

2. **Vulcan Server** - Look for:
   ```
   [ACDetect] Event IDs: [100, 105, 110, 98, 112, ...]
   [ACDetect] Event ID mean: 105.0
   [ACDetect] Event ID range: 14.0
   ```

3. **Grim Server** - Look for:
   ```
   [ACDetect] Event IDs: [0, 0, 1, 0, 2, 0, ...]
   [ACDetect] Event ID mean: 0.5
   [ACDetect] Event ID range: 2.0
   ```

---

## Debug Logging

Add this to `recordRawPacket` for testing:

```java
// Debug: Log first 10 event IDs
if (totalPacketsRecorded <= 10) {
    System.out.println(String.format("[ACDetect] Event ID #%d: %d (from %s)", 
        totalPacketsRecorded, eventId, className));
}

// Debug: Log event ID statistics every 100 packets
if (totalPacketsRecorded % 100 == 0) {
    List<Integer> eventIds = slidingWindow.stream()
        .map(e -> e.eventId)
        .collect(Collectors.toList());
    
    float mean = calculateMeanEventId(eventIds);
    float range = calculateEventIdRange(eventIds);
    
    System.out.println(String.format("[ACDetect] Event ID stats: mean=%.1f, range=%.1f", 
        mean, range));
}
```

---

## Common Packet Fields for Event IDs

### Transaction Packets (class_2672):
- `windowId` field - The window/inventory ID
- `actionNumber` field - Sequential action counter
- `accepted` field - Boolean (use 1 or 0)

### Keep-Alive Packets (class_2653):
- `id` field - Keep-alive ID
- Usually sequential, not useful for AC detection

### Custom Payload Packets (class_2658):
- `channel` field - Plugin channel name (hash it to int)
- AC plugins use specific channels

---

## Expected Feature Values

| Anticheat | Event ID Mean | Event ID Variance | Event ID Range |
|-----------|---------------|-------------------|----------------|
| Polar     | -1.0 to 0.0   | 0.1 - 0.5         | 0 - 2          |
| Vulcan    | 50 - 150      | 10 - 100          | 50 - 200       |
| Grim      | 0.0 - 2.0     | 0.5 - 2.0         | 0 - 10         |
| Matrix    | 10 - 80       | 5 - 50            | 20 - 100       |

---

## Troubleshooting

### Problem: All event IDs are 0
**Solution:** Event ID extraction is failing. Add debug logs to `extractEventId()` to see packet structure.

### Problem: Event IDs look random
**Solution:** You might be extracting the wrong field. Check packet toString() output for patterns.

### Problem: Server rejects 21-feature samples
**Solution:** Make sure server `N_FEATURES = 21` and restart it.

---

## Complete Example

```java
// Example packet processing flow:

// 1. Packet arrives
Object packet = ...;  // ConfirmTransactionS2CPacket
String packetStr = packet.toString();
// "ConfirmTransactionS2CPacket{windowId=-1, actionNumber=123, accepted=true}"

// 2. Extract event ID
int eventId = extractEventId(packet, packetStr, "confirmtransaction");
// eventId = -1 (found "windowId=-1")

// 3. Store in event
PacketEvent event = new PacketEvent(timestamp, 123, -1, PacketKind.TRANSACTION);

// 4. After 100 packets, calculate features
List<Integer> eventIds = [-1, -1, 0, -1, -1, -1, 0, ...];
float mean = calculateMeanEventId(eventIds);        // -0.8
float variance = calculateEventIdVariance(eventIds); // 0.2
float range = calculateEventIdRange(eventIds);      // 1.0

// 5. Feature vector
float[] features = [
    // [0-17]: Original features
    35.0f, 15.0f, 0.43f, ...,  // timing features
    // [18-20]: Event ID features
    -0.8f,  // mean event ID
    0.2f,   // variance
    1.0f    // range
];

// 6. Send to server
detectionClient.learnAsync(server, "POLAR", features, callback);
```

---

## Next Steps

1. Update `PacketPatternDetector.java` with all changes above
2. Rebuild mod: `./gradlew build` 
3. Update server: `cp acdetect_server_v2.py /root/DragoniteClient/acdetect_server.py` 
4. Restart server: `python3 /root/DragoniteClient/acdetect_server.py` 
5. Test on different AC servers
6. Monitor event ID values in logs
7. Verify 21 features are being sent/received

Event ID monitoring will significantly improve classification accuracy! 🎯
