# License system — simple explanation

## Two different “expiry” things

| Setting | What it means |
|--------|----------------|
| **License `expiresAt`** | When the **key stops working**. `null` = **lifetime** (never expires). Monthly = ~30 days from creation. |
| **`SESSION_DURATION_HOURS`** (in `.env`, default 24) | How long the client stays **logged in** after one login before it must talk to the server again. Not the same as lifetime. |

## License types (Discord bot)

Use **`/license create`** in your Discord server (admin only):

| Type | Key starts with | Expires |
|------|-----------------|--------|
| Lifetime Normal | `AA` | Never |
| Lifetime Premium | `AB` | Never |
| Monthly | `AC` | 30 days |
| Custom | `AD` | You set e.g. `90d`, `1y` |

Licenses are stored in **`licenses.json`** on the VPS (same folder as auth server). Bot and auth server share this file.

## Create licenses

**Discord (easiest):** `/license create` → pick type → bot gives you a key.

**HTTP API (optional):**

```bash
curl -X POST http://127.0.0.1:8000/v1/admin/license \
  -H "Content-Type: application/json" \
  -d '{
    "adminKey": "YOUR_ADMIN_KEY",
    "licenseKey": "ACXXXXXXXXXXXXXXXXXXXXXX",
    "licenseType": "monthly",
    "durationDays": 30
  }'
```

Lifetime:

```json
{
  "adminKey": "YOUR_ADMIN_KEY",
  "licenseKey": "AAXXXXXXXXXXXXXXXXXXXXXX",
  "licenseType": "lifetime_normal",
  "lifetime": true
}
```

## What you need in `.env`

| Variable | Required for |
|----------|----------------|
| `SERVER_SECRET` | Session/crypto (keep stable; don’t change randomly) |
| `ADMIN_KEY` | Admin API + optional bot |
| `DISCORD_WEBHOOK` | Login alerts in Discord channel |
| `DISCORD_TOKEN` | License bot (`dragonite-bot`) only |
