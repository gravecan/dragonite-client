#!/usr/bin/env bash
# Run on VPS after uploading auth-server.js
set -euo pipefail
cd "$(dirname "$0")/.."
export NODE_ENV=production
export DRAGONITE_PRODUCTION=true
if [[ ! -f .env ]]; then
  echo "Missing .env — copy .env.example and fill secrets (use rotate-secrets.sh)."
  exit 1
fi
npm install --production
if command -v pm2 >/dev/null 2>&1; then
  pm2 restart dragonite-auth || pm2 start auth-server.js --name dragonite-auth
else
  echo "pm2 not found — start manually: NODE_ENV=production node auth-server.js"
fi
sleep 2
curl -sf "http://127.0.0.1:${PORT:-8000}/v1/health" || curl -sf "https://${DOMAIN:-assets-delivery.site}/v1/health" || true
echo ""
echo "Deploy complete. Verify Tier 1 checklist in RELEASE_CHECKLIST.md"
