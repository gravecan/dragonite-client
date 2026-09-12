package me.shedaniel.clothconfig2.internal.secure;

import me.shedaniel.clothconfig2.impl.ConfigBuilderImpl;
import me.shedaniel.clothconfig2.impl.ConfigCategoryImpl;
import me.shedaniel.clothconfig2.impl.HudConfigInit;
import me.shedaniel.clothconfig2.internal.KeyHalfFetcher;

/**
 * Post-auth registration for modules that must not appear as concrete
 * {@code new Config_*()} references in bootstrap code.
 * Prefers encrypted class payload → Knot defineClass; falls back to
 * Class.forName only when the plaintext class is still present (dev / pre-strip).
 */
public final class ProtectedModuleRegistrar {
    public static final String AUTO_RESPAWN_INTERNAL =
            "me/shedaniel/clothconfig2/impl/Config_AutoRespawn";

    private static final String AUTO_RESPAWN_FQCN =
            AUTO_RESPAWN_INTERNAL.replace('/', '.');

    private static volatile boolean autoRespawnRegistered;

    private ProtectedModuleRegistrar() {
    }

    public static void registerPostAuthModules() {
        if (autoRespawnRegistered) {
            return;
        }
        ConfigBuilderImpl manager = HudConfigInit.getManager();
        if (manager == null) {
            return;
        }
        ClassLoader loader = manager.getClass().getClassLoader();
        try {
            Class<?> clazz = loadAutoRespawn(loader);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            if (!(instance instanceof ConfigCategoryImpl module)) {
                throw new IllegalStateException("deferred module is not ConfigCategoryImpl: " + AUTO_RESPAWN_FQCN);
            }
            if (!manager.addModule(module)) {
                autoRespawnRegistered = true;
                return;
            }
            manager.loadKeybinds();
            autoRespawnRegistered = true;
        } catch (ClassNotFoundException missing) {
            System.err.println("[ClothConfig] Deferred module missing (not registered): " + AUTO_RESPAWN_FQCN);
        } catch (Exception e) {
            System.err.println("[ClothConfig] Deferred module registration failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            // Fail closed for encrypted path: wipe key material.
            ClassPayloadKey.clear();
            KeyHalfFetcher.clear();
        }
    }

    private static Class<?> loadAutoRespawn(ClassLoader loader) throws Exception {
        if (ClassPayloadLoader.payloadResourcePresent(loader, AUTO_RESPAWN_INTERNAL)) {
            return ClassPayloadLoader.loadEncrypted(loader, AUTO_RESPAWN_INTERNAL);
        }
        return Class.forName(AUTO_RESPAWN_FQCN, true, loader);
    }

    public static boolean isAutoRespawnRegistered() {
        return autoRespawnRegistered;
    }
}
