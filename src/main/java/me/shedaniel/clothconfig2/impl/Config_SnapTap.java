package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.option.GameOptions;


public class Config_SnapTap extends ConfigCategoryImpl {

    public static Config_SnapTap INSTANCE;

    private static long lastLeft;
    private static long lastRight;
    private static long lastForward;
    private static long lastBack;

    public Config_SnapTap() {
        super("SnapTap", "Last movement key wins when opposing keys held", Cat.MOVEMENT);
        INSTANCE = this;
        setTooltip("Achilles port — W+S or A+D: most recent key takes priority.");
    }

    public static void onKeyTimes(MinecraftClient mc) {
        if (INSTANCE == null || !INSTANCE.isEnabled() || mc.options == null) {
            return;
        }
        long now = System.currentTimeMillis();
        GameOptions options = mc.options;
        if (options.leftKey.wasPressed()) {
            lastLeft = now;
        }
        if (options.rightKey.wasPressed()) {
            lastRight = now;
        }
        if (options.forwardKey.wasPressed()) {
            lastForward = now;
        }
        if (options.backKey.wasPressed()) {
            lastBack = now;
        }
    }

    public static void apply(Input input, MinecraftClient mc) {
        if (INSTANCE == null || !INSTANCE.isEnabled() || mc.options == null || input == null) {
            return;
        }
        GameOptions options = mc.options;

        if (options.forwardKey.isPressed() && options.backKey.isPressed()) {
            input.movementForward = lastForward >= lastBack ? 1f : -1f;
        }
        if (options.leftKey.isPressed() && options.rightKey.isPressed()) {
            input.movementSideways = lastLeft >= lastRight ? 1f : -1f;
        }
    }
}
