#!/usr/bin/env bash
# Generates new production secrets — paste into /opt/dragonite-auth/.env then: pm2 restart dragonite-auth
set -euo pipefail
echo "=== Dragonite secret rotation ($(date -Is)) ==="
echo ""
echo "SERVER_SECRET=$(openssl rand -hex 32)"
echo "ADMIN_KEY=$(openssl rand -hex 24)"
echo "ACDETECT_ENCRYPTION_KEY=$(openssl rand -hex 32)"
echo ""
echo "DISCORD_WEBHOOK=<create new webhook in Discord channel settings>"
echo ""
echo "After updating .env on the VPS:"
echo "  pm2 restart dragonite-auth"
echo "  curl -s https://dragoniteclient.fun/v1/health"
