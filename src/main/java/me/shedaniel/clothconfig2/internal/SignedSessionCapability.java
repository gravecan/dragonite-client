package me.shedaniel.clothconfig2.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

/**
 * Immutable evidence from a successfully verified, server-signed auth response.
 *
 * <p>The Ed25519 signature is verified before this object is created. Its
 * material is subsequently included in the native session seal, binding that
 * seal to the server nonce, exact JAR hash, session token and expiry.</p>
 */
final class SignedSessionCapability {
    private final String material;
    private final byte[] sessionTokenDigest;
    private final long expiresAtMillis;
    private final String jarHash;

    private SignedSessionCapability(
            String material,
            byte[] sessionTokenDigest,
            long expiresAtMillis,
            String jarHash) {
        this.material = material;
        this.sessionTokenDigest = sessionTokenDigest;
        this.expiresAtMillis = expiresAtMillis;
        this.jarHash = jarHash;
    }

    static SignedSessionCapability create(
            String signatureB64,
            String sessionToken,
            String expiresAt,
            String requestNonce,
            String requestJarHash) {
        try {
            if (signatureB64 == null || signatureB64.isBlank()
                    || sessionToken == null || sessionToken.isBlank()
                    || expiresAt == null || expiresAt.isBlank()
                    || requestNonce == null || requestNonce.isBlank()
                    || requestJarHash == null
                    || !requestJarHash.matches("(?i)[0-9a-f]{64}")) {
                return null;
            }
            byte[] signature = Base64.getDecoder().decode(signatureB64);
            if (signature.length != 64) {
                return null;
            }
            long expiry = Instant.parse(expiresAt).toEpochMilli();
            if (expiry <= System.currentTimeMillis()) {
                return null;
            }
            String normalizedHash = requestJarHash.toLowerCase(Locale.ROOT);
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] tokenDigest = sha256.digest(
                    sessionToken.getBytes(StandardCharsets.UTF_8));
            String material = "dragonite-signed-capability-v1|"
                    + signatureB64 + "|" + requestNonce + "|"
                    + normalizedHash + "|" + expiresAt + "|"
                    + Base64.getEncoder().encodeToString(tokenDigest);
            return new SignedSessionCapability(
                    material, tokenDigest, expiry, normalizedHash);
        } catch (Exception invalid) {
            return null;
        }
    }

    boolean matches(String sessionToken, long sessionExpiryMillis, String currentJarHash) {
        if (sessionToken == null || sessionToken.isBlank()
                || currentJarHash == null || System.currentTimeMillis() >= expiresAtMillis
                || sessionExpiryMillis != expiresAtMillis
                || !jarHash.equals(currentJarHash.toLowerCase(Locale.ROOT))) {
            return false;
        }
        try {
            byte[] actual = MessageDigest.getInstance("SHA-256").digest(
                    sessionToken.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(sessionTokenDigest, actual);
        } catch (Exception invalid) {
            return false;
        }
    }

    String material() {
        return material;
    }
}
