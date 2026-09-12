#!/usr/bin/env bash
# Compute JAR SHA-256 and print how to add it to VPS .env (no separate admin API).
set -euo pipefail

JAR="${1:-}"
ENV_FILE="${2:-/opt/dragonite-auth/.env}"

if [[ -z "$JAR" || ! -f "$JAR" ]]; then
  echo "Usage: $0 /path/to/cloth-config-*-legendaryFabric.jar [/opt/dragonite-auth/.env]"
  exit 1
fi

HASH="$(sha256sum "$JAR" | awk '{print tolower($1)}')"
echo "SHA-256: $HASH"
echo ""

if [[ -f "$ENV_FILE" ]]; then
  if grep -q '^EXPECTED_JAR_HASHES=' "$ENV_FILE"; then
    CURRENT="$(grep '^EXPECTED_JAR_HASHES=' "$ENV_FILE" | cut -d= -f2- | tr -d '"' | tr -d "'")"
    if echo "$CURRENT" | tr ',' '\n' | grep -qx "$HASH"; then
      echo "Hash already listed in $ENV_FILE"
    else
      if [[ -z "$CURRENT" ]]; then
        NEW="$HASH"
      else
        NEW="${CURRENT},${HASH}"
      fi
      echo "Update $ENV_FILE:"
      echo "  EXPECTED_JAR_HASHES=$NEW"
      echo ""
      echo "Then: pm2 restart dragonite-auth"
    fi
  else
    echo "Add to $ENV_FILE:"
    echo "  EXPECTED_JAR_HASHES=$HASH"
    echo ""
    echo "Then: pm2 restart dragonite-auth"
  fi
else
  echo "Add to your auth server .env:"
  echo "  EXPECTED_JAR_HASHES=$HASH"
  echo ""
  echo "Then restart the auth process (e.g. pm2 restart dragonite-auth)"
fi
