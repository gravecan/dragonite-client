package me.shedaniel.clothconfig2.injection;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

import me.shedaniel.clothconfig2.internal.PeInjectionResources;

/**
 * Native injection path for launchers that block the Java Attach API
 * (e.g. Lunar Client with -XX:+DisableAttachMechanism).
 *
 * Old working layout restored:
 *   1. Unique {@code %TEMP%/dragonite-native/session-<id>} (avoids locked DLL overwrite)
 *   2. Extract host tool + payload into that session dir
 *   3. Write {@code dragonite.cfg} next to the payload with this JAR path
 *   4. Launch host tool → CreateRemoteThread + LoadLibraryW
 *   5. Payload bootstraps via JNI URLClassLoader (no Attach / JVMTI)
 *
 * Classpath blobs still come from {@link PeInjectionResources} (shipped {@code .dat}
 * entries), not cleartext {@code /native/} paths.
 */
public class NativeInjector {

    private static Path tempDir;

    private NativeInjector() {}

    public static boolean inject(String pid) {
        try {
            Path baseDir = Paths.get(System.getProperty("java.io.tmpdir", "."), "dragonite-native");
            Files.createDirectories(baseDir);

            // Drop unlocked old sessions; locked ones (DLL still mapped) stay.
            try {
                Files.list(baseDir).filter(Files::isDirectory).forEach(d -> {
                    try {
                        Files.walk(d)
                                .sorted(Comparator.reverseOrder())
                                .forEach(p -> {
                                    try {
                                        Files.deleteIfExists(p);
                                    } catch (Exception ignored) {
                                    }
                                });
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception ignored) {
            }

            String sessionId = Long.toHexString(System.currentTimeMillis());
            tempDir = baseDir.resolve("session-" + sessionId);
            Files.createDirectories(tempDir);

            Path injectorExe = extractResource(
                    PeInjectionResources.injectorClasspath(),
                    PeInjectionResources.injectorExtractFileName());
            Path payloadDll = extractResource(
                    PeInjectionResources.payloadClasspath(),
                    PeInjectionResources.payloadExtractFileName());
            if (injectorExe == null || payloadDll == null) {
                return false;
            }

            String jarPath = resolveJarPath();
            if (jarPath == null) {
                return false;
            }

            // Shipped July payload only reads dragonite.cfg next to the DLL.
            Files.writeString(tempDir.resolve("dragonite.cfg"), jarPath, StandardCharsets.UTF_8);

            ProcessBuilder pb = new ProcessBuilder(
                    injectorExe.toAbsolutePath().toString(),
                    pid,
                    payloadDll.toAbsolutePath().toString());
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                while (br.readLine() != null) {
                    // swallow — do not echo extractor/injector paths into logs
                }
            }

            return proc.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isAvailable() {
        return NativeInjector.class.getResourceAsStream(
                PeInjectionResources.injectorClasspath()) != null
                && NativeInjector.class.getResourceAsStream(
                PeInjectionResources.payloadClasspath()) != null;
    }

    public static boolean isLunarProcess(String pid) {
        try {
            return ProcessHandle.of(Long.parseLong(pid)).map(ph -> {
                var info = ph.info();
                String cmd = info.command().orElse("").toLowerCase();
                String cmdLine = info.commandLine().orElse("").toLowerCase();
                return cmd.contains("lunarclient")
                        || cmdLine.contains("lunarclient")
                        || cmdLine.contains("com.moonsworth")
                        || cmdLine.contains("genesisclient");
            }).orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    private static Path extractResource(String resourcePath, String fileName) {
        try (InputStream in = NativeInjector.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            Path outFile = tempDir.resolve(fileName);
            Files.copy(in, outFile, StandardCopyOption.REPLACE_EXISTING);
            return outFile;
        } catch (IOException e) {
            return null;
        }
    }

    private static String resolveJarPath() {
        try {
            java.net.URL location = NativeInjector.class.getProtectionDomain().getCodeSource().getLocation();
            if (location != null) {
                File f = new File(location.toURI());
                if (f.isFile() && f.getName().endsWith(".jar")) {
                    return f.getAbsolutePath();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
