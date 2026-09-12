package me.shedaniel.clothconfig2.internal;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight mixin / inject diagnostics (stdout → Minecraft latest.log).
 */
public final class MixinRuntimeProbe {

    private static final String LOG_PREFIX = "[ClothConfig][MixinProbe] ";

    private static volatile String bootstrapMode = "fabric-mod";
    private static volatile boolean mixinConfigLoaded;
    private static final Set<String> appliedMixins = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger deferredTicks = new AtomicInteger(200);

    private MixinRuntimeProbe() {}

    public static void markInjectBootstrap() {
        bootstrapMode = "late-inject-knot-shared";
    }

    public static void markMixinConfigLoaded(String mixinPackage) {
        mixinConfigLoaded = true;
        System.out.println(LOG_PREFIX + "Mixin config plugin loaded (package=" + mixinPackage + ")");
    }

    public static void noteMixinApplied(String mixinSimpleName) {
        if (appliedMixins.add(mixinSimpleName)) {
            System.out.println(LOG_PREFIX + "Gameplay mixin alive: " + mixinSimpleName);
        }
    }

    public static void logDiagnostics(String phase) {
        boolean fabricListed = probeFabricModListed();
        boolean heartbeat = !appliedMixins.isEmpty();
        ClassLoader cl = MixinRuntimeProbe.class.getClassLoader();
        String loader = cl != null ? cl.getClass().getName() : "null";

        String verdict;
        if (heartbeat) {
            verdict = "MIXINS_ACTIVE";
        } else if (mixinConfigLoaded && "late-inject-knot-shared".equals(bootstrapMode)) {
            verdict = "MIXIN_CONFIG_REGISTERED_NO_HEARTBEAT_YET (need tick / already-loaded targets)";
        } else if ("late-inject-child-first".equals(bootstrapMode)) {
            verdict = "INJECT_LIKELY_NO_MC_MIXINS (events only)";
        } else if (mixinConfigLoaded && fabricListed) {
            verdict = "FABRIC_MIXIN_CONFIG_OK_BUT_NO_HEARTBEAT (check auth gate or failed applies)";
        } else if (fabricListed) {
            verdict = "MOD_LOADED_MIXIN_CONFIG_NOT_SEEN";
        } else {
            verdict = "MOD_NOT_IN_FABRIC_LOADER";
        }

        System.out.println(LOG_PREFIX + "phase=" + phase
                + " bootstrap=" + bootstrapMode
                + " classLoader=" + loader
                + " fabricModListed=" + fabricListed
                + " mixinConfigLoaded=" + mixinConfigLoaded
                + " heartbeatMixins=" + Collections.unmodifiableSet(appliedMixins)
                + " verdict=" + verdict);
    }

    public static void onClientTick() {
        int left = deferredTicks.getAndDecrement();
        if (left == 0) {
            logDiagnostics("delayed-tick");
        }
    }

    private static boolean probeFabricModListed() {
        try {
            Class<?> flClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object inst = flClass.getMethod("getInstance").invoke(null);
            Object result = flClass.getMethod("isModLoaded", String.class).invoke(inst, "cloth-config");
            return Boolean.TRUE.equals(result);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
