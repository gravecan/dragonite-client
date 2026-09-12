package me.shedaniel.clothconfig2.internal;

import me.shedaniel.clothconfig2.impl.ConfigBuilderImpl;
import me.shedaniel.clothconfig2.impl.ConfigCategoryImpl;
import me.shedaniel.clothconfig2.impl.Config_StringList;
import me.shedaniel.clothconfig2.impl.HudConfigInit;
import me.shedaniel.clothconfig2.internal.secure.JavaWatchdog;

public final class AuthGate {

    private static long lastDistributedCheck;
    private static final long DISTRIBUTED_CHECK_MS = 4_000L;
    private static final long ALLOW_FEATURES_CACHE_MS = 50L;

    private static volatile boolean cachedAllowFeatures;
    private static volatile long cachedAllowFeaturesAtMs;

    private AuthGate() {}

    public static void invalidateCache() {
        cachedAllowFeaturesAtMs = 0L;
    }

    
    public static boolean mixinGate() {
        if (allowFeatures()) {
            return true;
        }
        enforceSession();
        return false;
    }

    public static boolean allowFeatures() {
        long now = System.currentTimeMillis();
        if (now - cachedAllowFeaturesAtMs < ALLOW_FEATURES_CACHE_MS) {
            return cachedAllowFeatures;
        }
        cachedAllowFeatures = computeAllowFeatures();
        cachedAllowFeaturesAtMs = now;
        return cachedAllowFeatures;
    }

    private static boolean computeAllowFeatures() {
        SessionHandler session = SessionHandler.getInstance();
        return evaluateFeatureGate(
                Config_StringList.isDestroyed(),
                session != null && session.isInitialized(),
                session != null && session.isAuthenticated(),
                SecurityVault.isArmed(),
                SecurityVault.invariantHolds(),
                SecurityVault.verifyCombo(0x1A),
                JavaWatchdog.isOk());
    }

    static boolean evaluateFeatureGate(boolean destroyed, boolean initialized,
                                       boolean authenticated, boolean armed,
                                       boolean invariant, boolean comboOk,
                                       boolean watchdogOk) {
        if (destroyed || !initialized || !authenticated) {
            return false;
        }
        if (!armed || !invariant || !comboOk) {
            return false;
        }
        return watchdogOk;
    }

    
    public static boolean allowGui() {
        if (Config_StringList.isDestroyed()) {
            return false;
        }
        SessionHandler session = SessionHandler.getInstance();
        if (!session.isInitialized()) {
            return false;
        }
        NetworkHandler network = session.getNetworkHandler();
        return network != null && network.isValid();
    }

    
    public static void runDistributedChecks() {
        long now = System.currentTimeMillis();
        if (now - lastDistributedCheck < DISTRIBUTED_CHECK_MS) {
            return;
        }
        lastDistributedCheck = now;
        if (!allowFeatures()) {
            return;
        }
        try {
            new Thread(() -> {
                try {
                    CheckManager.check();
                } catch (Throwable ignored) {}
            }, "cloth-security-check").start();
        } catch (Throwable ignored) {
        }
    }

    
    public static void enforceSession() {
        if (allowFeatures()) {
            return;
        }
        ConfigBuilderImpl mgr = HudConfigInit.getManager();
        if (mgr == null) {
            return;
        }
        for (ConfigCategoryImpl mod : mgr.getModules()) {
            if (mod.isEnabled()) {
                mod.setEnabled(false);
            }
        }
    }
}
