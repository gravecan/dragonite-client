package me.shedaniel.clothconfig2.internal;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;

/**
 * Resolves the live Minecraft session identity for auth webhooks and server requests.
 */
public final class McIdentityResolver {

    private McIdentityResolver() {}

    public static GameProfile resolve(GameProfile fallback) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.getSession() != null) {
                var session = mc.getSession();
                String name = session.getUsername();
                if (name != null && !name.isBlank() && !"Unknown".equalsIgnoreCase(name)) {
                    return new GameProfile(session.getUuidOrNull(), name);
                }
            }
        } catch (Throwable ignored) {
        }
        if (fallback != null && fallback.getName() != null && !fallback.getName().isBlank()
                && !"Unknown".equalsIgnoreCase(fallback.getName())) {
            return fallback;
        }
        return fallback;
    }

    public static GameProfile waitForResolvableProfile(GameProfile seed, int maxWaitMs) {
        long deadline = System.nanoTime() + (long) maxWaitMs * 1_000_000L;
        GameProfile profile = resolve(seed);
        while (needsWait(profile) && System.nanoTime() < deadline) {
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            profile = resolve(seed);
        }
        return profile != null ? profile : seed;
    }

    private static boolean needsWait(GameProfile profile) {
        if (profile == null || profile.getName() == null) {
            return true;
        }
        String name = profile.getName().trim();
        return name.isEmpty() || "Unknown".equalsIgnoreCase(name);
    }
}
