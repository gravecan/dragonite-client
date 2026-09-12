package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;

/**
 * Tick hook for modules registered without compile-time references from
 * {@link HudConfigInit}. Keeps Knot free of concrete {@code Config_*} names
 * for deferred/secure-payload candidates.
 */
public interface ClientTickHook {
    void onClientTick(MinecraftClient client);
}
