package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import net.minecraft.client.MinecraftClient;


public class MovementConfig extends ConfigCategoryImpl {

    public static MovementConfig INSTANCE;

    private final EnumSelectorBuilder mode;

    public MovementConfig() {
        super("Sprint", "Normal = auto sprint while moving. Keep Sprint = no slowdown after attacking.", Cat.MOVEMENT);
        INSTANCE = this;
        mode = new EnumSelectorBuilder("Mode", "Normal = auto sprint | Keep Sprint = no attack slowdown", "Normal", "Normal", "Keep Sprint");
        addSetting(mode);
        setTooltip("Normal: omni sprint while moving. Keep Sprint: full speed after hits (motion 1).");
    }

    public boolean isKeepSprintMode() {
        return "Keep Sprint".equals(mode.get());
    }

    public void tick(MinecraftClient mc) {
        if (!isEnabled() || isKeepSprintMode() || mc.player == null) {
            return;
        }
        if (CombatMovementSync.shouldSuppressAutoSprint()) {
            return;
        }
        if (mc.player.isSprinting()) {
            return;
        }
        float forward = mc.player.forwardSpeed;
        float sideways = mc.player.sidewaysSpeed;
        boolean moving = forward > 0 || sideways != 0;
        if (!moving) {
            return;
        }
        if (!mc.player.isSneaking() && !mc.player.isTouchingWater()) {
            mc.player.setSprinting(true);
        }
    }

    public static void onAfterAttackSlow(MinecraftClient mc) {
        if (INSTANCE == null || !INSTANCE.isEnabled() || !INSTANCE.isKeepSprintMode() || mc.player == null) {
            return;
        }
        var vel = mc.player.getVelocity();
        mc.player.setVelocity(vel.x / 0.6, vel.y, vel.z / 0.6);
        mc.player.setSprinting(true);
    }

    public boolean shouldSprint() {
        return isEnabled() && !isKeepSprintMode();
    }
}
