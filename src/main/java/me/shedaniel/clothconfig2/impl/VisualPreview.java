package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;


public final class VisualPreview {
    private VisualPreview() {}

    public static boolean isClickGui(Screen screen) {
        return screen instanceof ClothConfigScreen;
    }

    public static boolean allowHudVisuals(MinecraftClient mc) {
        if (mc == null) {
            return false;
        }
        Screen screen = mc.currentScreen;
        return screen == null || isClickGui(screen);
    }
}
