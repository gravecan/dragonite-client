package me.shedaniel.clothconfig2.internal;

import me.shedaniel.clothconfig2.internal.secure.JavaAuthSigner;
import me.shedaniel.clothconfig2.internal.secure.JavaHwidGatherer;
import me.shedaniel.clothconfig2.internal.secure.JavaNativeDerivation;
import me.shedaniel.clothconfig2.internal.secure.JavaWatchdog;

public class ConfigLoader {

    /** Release KAT + IKM gate for the pure-Java crypto backend. */
    public static boolean javaBackendReady() {
        if (!BuildFingerprint.isReleaseBuild()) {
            return true;
        }
        return JavaNativeDerivation.javaBackendReady();
    }

    public static boolean verifyDllHash() {
        return javaBackendReady();
    }

    /** @deprecated name kept for obfuscated call sites; no JNI DLL is loaded. */
    public static boolean isNativeLoaded() {
        return javaBackendReady();
    }

    public static boolean isPoisoned() {
        if (!BuildFingerprint.isReleaseBuild()) {
            return false;
        }
        if (!javaBackendReady()) {
            return true;
        }
        return !JavaWatchdog.isOk();
    }

    public static boolean nativeKatVector(int vector) {
        return JavaNativeDerivation.nativeKatVector(vector);
    }

    public static boolean nativeKatOk() {
        return JavaNativeDerivation.nativeKatOk();
    }

    public static byte[] nativeStringKeyMixOrNull() {
        return JavaNativeDerivation.nativeStringKeyMixOrNull();
    }

    public static String getHwid() {
        if (!BuildFingerprint.isReleaseBuild()) {
            return "dev-hwid";
        }
        return JavaHwidGatherer.getHwid();
    }

    public static String getMachineGuid() {
        if (!BuildFingerprint.isReleaseBuild()) {
            return "dev-guid";
        }
        return JavaHwidGatherer.getMachineGuid();
    }

    public static boolean verifyDllFile() {
        return javaBackendReady();
    }

    public static byte[] deriveSessionKey(String sessionToken) {
        if (!javaBackendReady() || sessionToken == null) {
            return null;
        }
        return JavaNativeDerivation.deriveSessionKey(sessionToken);
    }

    public static String resolveJarDigestHexForAuth() {
        return resolveJarDigestHexForAuth(
                JarIntegrity.computeModJarFileSha256(),
                BuildFingerprint.getEmbeddedJarSha256(),
                BuildFingerprint.isReleaseBuild());
    }

    static String resolveJarDigestHexForAuth(String fileHash, String embeddedHash, boolean releaseBuild) {
        String jarSha = fileHash;
        if (jarSha == null || jarSha.isEmpty()) {
            if (!releaseBuild
                    && embeddedHash != null
                    && !embeddedHash.startsWith("__OBF_")
                    && !embeddedHash.isBlank()) {
                jarSha = embeddedHash;
            }
        }
        return jarSha != null ? jarSha.trim().toLowerCase() : "";
    }

    public static String signAuthProofNative(long timestamp, String hwid, String license, String jarPath) {
        String jarHex = resolveJarDigestHexForAuth();
        if (jarHex == null) {
            jarHex = "";
        }
        String payload = timestamp + "|" + hwid + "|" + license + "|" + jarHex;
        return JavaAuthSigner.signPayload(payload, license);
    }

    public static int nativeGameplayMix(int slot) {
        if (!BuildFingerprint.isReleaseBuild()) {
            return 0xA1B2C3D4;
        }
        if (!JavaNativeDerivation.javaBackendReady()) {
            return 0;
        }
        return JavaNativeDerivation.nativeGameplayMix(slot);
    }

    public static String buildAuthProofPayload(String timestamp, String hwid, String license) {
        String jarHex = resolveJarDigestHexForAuth();
        if (jarHex == null) {
            jarHex = "";
        }
        return timestamp + "|" + hwid + "|" + license + "|" + jarHex;
    }

    public static String ecdsaPublicSpkiFingerprint() {
        return JavaAuthSigner.getSpkiFingerprint();
    }

    public static String signChallengeNative(String nonce, long timestamp, String hwid, String license) {
        return JavaAuthSigner.signNonceBoundPayload(
                buildChallengeProofPayload(nonce, String.valueOf(timestamp), hwid, license), license);
    }

    public static String buildChallengeProofPayload(String nonce, String timestamp, String hwid, String license) {
        String jarHex = resolveJarDigestHexForAuth();
        return safe(nonce) + "|" + safe(timestamp) + "|" + safe(hwid) + "|"
                + safe(license) + "|" + safe(jarHex);
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
