#!/usr/bin/env bash
# Run on VPS: bash scripts/vps-diagnose-auth.sh  (from /root/dragonite-auth)
set -euo pipefail
cd "$(dirname "$0")/.." 2>/dev/null || cd /root/dragonite-auth

echo "=== Required modules next to auth-server.js ==="
for f in auth-proof-hmac.js auth-proof-policy.js notification-security.js release-registry.js; do
  if [[ -f "$f" ]]; then echo "  OK  $f"; else echo "  MISSING  $f"; fi
done

echo ""
echo "=== Last PM2 stderr ==="
pm2 logs dragonite-auth --lines 15 --err --nostream 2>/dev/null || true

echo ""
echo "=== Direct node start (first fatal error) ==="
set -a
[[ -f .env ]] && source .env
set +a
export NODE_ENV="${NODE_ENV:-production}"
timeout 3 node auth-server.js 2>&1 | head -25 || true

echo ""
echo "=== Fix checklist ==="
echo "1) Upload missing .js files from repo server/ (see VPS_UPLOAD_NOW.md)"
echo "2) If FATAL NATIVE_IKM_HEX: add 64-hex line to .env from build machine native/master_ikm.hex"
echo "3) pm2 restart dragonite-auth --update-env && curl -sS http://127.0.0.1:8000/v1/health"
