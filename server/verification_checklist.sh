#!/bin/bash
# verification_checklist.sh
# Complete verification script for anticheat learning system

echo "================================================================="
echo "ANTICHEAT LEARNING SYSTEM - VERIFICATION CHECKLIST"
echo "================================================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Step 1: Check if server is running
echo -e "\n${YELLOW}[1/8] Checking if Flask server is running...${NC}"
if pgrep -f "acdetect_server.py" > /dev/null; then
    echo -e "${GREEN}✓ Server process found${NC}"
    ps aux | grep acdetect_server.py | grep -v grep
else
    echo -e "${RED}✗ Server not running!${NC}"
    echo "Start it with: python3 /root/DragoniteClient/acdetect_server.py"
    exit 1
fi

# Step 2: Check if port 5001 is listening
echo -e "\n${YELLOW}[2/8] Checking if port 5001 is listening...${NC}"
if netstat -tuln | grep ':5001' > /dev/null 2>&1 || ss -tuln | grep ':5001' > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Port 5001 is listening${NC}"
    netstat -tuln | grep ':5001' || ss -tuln | grep ':5001'
else
    echo -e "${RED}✗ Port 5001 not listening!${NC}"
    exit 1
fi

# Step 3: Test server endpoint with curl
echo -e "\n${YELLOW}[3/8] Testing /api/stats endpoint...${NC}"
STATS=$(curl -s http://localhost:5001/api/stats 2>&1)
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Server responding${NC}"
    echo "$STATS" | python3 -m json.tool 2>/dev/null || echo "$STATS"
else
    echo -e "${RED}✗ Server not responding${NC}"
    echo "$STATS"
fi

# Step 4: Check database exists
echo -e "\n${YELLOW}[4/8] Checking if database exists...${NC}"
DB_PATH="/root/DragoniteClient/acdetect.db"
if [ -f "$DB_PATH" ]; then
    echo -e "${GREEN}✓ Database found at $DB_PATH${NC}"
    ls -lh "$DB_PATH"
else
    echo -e "${RED}✗ Database not found!${NC}"
    echo "Expected at: $DB_PATH"
    exit 1
fi

# Step 5: Check database schema
echo -e "\n${YELLOW}[5/8] Checking database schema...${NC}"
TABLES=$(sqlite3 "$DB_PATH" ".tables" 2>&1)
if echo "$TABLES" | grep -q "samples"; then
    echo -e "${GREEN}✓ Tables exist${NC}"
    echo "Tables: $TABLES"
else
    echo -e "${RED}✗ samples table missing!${NC}"
    echo "Tables found: $TABLES"
fi

# Step 6: Count samples in database
echo -e "\n${YELLOW}[6/8] Counting samples in database...${NC}"
SAMPLE_COUNT=$(sqlite3 "$DB_PATH" "SELECT COUNT(*) FROM samples;" 2>&1)
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Sample count: $SAMPLE_COUNT${NC}"
    if [ "$SAMPLE_COUNT" -eq 0 ]; then
        echo -e "${YELLOW}⚠ No samples yet - this is expected if just started${NC}"
    fi
else
    echo -e "${RED}✗ Error querying database${NC}"
    echo "$SAMPLE_COUNT"
fi

# Step 7: Show recent samples (if any)
echo -e "\n${YELLOW}[7/8] Showing recent samples...${NC}"
RECENT=$(sqlite3 "$DB_PATH" "SELECT id, label, timestamp FROM samples ORDER BY timestamp DESC LIMIT 5;" 2>&1)
if [ $? -eq 0 ] && [ -n "$RECENT" ]; then
    echo -e "${GREEN}✓ Recent samples:${NC}"
    echo "$RECENT"
else
    echo -e "${YELLOW}⚠ No samples in database yet${NC}"
fi

# Step 8: Check server logs
echo -e "\n${YELLOW}[8/8] Checking server output (last 20 lines)...${NC}"
echo "If running in screen/tmux, attach to see live logs"
echo "If running as service, check journalctl or log file"
echo -e "${YELLOW}(This step requires manual check)${NC}"

echo -e "\n================================================================="
echo "VERIFICATION COMPLETE"
echo "================================================================="

# Summary
echo -e "\n${GREEN}NEXT STEPS:${NC}"
echo "1. Check Minecraft logs for [ACDetect] messages"
echo "2. Watch server output: tail -f /path/to/server.log"
echo "3. Monitor database: watch -n 5 'sqlite3 $DB_PATH \"SELECT COUNT(*) FROM samples;\"'"
echo "4. Test manually with: python3 test_learning.py"
