package me.shedaniel.clothconfig2.internal;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignedSessionCapabilityTest {
    private static final String HASH = "a".repeat(64);
    private static final String SIGNATURE =
            Base64.getEncoder().encodeToString(new byte[64]);

    @Test
    void bindsTokenExpiryAndExactJarHash() {
        long expiryMillis = System.currentTimeMillis() + 120_000L;
        String expiry = Instant.ofEpochMilli(expiryMillis).toString();
        SignedSessionCapability capability = SignedSessionCapability.create(
                SIGNATURE, "server-session", expiry, "request-nonce", HASH);

        assertNotNull(capability);
        assertTrue(capability.matches("server-session", expiryMillis, HASH));
        assertFalse(capability.matches("different-session", expiryMillis, HASH));
        assertFalse(capability.matches("server-session", expiryMillis + 1L, HASH));
        assertFalse(capability.matches(
                "server-session", expiryMillis, "b".repeat(64)));
    }

    @Test
    void rejectsIncompleteMalformedAndExpiredEvidence() {
        String future = Instant.ofEpochMilli(
                System.currentTimeMillis() + 120_000L).toString();
        assertNull(SignedSessionCapability.create(
                null, "token", future, "nonce", HASH));
        assertNull(SignedSessionCapability.create(
                "not-base64", "token", future, "nonce", HASH));
        assertNull(SignedSessionCapability.create(
                SIGNATURE, "token", future, "", HASH));
        assertNull(SignedSessionCapability.create(
                SIGNATURE, "token", future, "nonce", "bad"));
        assertNull(SignedSessionCapability.create(
                SIGNATURE, "token",
                Instant.ofEpochMilli(System.currentTimeMillis() - 1L).toString(),
                "nonce", HASH));
    }
}
