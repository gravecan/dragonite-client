package me.shedaniel.clothconfig2.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class JarIntegrityPolicyTest {

    @TempDir
    Path tempDir;

    @Test
    void sha256NormalizationIsStrict() {
        String upper = "A".repeat(64);
        assertEquals(upper.toLowerCase(), JarIntegrity.normalizeSha256(upper));
        assertNull(JarIntegrity.normalizeSha256("short"));
        assertNull(JarIntegrity.normalizeSha256("g".repeat(64)));
        assertNull(JarIntegrity.normalizeSha256(null));
    }

    @Test
    void embeddedHashMustMatchExactly() {
        String hash = "a".repeat(64);
        assertTrue(JarIntegrity.embeddedHashMatches(hash, hash));
        assertFalse(JarIntegrity.embeddedHashMatches(hash, "b".repeat(64)));
        assertFalse(JarIntegrity.embeddedHashMatches(null, hash));
    }

    @Test
    void serverAllowlistFailsClosed() {
        String hash = "a".repeat(64);
        assertTrue(JarIntegrity.allowsComputedHash(Set.of(hash), hash.toUpperCase()));
        assertFalse(JarIntegrity.allowsComputedHash(Set.of(), hash));
        assertFalse(JarIntegrity.allowsComputedHash(Set.of(hash), "b".repeat(64)));
        assertFalse(JarIntegrity.allowsComputedHash(Set.of(hash), null));
    }

    @Test
    void releaseMetadataRequiresAllRequiredValues() {
        String hash = "a".repeat(64);
        String key = "MCowBQYDK2VwAyEAXHwcSx4aiZr0mYVGKW3ufSHNxNGI7fWokp1edJ2oI+Y=";
        assertTrue(JarIntegrity.releaseMetadataValid(
                "build-123", hash, "https://auth.example", "b".repeat(64), key));
        assertFalse(JarIntegrity.releaseMetadataValid(
                "__OBF_DRM_KEY_ID__", hash, "https://auth.example", "b".repeat(64), key));
        assertFalse(JarIntegrity.releaseMetadataValid(
                "build-123", null, "https://auth.example", "b".repeat(64), key));
        assertFalse(JarIntegrity.releaseMetadataValid(
                "build-123", hash, "https://auth.example", "b".repeat(64), "bad"));
    }

    @Test
    void releaseBuildDoesNotFallBackToEmbeddedJarHash() {
        String embedded = "f".repeat(64);
        assertEquals("", ConfigLoader.resolveJarDigestHexForAuth(null, embedded, true));
        assertEquals("", ConfigLoader.resolveJarDigestHexForAuth("", embedded, true));
    }

    @Test
    void devBuildMayUseEmbeddedJarHashWhenFileHashMissing() {
        String embedded = "f".repeat(64);
        assertEquals(embedded, ConfigLoader.resolveJarDigestHexForAuth(null, embedded, false));
    }

    @Test
    void classDigestChangesWhenAClassChanges() throws Exception {
        Path first = tempDir.resolve("first.jar");
        Path second = tempDir.resolve("second.jar");
        writeJar(first, new byte[]{1, 2, 3});
        writeJar(second, new byte[]{1, 2, 4});
        assertNotEquals(JarIntegrity.computeClassDigest(first), JarIntegrity.computeClassDigest(second));
    }

    @Test
    void modJarPathResolvesFromUrlClassLoaderWhenCodeSourceMissing() throws Exception {
        Path jar = tempDir.resolve("mod.jar");
        writeJar(jar, new byte[]{5, 6, 7});
        try (java.net.URLClassLoader loader = new java.net.URLClassLoader(
                new java.net.URL[] { jar.toUri().toURL() },
                ClassLoader.getSystemClassLoader().getParent())) {
            java.lang.reflect.Method m = JarIntegrity.class.getDeclaredMethod(
                    "resolveModJarPathFromClassLoader", ClassLoader.class);
            m.setAccessible(true);
            String path = (String) m.invoke(null, loader);
            assertEquals(jar.toAbsolutePath().toString(), path);
        }
    }

    private static void writeJar(Path path, byte[] classBytes) throws Exception {
        try (JarOutputStream out = new JarOutputStream(java.nio.file.Files.newOutputStream(path))) {
            out.putNextEntry(new JarEntry("a/Example.class"));
            out.write(classBytes);
            out.closeEntry();
            out.putNextEntry(new JarEntry("me/shedaniel/clothconfig2/internal/BuildFingerprint.class"));
            out.write(new byte[]{9});
            out.closeEntry();
        }
    }
}
