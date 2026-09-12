package me.shedaniel.clothconfig2.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/** Redacted standalone-auth diagnostics. Never writes to disk. */
public final class AuthorizationDiagnostics {

    private AuthorizationDiagnostics() {
    }

    public static void recordStage(String stage, String reasonCode) {
        // no disk
    }

    public static void recordProofAttempt(String payload, String proof, String jarHash,
                                          String hwid, byte[] rawIkm) {
        // no disk
    }

    public static String logPath() {
        return "(disabled)";
    }

    static String safeToken(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]", "_");
        return normalized.substring(0, Math.min(normalized.length(), 64));
    }

    static String fingerprintUtf8(String value) {
        return fingerprintBytes(value != null
                ? value.getBytes(StandardCharsets.UTF_8) : new byte[0]);
    }

    private static String fingerprintProof(String proof) {
        if (proof == null || proof.isBlank()) {
            return fingerprintBytes(new byte[0]);
        }
        int separator = proof.indexOf(':');
        String encoded = separator >= 0 ? proof.substring(separator + 1) : proof;
        try {
            return fingerprintBytes(Base64.getDecoder().decode(encoded));
        } catch (IllegalArgumentException malformed) {
            return fingerprintUtf8(proof);
        }
    }

    private static String fingerprintBytes(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value != null ? value : new byte[0]);
            StringBuilder out = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                out.append(String.format("%02x", digest[i]));
            }
            return out.toString();
        } catch (Exception unavailable) {
            return "unavailable";
        }
    }
}
