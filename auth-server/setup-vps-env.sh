#!/bin/bash
# Run on VPS: cd /root/dragonite-auth && bash setup-vps-env.sh
# Creates /root/dragonite-auth/.env with random ADMIN_KEY + SERVER_SECRET

set -e
cd "$(dirname "$0")"

ENV_FILE=".env"
if [ -f "$ENV_FILE" ]; then
  echo "Backing up existing .env -> .env.backup.$(date +%s)"
  cp "$ENV_FILE" ".env.backup.$(date +%s)"
fi

ADMIN_KEY="${ADMIN_KEY:-$(openssl rand -hex 24)}"
SERVER_SECRET="${SERVER_SECRET:-$(openssl rand -hex 32)}"
PORT="${PORT:-8000}"
DOMAIN="${DOMAIN:-assets-delivery.site}"

# Pass webhook: DISCORD_WEBHOOK='https://discord.com/api/webhooks/...' bash setup-vps-env.sh
WEBHOOK="${DISCORD_WEBHOOK:-}"

cat > "$ENV_FILE" <<EOF
PORT=$PORT
DOMAIN=$DOMAIN
TRUSTED_PROXY_IPS=127.0.0.1,::1
SERVER_SECRET=$SERVER_SECRET
ADMIN_KEY=$ADMIN_KEY
NODE_ENV=production
DRAGONITE_PRODUCTION=true
SESSION_DURATION_HOURS=24
PERSIST_SESSIONS=false
ENFORCE_JAR_HASH=true
EXPECTED_JAR_HASHES=
RELEASE_REGISTRATION_TOKEN=
ECDSA_PUBLIC_KEY_SPKI_B64=
DISCORD_CLIENT_ID=
DISCORD_CLIENT_SECRET=
DISCORD_REDIRECT_URI=https://${DOMAIN}/v1/auth/discord/callback
DISCORD_WEBHOOK=$WEBHOOK
DISCORD_TOKEN=
AUTH_SERVER_URL=http://127.0.0.1:${PORT}
EOF

chmod 600 "$ENV_FILE"
echo "Wrote $ENV_FILE"
echo "ADMIN_KEY=$ADMIN_KEY"
echo "(Save ADMIN_KEY somewhere safe — used for /v1/admin/* and bot)"
echo ""
echo "Next:"
echo "  Before starting production: set ECDSA_PUBLIC_KEY_SPKI_B64 and EXPECTED_JAR_HASHES in .env"
echo "  1) nano .env  — paste DISCORD_TOKEN for dragonite-bot (if not set)"
echo "  2) npm install"
echo "  3) pm2 restart dragonite-auth dragonite-bot --update-env"
