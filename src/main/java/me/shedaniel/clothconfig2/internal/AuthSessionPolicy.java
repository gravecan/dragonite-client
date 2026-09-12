package me.shedaniel.clothconfig2.internal;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

/** Validates server-issued session material before it can become authorization state. */
final class AuthSessionPolicy {
    private static final int MAX_TOKEN_LENGTH = 4096;

    private AuthSessionPolicy() {
    }

    static Deadline validate(String token, String expiresAt, long nowMillis, long nowNanos) {
        if (!validToken(token)) {
            return null;
        }
        long expiry = parseExpiry(expiresAt);
        if (expiry <= nowMillis) {
            return null;
        }
        long durationMillis = expiry - nowMillis;
        long durationNanos;
        try {
            durationNanos = TimeUnit.MILLISECONDS.toNanos(durationMillis);
            return new Deadline(expiry, Math.addExact(nowNanos, durationNanos));
        } catch (ArithmeticException overflow) {
            return null;
        }
    }

    static boolean validChallenge(String nonce, long timestamp) {
        if (nonce == null || !nonce.matches("[0-9a-fA-F]{64}")) {
            return false;
        }
        return timestamp > 0L;
    }

    private static boolean validToken(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            return false;
        }
        return token.codePoints().noneMatch(Character::isISOControl);
    }

    private static long parseExpiry(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Instant.parse(value.trim()).toEpochMilli();
        } catch (Exception ignored) {
            try {
                return OffsetDateTime.parse(value.trim()).toInstant().toEpochMilli();
            } catch (Exception invalid) {
                return 0L;
            }
        }
    }

    record Deadline(long wallClockMillis, long monotonicNanos) {
    }
}
