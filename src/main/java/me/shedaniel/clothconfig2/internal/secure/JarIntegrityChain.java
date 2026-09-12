package me.shedaniel.clothconfig2.internal.secure;

import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import me.shedaniel.clothconfig2.internal.ClientLog;
import me.shedaniel.clothconfig2.internal.JarIntegrity;

public class JarIntegrityChain {
    private static String EXPECTED_CHAIN_HASH = "to-be-patched";
    private static String currentChainHash = null;

    public static String computeStructuralHash() {
        if (currentChainHash != null) return currentChainHash;
        try {
            String jarPath = JarIntegrity.getModJarPathOrNull();
            if (jarPath == null || !jarPath.endsWith(".jar")) {
                return "dev-env";
            }

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (JarFile jar = new JarFile(jarPath)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".class")) {
                        // Structural chain: hash the class names and lengths
                        md.update(entry.getName().getBytes("UTF-8"));
                        md.update(longToBytes(entry.getSize()));
                    }
                }
            }
            
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            currentChainHash = sb.toString();
            return currentChainHash;
        } catch (Exception e) {
            ClientLog.err("JarIntegrityChain", "Failed to compute structural hash");
            return "error";
        }
    }

    public static boolean verifyChain() {
        if (EXPECTED_CHAIN_HASH.equals("to-be-patched")) {
            return true; // dev env or pre-obf
        }
        return computeStructuralHash().equals(EXPECTED_CHAIN_HASH);
    }
    
    private static byte[] longToBytes(long x) {
        return new byte[]{
            (byte) (x >> 56),
            (byte) (x >> 48),
            (byte) (x >> 40),
            (byte) (x >> 32),
            (byte) (x >> 24),
            (byte) (x >> 16),
            (byte) (x >> 8),
            (byte) x
        };
    }
}
