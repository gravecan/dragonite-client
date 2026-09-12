package me.shedaniel.clothconfig2.internal;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.Base64;

public final class JarIntegrity {

    private static volatile Set<String> serverAllowedHashes = Collections.emptySet();
    private static volatile String cachedModSha256;
    private static volatile String cachedModFileSha256;

    private JarIntegrity() {}

    public static void setServerAllowedHashes(String[] hashes) {
        if (hashes == null || hashes.length == 0) {
            serverAllowedHashes = Collections.emptySet();
            return;
        }
        Set<String> set = new HashSet<>();
        for (String h : hashes) {
            String normalized = normalizeSha256(h);
            if (normalized != null) {
                set.add(normalized);
            }
        }
        serverAllowedHashes = Collections.unmodifiableSet(set);
    }

    public static boolean verifyBuildFingerprint() {
        if (!BuildFingerprint.isReleaseBuild()) {
            return true;
        }
        return releaseMetadataValid(
                BuildFingerprint.getDrmKeyId(),
                BuildFingerprint.getEmbeddedJarSha256(),
                BuildFingerprint.authHost(),
                BuildFingerprint.authSpkiSha256Hex(),
                BuildFingerprint.authResponseEd25519PublicSpkiB64());
    }

    public static boolean verifyEmbeddedJarSha256() {
        if (!BuildFingerprint.isReleaseBuild()) {
            return true;
        }
        String expected = normalizeSha256(BuildFingerprint.getEmbeddedJarSha256());
        String actual = normalizeSha256(computeModJarSha256());
        return embeddedHashMatches(expected, actual);
    }

    public static boolean verifyAgainstServerAllowlist() {
        String actual = computeModJarFileSha256();
        return allowsComputedHash(serverAllowedHashes, actual);
    }

    public static boolean passesStartupGate() {
        return verifyBuildFingerprint() && verifyEmbeddedJarSha256();
    }

    public static String computeModJarSha256() {
        if (cachedModSha256 != null) {
            return cachedModSha256;
        }
        try {
            String jarPath = resolveModJarPath();
            if (jarPath == null) {
                return null;
            }
            cachedModSha256 = computeClassDigest(Path.of(jarPath));
            return cachedModSha256;
        } catch (Exception e) {
            return null;
        }
    }

    public static String computeModJarFileSha256() {
        if (cachedModFileSha256 != null) {
            return cachedModFileSha256;
        }
        try {
            String jarPath = resolveModJarPath();
            if (jarPath == null) {
                return null;
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(Path.of(jarPath))) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (read > 0) {
                        md.update(buffer, 0, read);
                    }
                }
            }
            cachedModFileSha256 = toHex(md.digest());
            return cachedModFileSha256;
        } catch (Exception e) {
            return null;
        }
    }

    public static String getModJarPathOrNull() {
        return resolveModJarPath();
    }

    static String normalizeSha256(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.matches("[0-9a-f]{64}") ? normalized : null;
    }

    static boolean embeddedHashMatches(String expected, String actual) {
        return expected != null && actual != null && expected.equals(actual);
    }

    static boolean allowsComputedHash(Set<String> allowedHashes, String actual) {
        String normalized = normalizeSha256(actual);
        return normalized != null && allowedHashes != null && !allowedHashes.isEmpty()
                && allowedHashes.contains(normalized);
    }

    static boolean releaseMetadataValid(String drmKeyId, String embeddedHash, String authHost,
                                        String authSpki, String authResponseKey) {
        String normalizedHash = normalizeSha256(embeddedHash);
        String normalizedSpki = normalizeSha256(authSpki);
        if (drmKeyId == null || !drmKeyId.matches("build-[0-9]+")) {
            return false;
        }
        if (normalizedHash == null || normalizedSpki == null || authHost == null
                || authHost.isBlank() || authHost.contains("__") || authHost.contains("localhost")) {
            return false;
        }
        if (authResponseKey == null || authResponseKey.isBlank()) {
            return false;
        }
        try {
            return Base64.getDecoder().decode(authResponseKey).length >= 32;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static String computeClassDigest(Path jarPath) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        Set<String> seen = new HashSet<>();
        List<String> names = new ArrayList<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!seen.add(entry.getName())) {
                    throw new java.io.IOException("Duplicate JAR entry: " + entry.getName());
                }
                if (!entry.isDirectory() && entry.getName().endsWith(".class")
                        && !"me/shedaniel/clothconfig2/internal/BuildFingerprint.class".equals(entry.getName())) {
                    names.add(entry.getName());
                }
            }
            names.sort(String::compareTo);
            for (String name : names) {
                md.update(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                try (InputStream in = jar.getInputStream(jar.getJarEntry(name))) {
                    md.update(in.readAllBytes());
                }
            }
        }
        return toHex(md.digest());
    }

    private static String resolveModJarPath() {
        String fromCodeSource = resolveModJarPathFromCodeSource();
        if (fromCodeSource != null) {
            return fromCodeSource;
        }
        String fromLoader = resolveModJarPathFromClassLoader(JarIntegrity.class.getClassLoader());
        if (fromLoader != null) {
            return fromLoader;
        }
        try {
            var container = net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getModContainer("cloth-config");
            if (container.isPresent()) {
                var origin = container.get().getOrigin();
                if (origin.getKind() == net.fabricmc.loader.api.metadata.ModOrigin.Kind.PATH) {
                    for (Path p : origin.getPaths()) {
                        if (Files.isRegularFile(p) && p.toString().toLowerCase().endsWith(".jar")) {
                            return p.toAbsolutePath().toString();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String resolveModJarPathFromCodeSource() {
        try {
            var protectionDomain = JarIntegrity.class.getProtectionDomain();
            if (protectionDomain == null) {
                return null;
            }
            var codeSource = protectionDomain.getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                return null;
            }
            URI uri = codeSource.getLocation().toURI();
            Path path = Path.of(uri);
            if (Files.isRegularFile(path) && path.toString().toLowerCase().endsWith(".jar")) {
                return path.toAbsolutePath().toString();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String resolveModJarPathFromClassLoader(ClassLoader loader) {
        for (ClassLoader current = loader; current != null; current = current.getParent()) {
            if (!(current instanceof java.net.URLClassLoader urlLoader)) {
                continue;
            }
            for (java.net.URL url : urlLoader.getURLs()) {
                String jarPath = jarPathFromUrl(url);
                if (jarPath != null) {
                    return jarPath;
                }
            }
        }
        return null;
    }

    private static String jarPathFromUrl(java.net.URL url) {
        if (url == null) {
            return null;
        }
        try {
            Path path = Path.of(url.toURI());
            if (Files.isRegularFile(path) && path.toString().toLowerCase().endsWith(".jar")) {
                return path.toAbsolutePath().toString();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
