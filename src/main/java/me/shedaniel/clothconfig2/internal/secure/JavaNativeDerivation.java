package me.shedaniel.clothconfig2.internal.secure;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import me.shedaniel.clothconfig2.internal.BuildFingerprint;

/**
 * Pure-Java port of {@code dragonite-auth.dll} HKDF helpers ({@code KeyDerive.cpp}).
 */
public final class JavaNativeDerivation {

    private static final String INFO_STRING_KEY = "cloth-string-key";
    private static final String SALT_STRING_MASTER = "cloth-string-master";

    private JavaNativeDerivation() {}

    public static byte[] deriveSessionKey(String sessionToken) {
        if (sessionToken == null) {
            return null;
        }
        byte[] ikm = buildIkm();
        if (ikm == null) {
            return null;
        }
        try {
            byte[] tok = sessionToken.getBytes(StandardCharsets.UTF_8);
            return hkdfSha256(ikm, tok, INFO_STRING_KEY.getBytes(StandardCharsets.UTF_8), 32);
        } finally {
            Arrays.fill(ikm, (byte) 0);
        }
    }

    public static int nativeGameplayMix(int slot) {
        byte[] ikm = buildIkm();
        if (ikm == null) {
            return 0;
        }
        try {
            String info = "cloth-gpx-" + slot;
            byte[] out = hkdfSha256(ikm, null, info.getBytes(StandardCharsets.UTF_8), 4);
            if (out == null || out.length < 4) {
                return 0;
            }
            int v = ((out[0] & 0xFF) << 24) | ((out[1] & 0xFF) << 16) | ((out[2] & 0xFF) << 8) | (out[3] & 0xFF);
            return v == 0 ? 0x51C0FFEE : v;
        } finally {
            Arrays.fill(ikm, (byte) 0);
        }
    }

    public static byte[] nativeStringKeyMixOrNull() {
        if (!BuildFingerprint.isReleaseBuild()) {
            return null;
        }
        byte[] full = deriveSessionKey(SALT_STRING_MASTER);
        if (full == null || full.length < 16) {
            return null;
        }
        byte[] mix = Arrays.copyOf(full, 16);
        Arrays.fill(full, (byte) 0);
        return mix;
    }

    public static boolean nativeKatVector(int vector) {
        if (!BuildFingerprint.isReleaseBuild()) {
            return true;
        }
        String input = BuildFingerprint.katInput(vector);
        String expectedHex = BuildFingerprint.katExpectedHex(vector);
        if (input == null || expectedHex == null
                || input.startsWith("__OBF_") || expectedHex.startsWith("__OBF_")) {
            return false;
        }
        byte[] expected = hexToBytes(expectedHex);
        if (expected == null || expected.length != 32) {
            return false;
        }
        byte[] actual = deriveSessionKey(input);
        if (actual == null) {
            return false;
        }
        boolean ok = constantTimeEquals(actual, expected);
        Arrays.fill(actual, (byte) 0);
        return ok;
    }

    public static boolean nativeKatOk() {
        if (!BuildFingerprint.isReleaseBuild()) {
            return true;
        }
        return evaluateKatGate(
                nativeKatVector(0) ? 1 : 0,
                nativeKatVector(1) ? 1 : 0,
                nativeKatVector(2) ? 1 : 0);
    }

    static boolean evaluateKatGate(int vector0, int vector1, int vector2) {
        if (vector0 == 0) {
            return false;
        }
        if (vector1 == 0) {
            return false;
        }
        if (vector2 == 0) {
            return false;
        }
        return true;
    }

    public static boolean javaBackendReady() {
        return evaluateBackendReady(
                BuildFingerprint.isReleaseBuild(),
                buildIkm() != null,
                nativeKatOk());
    }

    static boolean evaluateBackendReady(boolean releaseBuild, boolean ikmOk,
                                        boolean katOk) {
        if (!releaseBuild) {
            return true;
        }
        return ikmOk && katOk;
    }

    private static byte[] buildIkm() {
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

    public static byte[] deriveAuthProofKey(String license) {
        if (license == null || license.isBlank()) {
            return null;
        }
        String norm = normalizeLicenseKey(license);
        if (norm.isEmpty()) {
            return null;
        }
        byte[] ikm = buildIkm();
        if (ikm == null) {
            return null;
        }
        try {
            return hkdfSha256(ikm, norm.getBytes(StandardCharsets.UTF_8),
                    "cloth-auth-proof-v2".getBytes(StandardCharsets.UTF_8), 32);
        } finally {
            Arrays.fill(ikm, (byte) 0);
        }
    }

    static String normalizeLicenseKey(String license) {
        if (license == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < license.length(); i++) {
            char c = license.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.toString();
    }

    public static byte[] deriveSavedLicenseKey(String hwid) {
        if (hwid == null || hwid.isBlank()) {
            return null;
        }
        byte[] ikm = buildIkm();
        if (ikm == null) {
            return null;
        }
        try {
            byte[] salt = hwid.getBytes(StandardCharsets.UTF_8);
            return hkdfSha256(ikm, salt, "cloth-saved-license-v1".getBytes(StandardCharsets.UTF_8), 32);
        } finally {
            Arrays.fill(ikm, (byte) 0);
        }
    }

    /** Matches native {@code HkdfSha256}: salt bytes are the HKDF "salt" parameter (session token when deriving keys). */
    private static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int outLen) {
        try {
            byte[] prk;
            if (salt != null && salt.length > 0) {
                prk = hmacSha256(ikm, salt);
            } else {
                prk = hmacSha256(ikm, new byte[32]);
            }
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

    private static byte[] hmacSha256(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || (hex.length() & 1) != 0) {
            return null;
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                return null;
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
