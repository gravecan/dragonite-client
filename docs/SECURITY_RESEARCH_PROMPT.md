# Claude / security research prompt (Dragonite)

Copy-paste this when asking an external model for Java client protection advice:

---

You are a JVM reverse-engineering and software-protection expert. I maintain a **Fabric 1.21 Minecraft client** (Java 21) with:

- Custom ASM obfuscator (string encryption, indy, anti-debug, integrity witnesses, optional native `jawt_md.dll` DRM)
- Node.js auth server (HWID bind, session tokens, challenge proof, heartbeat HWID re-check)
- Client gates: `SecurityVault` (correlated int tags, not one boolean), `GuardRuntime`, `IntegrityProbe` in combat/render paths, `JoinIntegrityGuard` (revalidate on world join), `EnvironmentGuard` (agents/JDWP)

**Threat model:** attackers use Recaf, JADX/Vineflower, fake local auth servers, native DLL stubs, and patch `authenticated=true`.

Answer with **actionable Java/Fabric-specific** guidance:

1. What checks should run in **native** vs Java, and how to bind session tokens to native without storing secrets in the JAR?
2. Best practices for **split-key string decryption** (half in JAR, half on server after auth) — key derivation, rotation, anti-replay.
3. How to make **boolean-patch bypasses** expensive without full VMProtect (correlated state, opaque predicates, control-flow ideas that survive Fabric verifier).
4. **Certificate / SPKI pinning** patterns for HTTPS auth in a modded client.
5. Obfuscator transformer ordering pitfalls for **Fabric + Mixin** (what breaks stack maps).
6. Server-side session design: heartbeat, join revalidation, JAR hash allowlist — what Prestige-style clients miss.
7. Red flags in our architecture and top 5 highest-ROI changes for a small team.

Assume we will **not** ship server secrets in the client. Assume determined reversers; goal is **weeks** of effort, not impossible.

---

## Follow-up questions (checklist)

- [ ] Should decryptors call native for AES key material?
- [ ] Is `defineHiddenClass` virtualization worth it on Fabric?
- [ ] How to detect Recaf/Bytecode Viewer at runtime without false positives?
- [ ] Ed25519 challenge-response: client key in native vs per-license keys?
- [ ] How to rotate `EXPECTED_JAR_HASHES` per release in CI?
- [ ] GDPR/ethics of HWID — minimum viable binding fields?
