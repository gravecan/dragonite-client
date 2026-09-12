package me.shedaniel.clothconfig2.injection.bridge;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URISyntaxException;

/**
 * Bootstrap bridge from the Java agent into Fabric's mod system.
 * Called by AgentMain after instrumentation is set up.
 */
public class DragoniteAgent {

    /**
     * Native entry — must NOT run {@code DragoniteFabricLoader} on this classloader
     * (DLL URLClassLoader, no MC classes). Hand off to the Knot-resident copy so
     * Mixin registration, entrypoint, and AuthGate all share one loader.
     */
    public static void nativeEntry(String jarPath) {
        if (jarPath == null || jarPath.isEmpty()) {
            return;
        }
        try {
            ClassLoader knot = resolveMcClassLoader();
            if (knot == null) {
                System.err.println("[Dragonite-Agent] No Minecraft classloader for bridge");
                return;
            }
            if (!tryAddURL(knot, new File(jarPath).toURI().toURL())) {
                System.err.println("[Dragonite-Agent] Could not add JAR to Minecraft classloader");
            }
            Class<?> bridge = Class.forName(
                    "me.shedaniel.clothconfig2.injection.bridge.DragoniteFabricLoader",
                    true, knot);
            ClassLoader tc = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(knot);
                bridge.getMethod("loadMod", String.class).invoke(null, jarPath);
            } finally {
                Thread.currentThread().setContextClassLoader(tc);
            }
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            System.err.println("[Dragonite-Agent] Native bridge failed: "
                    + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            cause.printStackTrace();
        }
    }

    private static ClassLoader resolveMcClassLoader() {
        String[] names = {
                "net.minecraft.class_310",
                "net.minecraft.client.MinecraftClient",
                "net.minecraft.client.main.Main",
        };
        try {
            ThreadGroup root = Thread.currentThread().getThreadGroup();
            while (root != null && root.getParent() != null) {
                root = root.getParent();
            }
            if (root != null) {
                Thread[] all = new Thread[Math.max(64, root.activeCount() * 4)];
                int n = root.enumerate(all, true);
                java.util.LinkedHashSet<ClassLoader> candidates = new java.util.LinkedHashSet<>();
                for (int i = 0; i < n; i++) {
                    Thread t = all[i];
                    if (t == null) continue;
                    for (ClassLoader cl = t.getContextClassLoader(); cl != null; cl = cl.getParent()) {
                        candidates.add(cl);
                    }
                }
                for (ClassLoader cl = ClassLoader.getSystemClassLoader(); cl != null; cl = cl.getParent()) {
                    candidates.add(cl);
                }
                for (ClassLoader cl : candidates) {
                    for (String name : names) {
                        try {
                            Class.forName(name, false, cl);
                            return cl;
                        } catch (ClassNotFoundException ignored) {
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean tryAddURL(ClassLoader cl, java.net.URL url) {
        try {
            java.nio.file.Path jarPath = java.nio.file.Path.of(url.toURI());
            Method getDelegateM = cl.getClass().getDeclaredMethod("getDelegate");
            getDelegateM.setAccessible(true);
            Object delegate = getDelegateM.invoke(cl);
            Method addCodeSource = delegate.getClass().getDeclaredMethod("addCodeSource", java.nio.file.Path.class);
            addCodeSource.setAccessible(true);
            addCodeSource.invoke(delegate, jarPath);
            return true;
        } catch (Throwable ignored) {
        }
        try {
            Method m = java.net.URLClassLoader.class.getDeclaredMethod("addURL", java.net.URL.class);
            m.setAccessible(true);
            m.invoke(cl, url);
            return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static void bootstrap() {
        String jarPath = resolveOwnJarPath();
        if (jarPath == null) {
            return;
        }
        DragoniteFabricLoader.loadMod(jarPath);
    }

    private static String resolveOwnJarPath() {
        try {
            File f = new File(DragoniteAgent.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
            if (f.isFile() && f.getName().endsWith(".jar")) {
                return f.getAbsolutePath();
            }
        } catch (URISyntaxException | RuntimeException ignored) {
        }
        return null;
    }
}
