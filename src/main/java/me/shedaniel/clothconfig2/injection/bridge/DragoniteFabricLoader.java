package me.shedaniel.clothconfig2.injection.bridge;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Core Fabric injection — injects a mod JAR into Fabric's live KnotClassLoader
 * and invokes the client entrypoint at runtime.
 *
 * This runs INSIDE the Minecraft JVM after the agent has been loaded.
 * All output goes to Minecraft's console/log.
 */
public class DragoniteFabricLoader {

    /**
     * Loading auth/entrypoint through Knot after JAR was added there — same loader
     * Mixin uses, no AuthGate split.
     */
    private static final String ENTRYPOINT_CLASS = "me.shedaniel.clothconfig2.impl.ClothConfigInitializer";

    /** Live Instrumentation mirrored via {@code System} properties from agent init. */
    private static volatile Object liveInstrumentation;

    /**
     * Child-first (parent-last) classloader.
     * Loads classes from our JAR first, only delegates to parent (Lunar) for
     * classes not found in the JAR (like net.minecraft.*, net.fabricmc.*, etc.)
     * This prevents Ichor from transforming our bytecode.
     */
    static class ChildFirstClassLoader extends java.net.URLClassLoader {
        private static final Object UNSAFE;
        private static final java.lang.reflect.Method UNSAFE_DEFINE;
        private final java.security.ProtectionDomain protectionDomain;

        static {
            Object u = null;
            java.lang.reflect.Method m = null;
            try {
                java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                u = f.get(null);
                m = sun.misc.Unsafe.class.getMethod("defineClass",
                        String.class, byte[].class, int.class, int.class,
                        ClassLoader.class, java.security.ProtectionDomain.class);
            } catch (Throwable ignored) {}
            UNSAFE = u;
            UNSAFE_DEFINE = m;
        }

        ChildFirstClassLoader(java.net.URL[] urls, ClassLoader parent) {
            super(urls, parent);
            java.security.CodeSource codeSource = new java.security.CodeSource(
                    urls.length > 0 ? urls[0] : null,
                    (java.security.cert.Certificate[]) null);
            protectionDomain = new java.security.ProtectionDomain(codeSource, null, this, null);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                // 1. Already loaded?
                Class<?> c = findLoadedClass(name);
                if (c != null) {
                    if (resolve) resolveClass(c);
                    return c;
                }

                // 2. Java platform classes always go to parent
                if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("sun.") || name.startsWith("jdk.")) {
                    return super.loadClass(name, resolve);
                }

                // 3. Try loading from OUR JAR first (child-first)
                //    Use Unsafe.defineClass to skip bytecode verification
                //    (obfuscator can produce invalid stackmap frames)
                try {
                    c = findClassNoVerify(name);
                    if (c != null) {
                        if (resolve) resolveClass(c);
                        return c;
                    }
                } catch (ClassNotFoundException ignored) {
                    // Not in our JAR — delegate to parent (Lunar)
                }

                // 4. Delegate to parent for MC/Fabric/other classes
                return super.loadClass(name, resolve);
            }
        }

        /**
         * Load class bytes from our JAR and define without verification.
         * Uses Unsafe.defineClass to bypass the JVM's StackMapTable verifier.
         */
        private Class<?> findClassNoVerify(String name) throws ClassNotFoundException {
            String path = name.replace('.', '/') + ".class";
            java.net.URL res = findResource(path);
            if (res == null) throw new ClassNotFoundException(name);

            try (java.io.InputStream is = res.openStream()) {
                byte[] bytes = is.readAllBytes();

                // Try Unsafe.defineClass (skips verification)
                if (UNSAFE != null && UNSAFE_DEFINE != null) {
                    try {
                        return (Class<?>) UNSAFE_DEFINE.invoke(UNSAFE,
                                name, bytes, 0, bytes.length, this, protectionDomain);
                    } catch (Throwable t) {
                        // Unsafe failed, fall through to normal defineClass
                        System.err.println("[Dragonite-Agent] Unsafe.defineClass failed for " + name + ": " + t.getMessage());
                    }
                }

                // Fallback: normal defineClass (may trigger VerifyError)
                return defineClass(name, bytes, 0, bytes.length);
            } catch (java.io.IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }


    public static boolean loadMod(String modJarPath) {
        try {
            File modJar = new File(modJarPath);
            if (!modJar.exists()) {
                return false;
            }
            me.shedaniel.clothconfig2.impl.InjectedJarLocator.setJarPath(modJarPath);

            java.net.URL jarUrl = modJar.toURI().toURL();

            // Step 1: Find Lunar/Fabric classloader that can see Minecraft
            ClassLoader mcClassLoader = resolveMcClassLoader(jarUrl);
            if (mcClassLoader == null) {
                dumpThreadClassLoaders();
                return false;
            }

            // Step 2: Put our JAR on that loader so Mixin + entrypoint share ONE AuthGate /
            // SessionHandler / module singleton space (ChildFirst duplicates break mixins).
            boolean added = tryAddURL(mcClassLoader, jarUrl);
            if (!added) {
                // continue — some loaders still resolve via other means
            }

            // Step 3: Register mixin config with the already-running Mixin service
            registerMixinConfig(mcClassLoader);

            // Step 4: Entrypoint on the SAME loader Mixin uses (old working sharing model)
            try {
                Class<?> entrypointClass = mcClassLoader.loadClass(ENTRYPOINT_CLASS);
                try {
                    Method initMethod = entrypointClass.getMethod("onInitializeClient");
                    Object instance = entrypointClass.getConstructor().newInstance();
                    // Prefer client thread; fall back inline (Downloads-era sync path).
                    runOnClientThread(mcClassLoader, () -> {
                        try {
                            initMethod.invoke(instance);
                            me.shedaniel.clothconfig2.internal.MixinRuntimeProbe.logDiagnostics("post-inject-entrypoint");
                        } catch (Exception e) {
                            Throwable cause = e.getCause() != null ? e.getCause() : e;
                            cause.printStackTrace();
                        }
                    });
                } catch (NoSuchMethodException ignored) {
                }
            } catch (ClassNotFoundException e) {
                return false;
            }

            return true;

        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            root.printStackTrace();
            return false;
        }
    }

    /**
     * Hooks our mixin JSON into Fabric/Lunar's already-started Mixin environment.
     * Without this, late inject only runs Fabric event callbacks — no gameplay mixins.
     */
    private static void registerMixinConfig(ClassLoader mcClassLoader) {
        final String config = "META-INF/mixins/cloth-config.mixins.json";
        try {
            Class<?> mixinsClass = Class.forName("org.spongepowered.asm.mixin.Mixins", true, mcClassLoader);
            mixinsClass.getMethod("addConfiguration", String.class).invoke(null, config);
            // Late inject: plugin onLoad may not fire until prepare; mark registered if Mixins accepted it.
            try {
                Object configs = mixinsClass.getMethod("getConfigs").invoke(null);
                if (configs instanceof java.util.Collection<?> collection) {
                    for (Object handle : collection) {
                        Object cfg = handle.getClass().getMethod("getConfig").invoke(handle);
                        String name = String.valueOf(cfg.getClass().getMethod("getName").invoke(cfg));
                        if (name != null && name.contains("cloth-config")) {
                            me.shedaniel.clothconfig2.internal.MixinRuntimeProbe
                                    .markMixinConfigLoaded("registered:" + name);
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            tryRetransformLoadedTargets(mcClassLoader);
            me.shedaniel.clothconfig2.internal.MixinRuntimeProbe.markInjectBootstrap();
        } catch (Throwable ignored) {
        }
    }

    private static Object resolveLiveInstrumentation(ClassLoader mcClassLoader) {
        if (liveInstrumentation != null) {
            return liveInstrumentation;
        }
        try {
            Object mirrored = System.getProperties().get(
                    "me.shedaniel.clothconfig2.injection.instrumentation");
            if (mirrored != null) {
                liveInstrumentation = mirrored;
                return mirrored;
            }
        } catch (Throwable ignored) {
        }
        // Prefer this loader's AgentMain only if it already holds Instrumentation
        // (same-loader bootstrap). Knot's post-addURL copy is usually null.
        try {
            Class<?> agentMain = Class.forName(
                    "me.shedaniel.clothconfig2.injection.AgentMain", false, mcClassLoader);
            Object inst = agentMain.getMethod("getInstrumentation").invoke(null);
            if (inst != null) {
                liveInstrumentation = inst;
                return inst;
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> agentMain = Class.forName(
                    "me.shedaniel.clothconfig2.injection.AgentMain",
                    false, DragoniteFabricLoader.class.getClassLoader());
            Object inst = agentMain.getMethod("getInstrumentation").invoke(null);
            if (inst != null) {
                liveInstrumentation = inst;
                return inst;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void tryRetransformLoadedTargets(ClassLoader mcClassLoader) {
        try {
            Object inst = resolveLiveInstrumentation(mcClassLoader);
            if (inst == null) {
                return;
            }
            Method retransform = inst.getClass().getMethod("retransformClasses", Class[].class);
            // Named + intermediary names for every gameplay mixin target we ship.
            String[] targets = {
                    "net.minecraft.class_1297",
                    "net.minecraft.entity.Entity",
                    "net.minecraft.class_1309",
                    "net.minecraft.entity.LivingEntity",
                    "net.minecraft.class_1657",
                    "net.minecraft.entity.player.PlayerEntity",
                    "net.minecraft.class_1676",
                    "net.minecraft.entity.projectile.FireworkRocketEntity",
                    "net.minecraft.class_2248$class_4971",
                    "net.minecraft.block.AbstractBlock$AbstractBlockState",
                    "net.minecraft.class_761",
                    "net.minecraft.client.render.WorldRenderer",
                    "net.minecraft.class_757",
                    "net.minecraft.client.render.GameRenderer",
                    "net.minecraft.class_758",
                    "net.minecraft.client.render.BackgroundRenderer",
                    "net.minecraft.class_759",
                    "net.minecraft.client.render.item.HeldItemRenderer",
                    "net.minecraft.class_918",
                    "net.minecraft.client.render.item.ItemRenderer",
                    "net.minecraft.class_1007",
                    "net.minecraft.client.render.entity.PlayerEntityRenderer",
                    "net.minecraft.class_765",
                    "net.minecraft.client.render.LightmapTextureManager",
                    "net.minecraft.class_4603",
                    "net.minecraft.client.gui.hud.InGameOverlayRenderer",
                    "net.minecraft.class_329",
                    "net.minecraft.client.gui.hud.InGameHud",
                    "net.minecraft.class_465",
                    "net.minecraft.client.gui.screen.ingame.HandledScreen",
                    "net.minecraft.class_310",
                    "net.minecraft.client.MinecraftClient",
                    "net.minecraft.class_312",
                    "net.minecraft.client.Mouse",
                    "net.minecraft.class_309",
                    "net.minecraft.client.input.KeyboardInput",
                    "net.minecraft.class_4184",
                    "net.minecraft.client.render.Camera",
                    "net.minecraft.class_2535",
                    "net.minecraft.network.ClientConnection",
                    "net.minecraft.class_634",
                    "net.minecraft.client.network.ClientPlayNetworkHandler",
                    "net.minecraft.class_8673",
                    "net.minecraft.client.network.ClientCommonNetworkHandler",
                    "net.minecraft.class_636",
                    "net.minecraft.client.network.ClientPlayerInteractionManager",
                    "net.minecraft.class_746",
                    "net.minecraft.client.network.ClientPlayerEntity",
                    "net.minecraft.class_638",
                    "net.minecraft.client.world.ClientWorld",
            };
            java.util.LinkedHashSet<Class<?>> loaded = new java.util.LinkedHashSet<>();
            for (String name : targets) {
                try {
                    loaded.add(Class.forName(name, false, mcClassLoader));
                } catch (Throwable ignored) {
                }
            }
            try {
                Method allLoaded = inst.getClass().getMethod("getAllLoadedClasses");
                Class<?>[] all = (Class<?>[]) allLoaded.invoke(inst);
                java.util.HashSet<String> wanted = new java.util.HashSet<>();
                for (String name : targets) {
                    wanted.add(name);
                    int dot = name.lastIndexOf('.');
                    if (dot >= 0) {
                        wanted.add(name.substring(dot + 1));
                    }
                }
                for (Class<?> c : all) {
                    if (c == null) {
                        continue;
                    }
                    String name = c.getName();
                    if (wanted.contains(name)
                            || wanted.contains(name.substring(name.lastIndexOf('.') + 1))) {
                        if (c.getClassLoader() == mcClassLoader
                                || (c.getClassLoader() != null
                                && name.startsWith("net.minecraft."))) {
                            loaded.add(c);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            if (loaded.isEmpty()) {
                return;
            }
            Class<?>[] batch = loaded.toArray(Class<?>[]::new);
            retransform.invoke(inst, (Object) batch);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Resolve the Minecraft classloader using Lion's proven approach:
     * 1. Try Fabric's KnotClassLoader (fast path for vanilla Fabric)
     * 2. Walk all thread classloaders and test which one can load Minecraft classes
     * 3. Try Class.forName with seed classloaders (catches Genesis's loader hierarchy)
     *
     * IMPORTANT: On Lunar Client, Genesis bootstraps MC classes asynchronously.
     * The DLL payload thread can fire before any classloader has MC classes loaded.
     * We retry with exponential backoff for up to 60 seconds.
     */
    private static ClassLoader resolveMcClassLoader(java.net.URL jarUrl) {
        // Test class names: intermediary (Lunar), named (Fabric), MCP (Forge)
        String[] mcClassNames = {
            "net.minecraft.class_310",                // Intermediary (Lunar Client)
            "net.minecraft.client.MinecraftClient",   // Fabric named mappings
            "net.minecraft.client.Minecraft",         // Forge / MCP
            "net.minecraft.class_757",                // Intermediary GameRenderer
            "net.minecraft.client.main.Main"          // Fallback — loads early
        };

        int maxAttempts = 20;
        long delayMs = 500;  // Start at 500ms, backoff to 5s

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            System.out.println("[Dragonite-Agent] MC classloader resolve attempt " + attempt + "/" + maxAttempts);

            // --- Strategy 1: Thread CCL walk (original approach) ---
            ClassLoader found = walkThreadClassLoaders(mcClassNames);
            if (found != null) {
                System.out.println("[Dragonite-Agent] Found via thread walk on attempt " + attempt);
                return found;
            }

            // --- Strategy 2: Class.forName with seed classloaders (Lion's approach) ---
            // This catches cases where the MC classloader isn't any thread's CCL
            // but IS the defining loader for an already-loaded MC class.
            found = classForNameFallback(mcClassNames);
            if (found != null) {
                System.out.println("[Dragonite-Agent] Found via Class.forName on attempt " + attempt);
                return found;
            }

            if (attempt < maxAttempts) {
                System.out.println("[Dragonite-Agent] MC classes not found yet, waiting " + delayMs + "ms...");
                try { Thread.sleep(delayMs); } catch (InterruptedException e) { break; }
                delayMs = Math.min(delayMs + 500, 5000); // Backoff: 500 → 1000 → 1500 → ... → 5000
            }
        }

        System.err.println("[Dragonite-Agent] FATAL: Could not find MC classloader after " + maxAttempts + " attempts");
        return null;
    }

    /** Walk all thread context classloaders + parents, test if any can load MC classes. */
    private static ClassLoader walkThreadClassLoaders(String[] mcClassNames) {
        java.util.LinkedHashSet<ClassLoader> candidates = new java.util.LinkedHashSet<>();
        try {
            ThreadGroup root = Thread.currentThread().getThreadGroup();
            while (root != null && root.getParent() != null) root = root.getParent();
            if (root != null) {
                Thread[] all = new Thread[Math.max(64, root.activeCount() * 4)];
                int n = root.enumerate(all, true);
                for (int i = 0; i < n; i++) {
                    Thread t = all[i];
                    if (t == null) continue;
                    ClassLoader ccl = t.getContextClassLoader();
                    for (ClassLoader cl = ccl; cl != null; cl = cl.getParent()) {
                        candidates.add(cl);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Dragonite-Agent] ThreadGroup walk failed: " + e.getMessage());
        }

        // Also add system classloader chain
        try {
            for (ClassLoader cl = ClassLoader.getSystemClassLoader(); cl != null; cl = cl.getParent()) {
                candidates.add(cl);
            }
        } catch (Exception ignored) {}

        for (ClassLoader cl : candidates) {
            if (!canLoadMinecraft(cl, mcClassNames)) continue;
            System.out.println("[Dragonite-Agent] Found MC classloader: " + cl.getClass().getName());
            return cl;
        }
        return null;
    }

    /**
     * Fallback: try Class.forName on MC class names with various seed classloaders.
     * If successful, return the DEFINING classloader of that class (c.getClassLoader()).
     * This is Lion's approach — it finds the real MC classloader even when no thread
     * CCL points to it (e.g. Genesis loads MC in a child loader that no thread references).
     */
    private static ClassLoader classForNameFallback(String[] mcClassNames) {
        ClassLoader[] seeds = {
            Thread.currentThread().getContextClassLoader(),
            DragoniteFabricLoader.class.getClassLoader(),
            tryGetSystemCL(),
        };

        for (String name : mcClassNames) {
            for (ClassLoader seed : seeds) {
                if (seed == null) continue;
                try {
                    Class<?> c = Class.forName(name, false, seed);
                    ClassLoader actual = c.getClassLoader();
                    if (actual != null) {
                        System.out.println("[Dragonite-Agent] Class.forName found " + name +
                            " -> loader: " + actual.getClass().getName());
                        return actual;
                    }
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private static ClassLoader tryGetSystemCL() {
        try { return ClassLoader.getSystemClassLoader(); }
        catch (Throwable t) { return null; }
    }

    /** Test if a classloader can load any Minecraft class. */
    private static boolean canLoadMinecraft(ClassLoader cl, String[] classNames) {
        for (String name : classNames) {
            try {
                cl.loadClass(name);
                return true;
            } catch (ClassNotFoundException ignored) {}
        }
        return false;
    }

    /** Try to add a URL to a classloader via reflection (works on URLClassLoader and KnotClassLoader). */
    private static boolean tryAddURL(ClassLoader cl, java.net.URL url) {
        // Try public addURL method (some classloaders expose this)
        try {
            Method addURL = cl.getClass().getMethod("addURL", java.net.URL.class);
            addURL.invoke(cl, url);
            return true;
        } catch (Exception ignored) {}

        // Try URLClassLoader.addURL (protected, need setAccessible)
        try {
            Method addURL = java.net.URLClassLoader.class.getDeclaredMethod("addURL", java.net.URL.class);
            addURL.setAccessible(true);
            addURL.invoke(cl, url);
            return true;
        } catch (Exception ignored) {}

        // Try Fabric KnotClassLoader's addCodeSource via delegate
        try {
            Method getDelegateM = cl.getClass().getDeclaredMethod("getDelegate");
            getDelegateM.setAccessible(true);
            Object delegate = getDelegateM.invoke(cl);
            Method addCodeSource = delegate.getClass().getDeclaredMethod("addCodeSource", java.nio.file.Path.class);
            addCodeSource.setAccessible(true);
            addCodeSource.invoke(delegate, java.nio.file.Path.of(url.toURI()));
            System.out.println("[Dragonite-Agent] Added JAR via Fabric KnotClassDelegate.addCodeSource()");
            return true;
        } catch (Exception ignored) {}

        return false;
    }

    /** Bridge classloader: loads from our JAR URL, delegates to MC classloader for everything else. */
    private static class BridgeClassLoader extends java.net.URLClassLoader {
        private final ClassLoader mcParent;

        BridgeClassLoader(java.net.URL[] urls, ClassLoader mcParent) {
            super(urls, mcParent);
            this.mcParent = mcParent;
            System.out.println("[Dragonite-Agent] BridgeClassLoader created with parent: " + mcParent.getClass().getName());
        }
    }

    /**
     * Try alternative method signatures for setting allowed prefixes
     * across different Fabric Loader versions.
     */
    private static void tryAlternativePrefixMethods(Object delegate, Path jarPath) {
        for (Method m : delegate.getClass().getDeclaredMethods()) {
            if (m.getName().toLowerCase().contains("prefix") || m.getName().toLowerCase().contains("allowed")) {
                System.out.println("[Dragonite-Agent] Found candidate method: " + m.getName() + "(" + Arrays.toString(m.getParameterTypes()) + ")");
            }
        }
    }

    /**
     * Try alternative methods for adding a code source.
     */
    private static boolean tryAlternativeCodeSource(Object delegate, ClassLoader knotLoader, Path jarPath, File modJar) {
        // Try addCodeSource with URL
        try {
            Method m = delegate.getClass().getDeclaredMethod("addCodeSource", java.net.URL.class);
            m.setAccessible(true);
            m.invoke(delegate, modJar.toURI().toURL());
            System.out.println("[Dragonite-Agent] addCodeSource(URL) OK");
            return true;
        } catch (Exception ignored) {}

        // Try addURL on URLClassLoader parent
        try {
            if (knotLoader instanceof java.net.URLClassLoader) {
                Method addURL = java.net.URLClassLoader.class.getDeclaredMethod("addURL", java.net.URL.class);
                addURL.setAccessible(true);
                addURL.invoke(knotLoader, modJar.toURI().toURL());
                System.out.println("[Dragonite-Agent] addURL() on KnotClassLoader OK");
                return true;
            }
        } catch (Exception ignored) {}

        return false;
    }

    /**
     * Dump all methods on the delegate for debugging.
     */
    private static void dumpDelegateMethods(Object delegate) {
        System.out.println("[Dragonite-Agent] --- Delegate methods dump ---");
        for (Method m : delegate.getClass().getDeclaredMethods()) {
            System.out.println("[Dragonite-Agent]   " + m.getName() + "(" + Arrays.toString(m.getParameterTypes()) + ") -> " + m.getReturnType().getSimpleName());
        }
        System.out.println("[Dragonite-Agent] --- End dump ---");
    }

    /**
     * Dump all thread classloaders for debugging when KnotClassLoader isn't found.
     */
    private static void dumpThreadClassLoaders() {
        System.out.println("[Dragonite-Agent] --- Thread ClassLoader dump ---");
        ThreadGroup root = Thread.currentThread().getThreadGroup();
        while (root.getParent() != null) root = root.getParent();
        Thread[] buf = new Thread[root.activeCount() + 64];
        int n = root.enumerate(buf, true);
        for (int i = 0; i < n; i++) {
            Thread t = buf[i];
            if (t != null) {
                ClassLoader cl = t.getContextClassLoader();
                System.out.println("[Dragonite-Agent]   Thread[" + t.getName() + "] -> " +
                    (cl != null ? cl.getClass().getName() : "null"));
            }
        }
        System.out.println("[Dragonite-Agent] --- End dump ---");
    }

    private static void invokeEntrypoint(ClassLoader knotLoader) throws Exception {
        System.out.println("[Dragonite-Agent] Loading entrypoint class: " + ENTRYPOINT_CLASS);
        Class<?> entrypoint = knotLoader.loadClass(ENTRYPOINT_CLASS);
        System.out.println("[Dragonite-Agent] Class loaded: " + entrypoint.getName());

        Method init = entrypoint.getMethod("onInitializeClient");
        System.out.println("[Dragonite-Agent] Found onInitializeClient() method");

        Object instance = entrypoint.getDeclaredConstructor().newInstance();
        System.out.println("[Dragonite-Agent] Instance created, invoking on client thread...");

        runOnClientThread(knotLoader, () -> {
            try {
                init.invoke(instance);
                System.out.println("[Dragonite-Agent] onInitializeClient() COMPLETED SUCCESSFULLY");
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;

                if (cause instanceof IllegalStateException
                        && cause.getMessage() != null
                        && cause.getMessage().contains("GameOptions")) {
                    System.out.println("[Dragonite-Agent] GameOptions locked — applying keybind patch...");
                    patchKeybinds(knotLoader);
                    System.out.println("[Dragonite-Agent] Keybind patch applied");
                } else {
                    System.err.println("[Dragonite-Agent] Entrypoint exception: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
                    cause.printStackTrace();
                }
            } catch (Exception e2) {
                System.err.println("[Dragonite-Agent] Entrypoint error: " + e2.getClass().getSimpleName() + ": " + e2.getMessage());
                e2.printStackTrace();
            }
        });
    }

    private static void patchKeybinds(ClassLoader knotLoader) {
        try {
            Class<?> mcClass = knotLoader.loadClass("net.minecraft.client.MinecraftClient");
            Object mc = mcClass.getMethod("getInstance").invoke(null);
            Object options = mcClass.getField("options").get(mc);
            if (options == null) {
                System.err.println("[Dragonite-Agent] GameOptions is null");
                return;
            }

            Field allKeysField = null;
            for (Field f : options.getClass().getDeclaredFields()) {
                if (f.getType().isArray()
                        && f.getType().getComponentType().getName().contains("KeyBinding")) {
                    allKeysField = f;
                    break;
                }
            }
            if (allKeysField == null) {
                System.err.println("[Dragonite-Agent] allKeys field not found in GameOptions");
                return;
            }
            allKeysField.setAccessible(true);
            Object[] existing = (Object[]) allKeysField.get(options);
            System.out.println("[Dragonite-Agent] Existing keybinds: " + existing.length);

            Class<?> keyBindClass = existing[0].getClass();
            while (keyBindClass.getSuperclass() != null
                    && !keyBindClass.getSuperclass().equals(Object.class)
                    && keyBindClass.getSuperclass().getName().contains("KeyBinding")) {
                keyBindClass = keyBindClass.getSuperclass();
            }

            Field keysByIdField = null;
            for (Field f : keyBindClass.getDeclaredFields()) {
                if (f.getType().getName().contains("Map")) {
                    f.setAccessible(true);
                    if (f.get(null) instanceof java.util.Map) {
                        keysByIdField = f;
                        break;
                    }
                }
            }

            java.util.List<Object> pending = new java.util.ArrayList<>();
            try {
                Class<?> reg = knotLoader.loadClass(
                    "net.fabricmc.fabric.impl.client.keybinding.KeyBindingRegistryImpl");
                for (Field f : reg.getDeclaredFields()) {
                    if (f.getType().getName().contains("List")) {
                        f.setAccessible(true);
                        Object val = f.get(null);
                        if (val instanceof java.util.List<?> list) {
                            for (Object item : list) {
                                if (keyBindClass.isInstance(item)) {
                                    pending.add(item);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("[Dragonite-Agent] Fabric keybind registry: " + e.getMessage());
            }

            if (pending.isEmpty()) {
                System.out.println("[Dragonite-Agent] No pending keybinds to inject");
                return;
            }

            Object[] updated = Arrays.copyOf(existing, existing.length + pending.size());
            for (int i = 0; i < pending.size(); i++) {
                updated[existing.length + i] = pending.get(i);
            }
            allKeysField.set(options, updated);
            System.out.println("[Dragonite-Agent] Injected " + pending.size() + " keybind(s)");

            if (keysByIdField != null) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> map =
                    (java.util.Map<String, Object>) keysByIdField.get(null);
                Method getKey = keyBindClass.getMethod("getTranslationKey");
                for (Object kb : pending) {
                    map.put((String) getKey.invoke(kb), kb);
                }
            }

        } catch (Exception e) {
            System.err.println("[Dragonite-Agent] Keybind patch failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void runOnClientThread(ClassLoader knotLoader, Runnable task) {
        try {
            Class<?> mcClass = null;
            for (String name : new String[]{
                    "net.minecraft.class_310",
                    "net.minecraft.client.MinecraftClient",
                    "net.minecraft.client.Minecraft",
            }) {
                try {
                    mcClass = knotLoader.loadClass(name);
                    break;
                } catch (ClassNotFoundException ignored) {
                }
            }
            if (mcClass == null) {
                task.run();
                return;
            }
            Object inst = null;
            for (String getter : new String[]{"method_1551", "getInstance"}) {
                try {
                    inst = mcClass.getMethod(getter).invoke(null);
                    if (inst != null) {
                        break;
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (inst == null) {
                try {
                    inst = mcClass.getMethod("getInstance").invoke(null);
                } catch (Throwable ignored) {
                }
            }
            if (inst == null) {
                task.run();
                return;
            }
            java.lang.reflect.Method execute = null;
            for (java.lang.reflect.Method method : mcClass.getMethods()) {
                if (method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == Runnable.class
                        && method.getReturnType() == Void.TYPE
                        && java.lang.reflect.Modifier.isPublic(method.getModifiers())
                        && !java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                    // Prefer Yarn/official name when present.
                    if ("execute".equals(method.getName()) || "method_2046".equals(method.getName())) {
                        execute = method;
                        break;
                    }
                    if (execute == null) {
                        execute = method;
                    }
                }
            }
            if (execute == null) {
                task.run();
                return;
            }
            execute.invoke(inst, task);
        } catch (Exception e) {
            System.err.println("[Dragonite-Agent] Client thread dispatch failed (" + e.getMessage() + "), running inline");
            task.run();
        }
    }

    /**
     * Finds the classloader that can load Minecraft classes.
     * Instead of matching by name (breaks on Lunar's obfuscated classloader),
     * we test each candidate classloader to see if it can actually load
     * Minecraft classes — same approach Lion uses in resolveMcClassLoader().
     */
    private static ClassLoader findKnotClassLoader() {
        // --- Strategy 1: Name-based match (fast path for vanilla Fabric) ---
        ClassLoader named = findByName();
        if (named != null) return named;

        // --- Strategy 2: Capability-based (works on Lunar's obfuscated loader) ---
        System.out.println("[Dragonite-Agent] Name-based search failed, trying capability-based...");

        // Collect every classloader from every thread + parents
        java.util.LinkedHashSet<ClassLoader> candidates = new java.util.LinkedHashSet<>();
        try {
            ThreadGroup root = Thread.currentThread().getThreadGroup();
            while (root != null && root.getParent() != null) root = root.getParent();
            if (root != null) {
                Thread[] all = new Thread[Math.max(64, root.activeCount() * 4)];
                int n = root.enumerate(all, true);
                for (int i = 0; i < n; i++) {
                    Thread t = all[i];
                    if (t == null) continue;
                    // Walk the classloader chain (current + all parents)
                    for (ClassLoader cl = t.getContextClassLoader(); cl != null; cl = cl.getParent()) {
                        candidates.add(cl);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Dragonite-Agent] ThreadGroup walk failed: " + e.getMessage());
        }

        // Also add system classloader chain
        try {
            for (ClassLoader cl = ClassLoader.getSystemClassLoader(); cl != null; cl = cl.getParent()) {
                candidates.add(cl);
            }
        } catch (Exception ignored) {}

        System.out.println("[Dragonite-Agent] Found " + candidates.size() + " candidate classloaders");

        // Test each one: can it load Minecraft's main class?
        // IMPORTANT: Lunar uses intermediary mappings (class_310 = MinecraftClient)
        String[] mcClassNames = {
            "net.minecraft.class_310",                // Intermediary (Lunar Client uses this!)
            "net.minecraft.client.MinecraftClient",   // Fabric named mappings
            "net.minecraft.client.Minecraft",         // Forge / MCP name
            "net.minecraft.class_757",                // Intermediary for GameRenderer
            "net.minecraft.client.main.Main"          // Fallback
        };

        for (ClassLoader cl : candidates) {
            for (String mcClass : mcClassNames) {
                try {
                    cl.loadClass(mcClass);
                    System.out.println("[Dragonite-Agent] Found Minecraft classloader: " +
                        cl.getClass().getName() + " (resolved " + mcClass + ")");
                    return cl;
                } catch (ClassNotFoundException ignored) {
                    // This classloader can't load Minecraft — try next
                }
            }
        }

        return null;
    }

    /** Original name-based search for vanilla Fabric (fast path). */
    private static ClassLoader findByName() {
        ThreadGroup root = Thread.currentThread().getThreadGroup();
        while (root.getParent() != null) root = root.getParent();

        Thread[] buf = new Thread[root.activeCount() + 64];
        int n = root.enumerate(buf, true);
        for (int i = 0; i < n; i++) {
            if (buf[i] == null) continue;
            ClassLoader cl = buf[i].getContextClassLoader();
            if (cl != null && cl.getClass().getName().contains("KnotClassLoader")) {
                return cl;
            }
        }
        ClassLoader current = Thread.currentThread().getContextClassLoader();
        if (current != null && current.getClass().getName().contains("KnotClassLoader")) {
            return current;
        }
        return null;
    }

    private static Object getDelegate(ClassLoader knotLoader) throws Exception {
        // Try standard Fabric KnotClassLoader.getDelegate()
        try {
            Method m = knotLoader.getClass().getDeclaredMethod("getDelegate");
            m.setAccessible(true);
            return m.invoke(knotLoader);
        } catch (NoSuchMethodException e) {
            // Not a standard KnotClassLoader (e.g. Lunar's obfuscated version)
            // Return a wrapper that exposes what we need
            System.out.println("[Dragonite-Agent] getDelegate() not found on " +
                knotLoader.getClass().getName() + " — using direct classloader");
            return new DirectClassLoaderDelegate(knotLoader);
        }
    }

    /**
     * Fallback delegate for non-Fabric classloaders (e.g. Lunar's obfuscated
     * genesis loader). Provides the methods DragoniteFabricLoader expects.
     */
    private static class DirectClassLoaderDelegate {
        private final ClassLoader cl;

        DirectClassLoaderDelegate(ClassLoader cl) {
            this.cl = cl;
        }

        /** Dummy — Lunar doesn't need prefix filtering */
        public void setAllowedPrefixes(Path jarPath, String[] prefixes) {
            // no-op
        }

        /** Add the JAR as a code source using addURL or equivalent */
        public void addCodeSource(Path jarPath) throws Exception {
            java.net.URL url = jarPath.toUri().toURL();
            // Try addURL on the classloader
            boolean added = false;
            try {
                Method addURL = cl.getClass().getMethod("addURL", java.net.URL.class);
                addURL.invoke(cl, url);
                added = true;
            } catch (NoSuchMethodException ignored) {}

            if (!added) {
                try {
                    Method addURL = java.net.URLClassLoader.class.getDeclaredMethod("addURL", java.net.URL.class);
                    addURL.setAccessible(true);
                    addURL.invoke(cl, url);
                    added = true;
                } catch (Exception ignored) {}
            }

            if (!added) {
                System.out.println("[Dragonite-Agent] addURL not available — JAR classes loaded via URLClassLoader parent");
            }
        }
    }
}
