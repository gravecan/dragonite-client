# One-shot commands (`/root/dragonite-auth`)

## SPKI pin (launcher JVM arg)

```bash
HOST=dragoniteclient.fun; openssl s_client -connect ${HOST}:443 -servername ${HOST} </dev/null 2>/dev/null | openssl x509 -pubkey -noout | openssl pkey -pubin -outform DER | openssl dgst -sha256 -hex | awk '{print "-Ddragonite.auth.spki="$2}'
```

## Remove bogus JAR allowlist (if you set YOUR64CHARHASH)

```bash
cd /root/dragonite-auth && sed -i '/^EXPECTED_JAR_HASHES=/d' .env && pm2 restart dragonite-auth --update-env
```

JAR SHA allowlist is **disabled** — each obfuscation injects a random build tag only.
