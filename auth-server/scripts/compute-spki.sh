#!/usr/bin/env bash
# SPKI pin for -Ddragonite.auth.spki= (64-char hex SHA-256 of cert public key DER)
set -euo pipefail

HOST="${1:-assets-delivery.site}"
PORT="${2:-443}"

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

openssl s_client -connect "${HOST}:${PORT}" -servername "$HOST" </dev/null 2>/dev/null \
  | openssl x509 -outform DER > "$TMP"

SPKI_HEX="$(openssl x509 -in "$TMP" -pubkey -noout \
  | openssl pkey -pubin -outform DER \
  | openssl dgst -sha256 -hex | awk '{print $2}')"

echo "SPKI SHA-256 (hex, 64 chars): $SPKI_HEX"
echo ""
echo "Launcher JVM arg:"
echo "  -Ddragonite.auth.spki=$SPKI_HEX"
