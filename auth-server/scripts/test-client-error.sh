#!/bin/bash
# Test /v1/client-error + Discord webhook from the VPS.
set -euo pipefail
BASE="${1:-http://127.0.0.1:8000}"
BODY='{"errorCode":"test_ping","errorMessage":"manual VPS test","mcUsername":"Test","mcUuid":"00000000-0000-0000-0000-000000000001","osVersion":"linux","windowsName":"root","pcName":"vps"}'

echo "=== Unsigned (needs mcUuid or CLIENT_ERROR_ALLOW_UNSIGNED=true) ==="
curl -s -X POST "$BASE/v1/client-error" \
  -H "Content-Type: application/json" \
  -d "$BODY"
echo ""

echo "=== Signed (always works in production) ==="
SIG=$(node -e "
const crypto=require('crypto');
const body=process.argv[1];
const mc='00000000-0000-0000-0000-000000000001';
const key=crypto.createHash('sha256').update(mc+'|dragonite-error').digest();
console.log(crypto.createHmac('sha256',key).update(body).digest('base64'));
" "$BODY")

curl -s -X POST "$BASE/v1/client-error" \
  -H "Content-Type: application/json" \
  -H "X-Dragonite-Signature: $SIG" \
  -d "$BODY"
echo ""
