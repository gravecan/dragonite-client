# VPS — do this now (copy/paste)

## 1) On your PC — upload these files to `/root/dragonite-auth/`

**Auth server (upload the whole set — `auth-server.js` alone will crash with `Cannot find module './auth-proof-hmac'`):**

- `auth-server.js`
- `auth-proof-hmac.js`
- `auth-proof-policy.js`
- `notification-security.js`
- `release-registry.js`
- `auth-response-signing.js`
- `session-security.js`
- `release-provisioning.js`
- `key-access-policy.js`
- `session-store.js`
- `blacklist-cascade.js` (if present on disk)
- `discord-bot.js` (updated)
- `package.json`
- `setup-vps-env.sh`
- **`public/index.html` + `public/style.css`** (homepage — without these, `dragoniteclient.fun` is a white page)

**Do NOT upload** React `src/` — Node cannot run it. Full shop site = build `Dragonite Website` → upload `dist/` only.

## 2) On VPS — one-time env + install

```bash
cd /root/dragonite-auth

# Webhook for login alerts (rotate in Discord if this was ever leaked)
export DISCORD_WEBHOOK='PASTE_YOUR_WEBHOOK_URL_HERE'
bash setup-vps-env.sh

nano .env
```

In nano, set **`DISCORD_TOKEN=`** to your bot token (same bot as `dragonite-bot`).

Save, then:

```bash
npm install
pm2 restart dragonite-auth dragonite-bot --update-env
pm2 logs dragonite-auth --lines 20
```

You want: **`Admin key: Configured`** and **`Webhook alerts: Configured`**.

If `pm2` shows **errored**, check:

```bash
pm2 logs dragonite-auth --lines 30 --err
```

| Log | Fix |
|-----|-----|
| `Cannot find module './auth-proof-hmac'` | Upload `auth-proof-hmac.js` + `auth-proof-policy.js` from repo `server/` |
| `NATIVE_IKM_HEX ... required` | Add `NATIVE_IKM_HEX=<64 hex from native/master_ikm.hex>` to `.env` |

## 3) Test

```bash
curl -s http://127.0.0.1:8000/v1/challenge
curl -s https://assets-delivery.site/v1/health
```

## 4) Create a license in Discord

`/license create` → **Monthly** or **Lifetime Normal** → copy key → give to user → they enter it in the client once.

## 5) Save your ADMIN_KEY

After `setup-vps-env.sh`, it prints `ADMIN_KEY=...` — save it in a password manager.
