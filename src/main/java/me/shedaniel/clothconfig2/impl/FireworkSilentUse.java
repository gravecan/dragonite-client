package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;


@Deprecated
public final class FireworkSilentUse {

    private FireworkSilentUse() {}

    public static boolean use(MinecraftClient mc, int rocketHotbarSlot) {
        return HotbarSilentUse.useItemFromHotbar(mc, rocketHotbarSlot);
    }
}
