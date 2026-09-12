package me.shedaniel.clothconfig2.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Cleans old unique temp dumps. Leaves {@code dragonite-native/session-*} alone —
 * {@link me.shedaniel.clothconfig2.injection.NativeInjector} manages those itself
 * (locked DLLs must not be force-deleted mid-game).
 */
public final class TempCleanup {
    /** Legacy one-shot folders from older builds (unique names every run). */
    private static final String[] ORPHAN_PREFIXES = {
            "cloth-native",
            "dragonite-bridge-",
            "dragonite-agent",
            "jna-",
            "mod-agent",
            "jdk-s",
    };

    private TempCleanup() {}

    public static void purgeOrphans() {
        Path tmp;
        try {
            tmp = Path.of(System.getProperty("java.io.tmpdir", "."));
        } catch (Exception e) {
            return;
        }
        if (!Files.isDirectory(tmp)) {
            return;
        }

        try (Stream<Path> stream = Files.list(tmp)) {
            stream.forEach(path -> {
                String name = path.getFileName().toString();
                // Never wipe dragonite-native — active inject sessions live there.
                if ("dragonite-native".equals(name)) {
                    return;
                }
                for (String prefix : ORPHAN_PREFIXES) {
                    if (name.startsWith(prefix) || name.equals(prefix)) {
                        deleteTreeQuiet(path);
                        break;
                    }
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static void deleteTreeQuiet(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }
}
