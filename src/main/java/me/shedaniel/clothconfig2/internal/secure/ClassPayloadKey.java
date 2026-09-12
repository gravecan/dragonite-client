package me.shedaniel.clothconfig2.internal.secure;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import me.shedaniel.clothconfig2.internal.BuildFingerprint;
import me.shedaniel.clothconfig2.internal.KeyHalfFetcher;

/**
 * Runtime class-payload key: HKDF(mixed IKM, salt=server keyHalfB, info=v1).
 * Matches obfuscator {@code ClassPayloadKeyDerive}. No complete key exists in the JAR.
 */
public final class ClassPayloadKey {
    public static final String INFO = "dragonite-class-payload-v1";
    public static final int KEY_BYTES = 32;

    private static volatile byte[] cached;

    private ClassPayloadKey() {
    }

    /** Derive or return cached 32-byte key. Null if auth material is unavailable. */
    public static byte[] getOrDerive() {
        byte[] hit = cached;
        if (hit != null) {
            return hit.clone();
        }
        String keyId = BuildFingerprint.getDrmKeyId();
        if (keyId == null || keyId.isBlank()) {
            return null;
        }
        byte[] halfB = KeyHalfFetcher.getServerHalf(keyId);
        if (halfB == null || halfB.length < 16) {
            return null;
        }
        byte[] ikm = mixedIkmOrNull();
        if (ikm == null) {
            return null;
        }
        try {
            byte[] key = hkdfSha256(ikm, halfB, INFO.getBytes(StandardCharsets.UTF_8), KEY_BYTES);
            if (key != null) {
                cached = key;
                return key.clone();
            }
            return null;
        } finally {
            Arrays.fill(ikm, (byte) 0);
        }
    }

    public static void clear() {
        byte[] previous = cached;
        cached = null;
        if (previous != null) {
            Arrays.fill(previous, (byte) 0);
        }
    }

    private static byte[] mixedIkmOrNull() {
        byte[] raw = BuildFingerprint.nativeIkmOrNull();
        if (raw == null || raw.length < 32) {
            return null;
        }
        byte[] ikm = Arrays.copyOf(raw, 32);
        for (int i = 0; i < 32; i++) {
            ikm[i] ^= (byte) (0x5A ^ i);
        }
        return ikm;
    }

    private static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int outLen) {
        try {
            byte[] prk = hmac(ikm, salt);
            byte[] out = new byte[outLen];
            byte[] t = new byte[32];
            int offset = 0;
            byte counter = 1;
            while (offset < outLen) {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(prk, "HmacSHA256"));
                if (offset > 0) {
                    mac.update(t);
                }
                if (info != null && info.length > 0) {
                    mac.update(info);
                }
                mac.update(counter);
                t = mac.doFinal();
                int copy = Math.min(32, outLen - offset);
                System.arraycopy(t, 0, out, offset, copy);
                offset += copy;
                counter++;
            }
            Arrays.fill(prk, (byte) 0);
            Arrays.fill(t, (byte) 0);
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }
}
