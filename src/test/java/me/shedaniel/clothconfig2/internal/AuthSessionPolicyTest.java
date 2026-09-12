package me.shedaniel.clothconfig2.internal;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuthSessionPolicyTest {
    private static final long NOW_MS = 1_750_000_000_000L;
    private static final long NOW_NS = 123_000_000L;

    @Test
    void acceptsSignedServerSessionWithFutureExpiry() {
        long expiry = NOW_MS + 60_000L;
        AuthSessionPolicy.Deadline result = AuthSessionPolicy.validate(
                "server-session-token", Instant.ofEpochMilli(expiry).toString(), NOW_MS, NOW_NS);

        assertNotNull(result);
        assertEquals(expiry, result.wallClockMillis());
        assertEquals(NOW_NS + 60_000_000_000L, result.monotonicNanos());
    }

    @Test
    void rejectsMissingMalformedOrExpiredSessionMaterial() {
        String future = Instant.ofEpochMilli(NOW_MS + 60_000L).toString();
        assertNull(AuthSessionPolicy.validate(null, future, NOW_MS, NOW_NS));
        assertNull(AuthSessionPolicy.validate("", future, NOW_MS, NOW_NS));
        assertNull(AuthSessionPolicy.validate("token", null, NOW_MS, NOW_NS));
        assertNull(AuthSessionPolicy.validate("token", "not-a-date", NOW_MS, NOW_NS));
        assertNull(AuthSessionPolicy.validate(
                "token", Instant.ofEpochMilli(NOW_MS).toString(), NOW_MS, NOW_NS));
    }

    @Test
    void validatesChallengeShape() {
        assertEquals(true, AuthSessionPolicy.validChallenge("a".repeat(64), NOW_MS));
        assertEquals(false, AuthSessionPolicy.validChallenge("replayed", NOW_MS));
        assertEquals(false, AuthSessionPolicy.validChallenge("a".repeat(64), 0L));
    }
}
