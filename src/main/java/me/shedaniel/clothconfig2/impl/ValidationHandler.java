package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import net.minecraft.client.MinecraftClient;

public class ValidationHandler extends ConfigCategoryImpl {
    private final DoubleFieldBuilder speed;

    public ValidationHandler() {
        super("Fly", "Creative flight", Cat.MOVEMENT);
        setTooltip("Survival creative flight. Speed 1–20 (5 = default).");
        speed = new DoubleFieldBuilder("Speed", "Flight speed", 5.0, 1.0, 20.0, 0.5);
        addSetting(speed);
    }

    public void tick(MinecraftClient mc) {
        if (!isEnabled()) {
            return;
        }
        if (mc.player != null) {
            if (!mc.player.getAbilities().flying) {
                mc.player.getAbilities().flying = true;
            }
            mc.player.getAbilities().setFlySpeed((float) (speed.get() * 0.05));
        }
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && !mc.player.isCreative()) {
            mc.player.getAbilities().flying = false;
            mc.player.getAbilities().setFlySpeed(0.05f);
        }
    }
}
