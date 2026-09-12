package me.shedaniel.clothconfig2.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

/**
 * Minimal JNI bridge used to verify native build and release packaging.
 *
 * <p>The native side exposes only version and deterministic health-check
 * functions. It does not inspect or modify JVM, process, or operating-system
 * state.</p>
 */
public final class NativeBridge {
    static final int HEALTH_CHALLENGE = 0x13579BDF;
    static final int HEALTH_MASK = 0x5A17C3E1;
    private static final String RESOURCE_PATH = "/native/dragonite-bridge.dll";
    private static final String DIALECT_RESOURCE =
            "/native/dragonite-vm2-dialect.txt";

    private static volatile boolean loaded;
    private static volatile boolean healthy;
    private static volatile String status = "not-loaded";
    private static volatile String version = "unavailable";

    private NativeBridge() {
    }

    private static native String nativeVersion();

    private static native int nativeHealthCheck(int challenge);

    private static native long nativeDialectFingerprint();

    private static native boolean nativeVm2SelfTest();

    private static native long nativeExecuteVm2(
            int[] code, long[] locals, int maxStack, int profile, int binding,
            long payloadTag);

    public static synchronized boolean load() {
        if (loaded) {
            return healthy;
        }

        try {
            version = nativeVersion();
            loaded = true;
            int response = nativeHealthCheck(HEALTH_CHALLENGE);
            String expectedDialect = readDialectFingerprint();
            String actualDialect = String.format(
                    "%016x", nativeDialectFingerprint());
            healthy = response == expectedHealthResponse(HEALTH_CHALLENGE)
                    && version != null
                    && !version.isBlank()
                    && expectedDialect.equals(actualDialect)
                    && nativeVm2SelfTest();
            status = healthy ? "healthy" : "health-check-failed";
            return healthy;
        } catch (UnsatisfiedLinkError | IOException ignored) {
        }

        Path dllPath = null;
        try (InputStream input = NativeBridge.class.getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                status = "resource-missing";
                return false;
            }

            TempCleanup.purgeOrphans();

            // Unique per-process path: a fixed cache.dll stays locked on Windows
            // after System.load and overwriting it throws FileSystemException.
            Path base = Path.of(System.getProperty("java.io.tmpdir", "."), "jdk-s");
            Files.createDirectories(base);
            String unique = "cache-" + ProcessHandle.current().pid() + "-"
                    + Long.toUnsignedString(System.nanoTime(), 36) + ".dll";
            dllPath = base.resolve(unique);
            try (OutputStream output = Files.newOutputStream(dllPath)) {
                input.transferTo(output);
            }
            try {
                dllPath.toFile().deleteOnExit();
            } catch (Throwable ignored) {
            }

            System.load(dllPath.toAbsolutePath().toString());
            loaded = true;
            version = nativeVersion();
            int response = nativeHealthCheck(HEALTH_CHALLENGE);
            String expectedDialect = readDialectFingerprint();
            String actualDialect = String.format(
                    "%016x", nativeDialectFingerprint());
            healthy = response == expectedHealthResponse(HEALTH_CHALLENGE)
                    && version != null
                    && !version.isBlank()
                    && expectedDialect.equals(actualDialect)
                    && nativeVm2SelfTest();
            status = healthy ? "healthy" : "health-check-failed";
            return healthy;
        } catch (IOException | UnsatisfiedLinkError exception) {
            status = "load-failed:" + exception.getClass().getSimpleName();
            return false;
        } catch (RuntimeException exception) {
            status = "health-check-error:" + exception.getClass().getSimpleName();
            return false;
        }
    }

    /**
     * Fail-closed entry used only by the experimental VM2 transformer.
     * There is deliberately no Java interpreter fallback.
     */
    public static long executeVm2(
            int[] code, long[] locals, int maxStack, int profile, int binding,
            long payloadTag) {
        if (!load() || !healthy) {
            throw new SecurityException("Native VM2 interpreter unavailable: " + status);
        }
        return nativeExecuteVm2(
                code, locals, maxStack, profile, binding, payloadTag);
    }

    private static String readDialectFingerprint() throws IOException {
        try (InputStream input =
                     NativeBridge.class.getResourceAsStream(DIALECT_RESOURCE)) {
            if (input == null) {
                throw new IOException("VM2 dialect fingerprint missing");
            }
            String value = new String(
                    input.readAllBytes(), StandardCharsets.US_ASCII).trim();
            if (!value.matches("[0-9a-fA-F]{16}")) {
                throw new IOException("VM2 dialect fingerprint malformed");
            }
            return value.toLowerCase(java.util.Locale.ROOT);
        }
    }

    static int expectedHealthResponse(int challenge) {
        return challenge ^ HEALTH_MASK;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static boolean isHealthy() {
        return healthy;
    }

    public static String getStatus() {
        return status;
    }

    public static String getVersion() {
        return version;
    }
}
