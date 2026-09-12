package me.shedaniel.clothconfig2.internal;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Binds a live server-issued session to release-specific VM2 computation.
 *
 * <p>In ordinary development builds the two primitive functions below execute
 * as Java. The hardened-experimental release requires the obfuscator to replace
 * both functions with calls to the native VM2 interpreter. Consequently, the
 * release cannot establish or retain an authenticated session when the native
 * bridge is missing, corrupt, or incompatible.</p>
 */
public final class NativeSessionGate {
    static final int PURPOSE_SESSION = 0x53455353;
    static final int PURPOSE_AUTH_LIVE = 0x41555448;
    static final int PURPOSE_PROTECTED_ACTION = 0x4143544E;

    private static volatile long tokenWordA;
    private static volatile long tokenWordB;
    private static volatile long sealedEpoch;
    private static volatile long sessionSeal;
    private static volatile boolean armed;

    private NativeSessionGate() {
    }

    static synchronized boolean arm(
            String sessionToken,
            String hwid,
            String signedCapabilityEvidence,
            long authEpoch) {
        disarm();
        if (sessionToken == null || sessionToken.isBlank()
                || hwid == null || hwid.isBlank() || authEpoch == 0L) {
            return false;
        }
        if (signedCapabilityEvidence == null
                || signedCapabilityEvidence.isBlank()) {
            return false;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("dragonite-native-session-v1".getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) 0);
            digest.update(sessionToken.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(hwid.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(signedCapabilityEvidence.getBytes(StandardCharsets.UTF_8));
            ByteBuffer words = ByteBuffer.wrap(digest.digest());
            long wordA = words.getLong();
            long wordB = words.getLong();
            long seal = deriveSeal(wordA, wordB, authEpoch, PURPOSE_SESSION);
            if (seal == 0L
                    || !verifySeal(seal, wordA, wordB, authEpoch, PURPOSE_SESSION)) {
                return false;
            }
            tokenWordA = wordA;
            tokenWordB = wordB;
            sealedEpoch = authEpoch;
            sessionSeal = seal;
            armed = true;
            return holds(authEpoch, PURPOSE_AUTH_LIVE);
        } catch (Exception | LinkageError failure) {
            disarm();
            return false;
        }
    }

    static boolean holds(long authEpoch, int purpose) {
        if (!armed || authEpoch == 0L || authEpoch != sealedEpoch) {
            return false;
        }
        try {
            long wordA = tokenWordA;
            long wordB = tokenWordB;
            if (!verifySeal(sessionSeal, wordA, wordB, authEpoch, PURPOSE_SESSION)) {
                return false;
            }
            long purposeSeal = deriveSeal(wordA, wordB, authEpoch, purpose);
            return purposeSeal != 0L
                    && verifySeal(purposeSeal, wordA, wordB, authEpoch, purpose);
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    static synchronized void disarm() {
        armed = false;
        tokenWordA = 0L;
        tokenWordB = 0L;
        sealedEpoch = 0L;
        sessionSeal = 0L;
    }

    /**
     * Mandatory native-VM2 method in hardened-experimental releases.
     */
    static long deriveSeal(long wordA, long wordB, long authEpoch, int purpose) {
        long mixed = wordA ^ Long.rotateLeft(wordB, 17) ^ authEpoch;
        mixed += (long) purpose * 0x9E3779B97F4A7C15L;
        mixed ^= mixed >>> 29;
        mixed *= 0xD6E8FEB86659FD93L;
        mixed ^= Long.rotateLeft(mixed, 23);
        mixed += Long.rotateRight(authEpoch ^ wordB, purpose & 63);
        mixed ^= mixed >>> 31;
        return mixed == 0L ? 0x6A09E667F3BCC909L : mixed;
    }

    /**
     * Independently recomputes the seal. This is a second mandatory native-VM2
     * boundary rather than a Java comparison around one native return value.
     */
    static boolean verifySeal(
            long candidate, long wordA, long wordB, long authEpoch, int purpose) {
        long mixed = wordA ^ Long.rotateLeft(wordB, 17) ^ authEpoch;
        mixed += (long) purpose * 0x9E3779B97F4A7C15L;
        mixed ^= mixed >>> 29;
        mixed *= 0xD6E8FEB86659FD93L;
        mixed ^= Long.rotateLeft(mixed, 23);
        mixed += Long.rotateRight(authEpoch ^ wordB, purpose & 63);
        mixed ^= mixed >>> 31;
        if (mixed == 0L) {
            mixed = 0x6A09E667F3BCC909L;
        }
        return (candidate ^ mixed) == 0L;
    }
}
