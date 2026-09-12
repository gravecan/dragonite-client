# Remove a false-positive blacklist

After a bad **invalid challenge proof** (fixed in client builds from 2026-05-30+), the auth server auto-blacklists HWID, license, IP, MC name, UUID, and Discord id.

## Discord bot (recommended)

```
/blacklist remove license AD995X54M5CNIN6DME7E
```

Or remove by HWID hash:

```
/blacklist remove hwid 6b0fe33b9fe71695a0fe5ca2baa2a28fbfd3909564f92c42ab80925f528554a1
```

The bot clears **cascade** keys for that person (see `blacklist-cascade.js`).

## Manual (VPS)

1. Stop auth-server (optional).
2. Edit `server/blacklist.json` and delete entries for the license, normalized HWID, IP, MC username, UUID, Discord id.
3. Restart auth-server or wait for file reload.

## While testing fixes

Set on the VPS `.env` (temporary only):

```
DISABLE_AUTO_BLACKLIST=true
```

This still logs crack webhooks but does not write new blacklist entries.
