package me.shedaniel.clothconfig2.injection;

import me.shedaniel.clothconfig2.injection.bridge.DragoniteAgent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

/**
 * Java Agent entry point for the Dragonite injection system.
 *
 * Referenced in the JAR manifest as Premain-Class and Agent-Class.
 * When loaded into a target JVM, installs a master class transformer
 * and bootstraps the Dragonite client via Fabric's classloader.
 */
public class AgentMain {

    /** Cross-loader handoff key — Knot may redefine this class with a null static. */
    public static final String INSTRUMENTATION_PROPERTY =
            "me.shedaniel.clothconfig2.injection.instrumentation";

    private static Instrumentation instrumentation;
    private static boolean initialized = false;
    private static final List<ITransformer> transformers = new ArrayList<>();

    /**
     * Called by JVM when loaded via -javaagent flag (before main).
     */
    public static void premain(String args, Instrumentation inst) {
        System.out.println("[Dragonite-Agent] premain() called — loading as pre-main agent");
        initialize(args, inst);
    }

    /**
     * Called by JVM when loaded via Attach API (runtime injection).
     */
    public static void agentmain(String args, Instrumentation inst) {
        System.out.println("[Dragonite-Agent] agentmain() called — runtime injection active");
        initialize(args, inst);
    }

    private static void initialize(String args, Instrumentation inst) {
        if (initialized) {
            System.out.println("[Dragonite-Agent] Already initialized — skipping");
            return;
        }
        instrumentation = inst;
        initialized = true;
        // Knot may later define a second AgentMain with a null static after tryAddURL.
        // Mirror the live Instrumentation where every classloader can resolve it.
        try {
            System.getProperties().put(INSTRUMENTATION_PROPERTY, inst);
        } catch (Throwable ignored) {
        }

        System.out.println("[Dragonite-Agent] Instrumentation received: " + inst.getClass().getName());
        System.out.println("[Dragonite-Agent]   Can retransform: " + inst.isRetransformClassesSupported());
        System.out.println("[Dragonite-Agent]   Can redefine: " + inst.isRedefineClassesSupported());
        System.out.println("[Dragonite-Agent]   Loaded classes: " + inst.getAllLoadedClasses().length);

        // Install the master class file transformer
        inst.addTransformer(new MasterTransformer(), true);
        System.out.println("[Dragonite-Agent] Master transformer installed");

        // Bootstrap the Dragonite client
        System.out.println("[Dragonite-Agent] Starting Dragonite bootstrap...");
        try {
            DragoniteAgent.bootstrap();
            System.out.println("[Dragonite-Agent] Bootstrap completed — Dragonite should be active");
        } catch (Throwable t) {
            System.err.println("[Dragonite-Agent] Bootstrap FAILED: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            t.printStackTrace();
        }

        if (args != null && !args.isEmpty()) {
            processArgs(args);
        }
    }

    private static void processArgs(String args) {
        for (String part : args.split(",")) {
            String[] kv = part.split("=");
            if (kv.length == 2) {
                String key = kv[0].trim();
                String value = kv[1].trim();
                System.out.println("[Dragonite-Agent] Arg: " + key + "=" + value);
                if ("debug".equals(key) && Boolean.parseBoolean(value)) {
                    System.out.println("[Dragonite-Agent] Debug mode enabled");
                }
            }
        }
    }

    // ---- Public API ----

    public static Instrumentation getInstrumentation() {
        if (instrumentation != null) {
            return instrumentation;
        }
        try {
            Object mirrored = System.getProperties().get(INSTRUMENTATION_PROPERTY);
            if (mirrored instanceof Instrumentation live) {
                return live;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static boolean isInitialized() { return initialized; }

    public static void registerTransformer(ITransformer t) {
        synchronized (transformers) { transformers.add(t); }
    }

    public static void unregisterTransformer(ITransformer t) {
        synchronized (transformers) { transformers.remove(t); }
    }

    public static void retransformClass(Class<?> c) throws UnmodifiableClassException {
        if (instrumentation != null && instrumentation.isRetransformClassesSupported()) {
            instrumentation.retransformClasses(c);
        }
    }

    public static Class<?>[] getAllLoadedClasses() {
        return instrumentation != null ? instrumentation.getAllLoadedClasses() : new Class<?>[0];
    }

    // ---- Master Transformer ----

    private static class MasterTransformer implements ClassFileTransformer {
        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            if (className == null) return null;

            byte[] result = classfileBuffer;
            boolean modified = false;

            synchronized (transformers) {
                for (ITransformer t : transformers) {
                    if (t.shouldTransform(className)) {
                        try {
                            byte[] out = t.transform(className, result);
                            if (out != null) {
                                result = out;
                                modified = true;
                            }
                        } catch (Exception e) {
                            System.err.println("[Dragonite-Agent] Transform error in "
                                + className + " (" + t.getName() + "): " + e.getMessage());
                        }
                    }
                }
            }
            return modified ? result : null;
        }
    }
}
