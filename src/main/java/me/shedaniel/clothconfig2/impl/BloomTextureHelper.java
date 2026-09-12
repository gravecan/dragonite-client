package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import com.mojang.blaze3d.systems.RenderSystem;


public final class BloomTextureHelper {
    public static final Identifier BLOOM = Identifier.of("cloth-config2", "textures/bloom.png");

    private static Boolean available;

    private BloomTextureHelper() {}

    public static void invalidateCache() {
        available = null;
    }

    public static boolean bind(MinecraftClient mc) {
        if (mc == null) {
            return false;
        }
        if (available == null) {
            available = mc.getResourceManager().getResource(BLOOM).isPresent();
        }
        if (!available) {
            return false;
        }
        try {
            RenderSystem.setShaderTexture(0, BLOOM);
            return true;
        } catch (Throwable ignored) {
            available = false;
            return false;
        }
    }
}
