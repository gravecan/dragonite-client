# Dragonite production release checklist

Complete every step on the VPS before shipping a client build.

## 1. Rotate exposed secrets

If `.env` has ever been shared or committed, rotate `SERVER_SECRET`, `ADMIN_KEY`,
Discord credentials/webhooks, and any AC-detect encryption keys. Never paste
private keys, tokens, license keys, HWIDs, or full `.env` files into chat.

## 2. Deploy the auth server

Upload the new `auth-server.js` to `/root/dragonite-auth/auth-server.js`.
Do not overwrite `.env`, `licenses.json`, `blacklist.json`, or `data/`.

```bash
cd /root/dragonite-auth
npm install --production
node --check auth-server.js
pm2 restart dragonite-auth dragonite-bot --update-env
curl -s https://dragoniteclient.fun/v1/health
```

## 3. Required auth-hardening settings

Keep these values in `/root/dragonite-auth/.env`:

```dotenv
# Generated and retained only on the VPS. Never put this in a JAR.
AUTH_RESPONSE_ED25519_PRIVATE_PKCS8_B64=...
# The legacy client-held signing-key scheme must stay disabled.
REQUIRE_AUTH_PROOF=false
# Server restarts invalidate bearer sessions instead of restoring them from disk.
PERSIST_SESSIONS=false
```

The matching public key is embedded in the corresponding client JAR. If the
server signing key is rotated, build a client using its new public key before
deploying the server change.

After the first hardened deployment, archive the old `data/sessions.*` files
outside of the active data directory. They contain old bearer-session material
and are not read with `PERSIST_SESSIONS=false`.

## 4. Register the final release JAR hash

For automated releases, configure a separate `RELEASE_REGISTRATION_TOKEN` on
the VPS and build machine as described in `RELEASE_AUTOMATION.md`. The release
registry retains all approved release hashes and split-key records; never reuse
`ADMIN_KEY` as the release token. The release build registers the finalized
JAR hash automatically and verifies that the server stored its matching key ID
before distribution. Do not replace `EXPECTED_JAR_HASHES` to publish a normal
release; it is a legacy/static allowlist and would discard older manually
configured hashes.

For a new production server with no legacy hash, keep `ENFORCE_JAR_HASH=true`.
The server enters a fail-closed release provisioning lock until the first
automated build registers its hash; do not disable enforcement to bootstrap.

## 5. Smoke test

1. Use a clean Windows test profile with only the newly registered client JAR installed.
2. Confirm license login and HWID auto-login work.
3. Restart `dragonite-auth`; with the default `PERSIST_SESSIONS=false`, confirm
   the prior session does not survive.
4. Confirm a revoked or expired license receives a heartbeat rejection.
5. Confirm a modified successful auth response is rejected by the client.
6. Check PM2 logs contain no signing-key or release-registry errors.

## Post-release

- Keep obfuscator mapping files offline; never ship them.
- Keep the VPS signing private key, admin key, SMTP password, Discord token,
  and session data out of source control and chat.
