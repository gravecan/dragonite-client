# Security roadmap vs expert guidance

Status map for the Fabric client hardening plan.

## Done in codebase

| Recommendation | Status |
|----------------|--------|
| Heartbeat HWID revalidation | Server + client |
| Challenge proof on `/v1/auth` | Server + client |
| Correlated trust (not one boolean) | `SecurityVault` triplet invariant |
| Scattered gates | `GuardRuntime`, `IntegrityProbe` |
| Join revalidation | `JoinIntegrityGuard` |
| JAR hash on auth + allowlist in response | `JarIntegrity` + server `allowedJarHashes` |
| SPKI pinning (auth only) | `AuthTls` via `-Ddragonite.auth.spki=` |
| Environment: agents, JDWP args, JDWP ports, tool classes | `EnvironmentGuard` |
| Obfuscator `legendaryFabric` preset | Obfuscator |

## Next (highest ROI, needs more work)

| Item | Effort | Notes |
|------|--------|-------|
| **SPKI hash baked in release** | Small | CI: `openssl s_client … \| openssl dgst -sha256` → obfuscate into client or JVM arg |
| **Register JAR hash on VPS** | Small | `EXPECTED_JAR_HASHES=816af78c…` for legendaryFabric build |
| **Split-key strings E2E** | Medium | Wire `KeyHalfFetcher` + `embeddedKeyHalfA` into decryptor bytecode |
| **Native HKDF(sessionToken)** | Large | `native(T)→K` for string decrypt; IKM rotated per CI build |
| **Ed25519 in native** | Large | Per-tier or per-license keys |
| **Heartbeat HMAC(timestamp/60)** | Medium | Server verifies rolling window |
| **CI hash upload** | Small | POST hash after Gradle + obfuscator |
| **defineHiddenClass for vault** | Medium | Isolate from Mixin targets |

## SPKI setup (one-time)

```bash
openssl s_client -connect dragoniteclient.fun:443 -servername dragoniteclient.fun </dev/null 2>/dev/null \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary \
  | xxd -p -c 32
```

Launch Minecraft with:

`-Ddragonite.auth.spki=<hex>`

(Optional backup cert) `-Ddragonite.auth.spki.backup=<hex>`

## Production gate

Release is ready when:

1. VPS secrets rotated  
2. Auth server v1.4 deployed  
3. JAR hash in `EXPECTED_JAR_HASHES`  
4. SPKI pins set for production launcher  
5. One full login → join → modules test on clean PC  
