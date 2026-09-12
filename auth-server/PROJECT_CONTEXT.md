# Dragonite — project context (read this first in new chats)

This file documents **where everything lives**, **how auth/licensing works**, and **VPS ops** so assistants don’t have to rediscover paths each session.

---

## Repository roots (Windows dev machine)

Workspace is usually:

`C:\Users\Daniel\Downloads\Dragonite Projects (1)\`

| Component | Path |
|-----------|------|
| **Fabric client (1.21)** | `...\My projekts\Dragonite Client\Dragonite Client\Dragonite Client\Dragonite Client\1.21\Dragonite Client\Dragonite\` |
| **Auth server + Discord bot** | `...\My projekts\Obsufucator\Dragonite Obsufucator\Dragonite Obsufucator\Dragonite Obsufucator\server\` |
| **Obfuscator** | `...\My projekts\Obsufucator\Dragonite Obsufucator\Dragonite Obsufucator\Dragonite Obsufucator\` (parent of `server/`) |
| **Client security deploy notes** | `...\1.21\Dragonite Client\Dragonite\SECURITY_DEPLOY.md` |

**Main client auth classes:** `com.dragonite.client.*` — `SessionHandler`, `NetworkHandler`, `AuthConfig`, `AuthGate`, `DialogHandler`.

**Client auth URL:** default `https://dragoniteclient.fun` via `AuthConfig` (override: env `DRAGONITE_AUTH_URL` or `-Ddragonite.auth.url`).

---

## VPS production layout

| Item | Value |
|------|--------|
| **Server folder** | `/root/dragonite-auth/` |
| **Auth process (PM2)** | `dragonite-auth` → `auth-server.js` |
| **License bot (PM2)** | `dragonite-bot` → `discord-bot.js` |
| **Port** | `8000` (behind nginx/HTTPS on `dragoniteclient.fun`) |
| **Secrets** | `/root/dragonite-auth/.env` (never commit) |
| **License database** | `/root/dragonite-auth/licenses.json` |
| **Other data** | `blacklist.json`, `event_ids.json`, `key_halves.json`, `hwids.txt` (optional allowlist) |

**Quick health check:**

```bash
curl -s http://127.0.0.1:8000/v1/challenge
curl -s https://dragoniteclient.fun/v1/challenge
pm2 status
```

**Restart after deploy:**

```bash
cd /root/dragonite-auth && npm install && pm2 restart dragonite-auth dragonite-bot --update-env
```

**Do not run two auth processes** (e.g. both `auth` and `dragonite-auth`) — port 8000 `EADDRINUSE`.

---

## Server files (this folder)

| File | Role |
|------|------|
| `auth-server.js` | Express API: HWID auth, sessions, heartbeat, admin, Discord OAuth, webhooks |
| `discord-bot.js` | Slash commands: `/license create|list|info|reset|delete`, blacklist, stats |
| `package.json` | `express`, `dotenv`, `discord.js` |
| `setup-vps-env.sh` | Generates `.env` with random `ADMIN_KEY` / `SERVER_SECRET` |
| `.env.example` | Template only |
| `LICENSE_GUIDE.md` | Lifetime vs monthly vs session duration |
| `VPS_UPLOAD_NOW.md` | Short deploy checklist |
| `PROJECT_CONTEXT.md` | This file |

---

## `.env` variables

| Variable | Purpose |
|----------|---------|
| `PORT` | `8000` |
| `DOMAIN` | `dragoniteclient.fun` |
| `SERVER_SECRET` | Session/crypto — **keep stable** across restarts |
| `ADMIN_KEY` | Admin API + bot config |
| `SESSION_DURATION_HOURS` | How long a **login session** lasts (default 24), not license lifetime |
| `DISCORD_WEBHOOK` | Login/crack alerts → Discord **channel** |
| `DISCORD_TOKEN` | Bot token for **slash commands** |
| `DISCORD_CLIENT_ID` | OAuth app id (`1482797317194252288`) |
| `DISCORD_CLIENT_SECRET` | OAuth secret |
| `DISCORD_REDIRECT_URI` | `https://dragoniteclient.fun/v1/auth/discord/callback` |
| `EXPECTED_JAR_HASHES` | Optional comma-separated SHA-256 of release JARs |
| `AUTH_SERVER_URL` | Bot uses `http://127.0.0.1:8000` |

`auth-server.js` loads `.env` via `require('dotenv').config()` at top.

---

## License key format (important)

**Client GUI** (`DialogHandler.java`):

- Placeholder: `XXXX-XXXX-XXXX-XXXX-XXXX`
- **Exactly 20** alphanumeric characters (dashes added automatically in UI)
- User can paste with or without dashes; server normalizes on lookup

**Bot prefixes** (`discord-bot.js`):

| Prefix | Type |
|--------|------|
| `AA` | Lifetime Normal |
| `AB` | Lifetime Premium |
| `AC` | Monthly (30 days) |
| `AD` | Custom duration |

**Storage:** keys in `licenses.json` are **20 chars, no dashes**. Discord embed shows **dashed** display via `formatLicenseKeyDisplay()`.

**Lifetime:** `expiresAt: null` in JSON. **Monthly:** `expiresAt` ~30 days from creation.

**Do not generate 24-char keys** — old bug; client truncates at 20.

**Discord bot** reloads `licenses.json` from disk on each `/license` / `/blacklist` command (auth server writes file on login/logout).

**License list** shows MC username, Discord, IP after first login. `/blacklist add` uses a **type** dropdown (IP, MC username, UUID, Discord id, etc.).

---

## Auth API (main endpoints)

| Method | Path | Notes |
|--------|------|------|
| GET | `/v1/challenge` | Returns `nonce` |
| POST | `/v1/auth/hwid` | HWID-first check; auto-login if bound |
| POST | `/v1/auth` | License + nonce login |
| POST | `/v1/heartbeat` | Keep session alive |
| POST | `/v1/verify` | JAR integrity |
| POST | `/v1/admin/license` | Create license (`adminKey`) |
| GET | `/v1/admin/licenses` | List licenses |

---

## Discord: two systems

1. **`dragonite-bot`** — create/manage keys (`/license ...`). Needs `DISCORD_TOKEN`. Writes `licenses.json`.
2. **`DISCORD_WEBHOOK`** — security alerts from `auth-server.js` (login, crack, blacklist, sharing, etc.). Create webhook **in the alert channel** (user channel id was `1509174119025868950`).

**Discord Developer Portal → Redirects:** use  
`https://dragoniteclient.fun/v1/auth/discord/callback`  
(not raw VPS IP).

---

## Client security (summary)

- `AuthGate` — disables modules without valid session (`HudConfigInit`).
- `AuthConfig` — HTTPS required in production unless `-Ddragonite.auth.allowInsecure=true`.
- Telemetry webhook in client is **opt-in** (`DRAGONITE_TELEMETRY=1`).
- No hardcoded VPS IPs in client source (as of security pass).

---

## Common issues

| Symptom | Fix |
|---------|-----|
| `EADDRINUSE :8000` | Only one PM2 app for auth; `pm2 delete auth` if duplicate |
| `DISCORD_TOKEN missing` | Set in `.env`, `pm2 restart dragonite-bot --update-env` |
| `Cannot find module auth-server.js` | Upload `auth-server.js` to `/root/dragonite-auth/` |
| License “too short” in client | Key must be 20 chars; recreate via bot after fix |
| Webhook no messages | Wrong webhook URL / wrong channel; check `pm2 logs dragonite-auth` for `Webhook alerts: Configured` |
| `nano` fails (no TTY) | Use `sed` or `setup-vps-env.sh` |

---

## Deploy checklist (minimal)

1. Upload `auth-server.js`, `discord-bot.js`, `package.json` to VPS.
2. Ensure `.env` complete (`setup-vps-env.sh` or manual).
3. `npm install` → `pm2 restart dragonite-auth dragonite-bot --update-env`.
4. Test `curl` challenge + `/license create` in Discord.
5. Test login in Minecraft with dashed key.

---

## Related docs

- `LICENSE_GUIDE.md` — lifetime vs monthly vs session hours
- `VPS_UPLOAD_NOW.md` — copy/paste VPS steps
- Client `SECURITY_DEPLOY.md` — JAR build, obfuscator, native DLL

---

*Last updated: 2026-05 — license format 20 chars; dotenv; AuthGate on client.*
