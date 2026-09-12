package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.RangeSliderBuilder;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;


public class Config_WTap extends ConfigCategoryImpl {

    public static Config_WTap INSTANCE;

    private final DoubleFieldBuilder chance;
    private final RangeSliderBuilder holdTime;

    private long releaseForwardAt = 0L;
    private boolean holdingRelease;

    public Config_WTap() {
        super("WTap", "Briefly releases W after a hit while sprinting to reset knockback and keep combo speed.", Cat.MOVEMENT);
        INSTANCE = this;
        chance = new DoubleFieldBuilder("Chance", "Chance to W-tap after hit %", 100, 0, 100, 1);
        chance.setSuffix("%");
        holdTime = new RangeSliderBuilder("Hold Time", "Ms forward released", 100, 200, 10, 200, 1);
        addSetting(chance);
        addSetting(holdTime);
    }

    @Override
    public void onDisable() {
        resetKeys();
    }

    public void onAttack() {
        if (!isEnabled()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) {
            return;
        }
        if (!mc.options.forwardKey.isPressed()) {
            return;
        }
        if (!mc.player.isSprinting()) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble(100.0) > chance.get()) {
            return;
        }
        RotationBreakGuard.cancelActiveDig(mc);
        mc.options.forwardKey.setPressed(false);
        releaseForwardAt = System.currentTimeMillis() + randomMs(holdTime.getMinVal(), holdTime.getMaxVal());
        holdingRelease = true;
    }

    public void tick(MinecraftClient mc) {
        if (!isEnabled()) {
            resetKeys();
            return;
        }
        if (mc.player == null || mc.options == null) {
            resetKeys();
            return;
        }
        long handle = mc.getWindow().getHandle();
        if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_W) != GLFW.GLFW_PRESS) {
            resetState();
            return;
        }
        long now = System.currentTimeMillis();
        if (holdingRelease && now >= releaseForwardAt) {
            if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) {
                mc.options.forwardKey.setPressed(true);
            }
            holdingRelease = false;
        }
    }

    private static long randomMs(double min, double max) {
        double lo = Math.min(min, max);
        double hi = Math.max(min, max);
        if (hi <= lo) {
            return (long) lo;
        }
        return (long) (lo + ThreadLocalRandom.current().nextDouble(hi - lo));
    }

    private void resetState() {
        holdingRelease = false;
        releaseForwardAt = 0L;
    }

    private void resetKeys() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (holdingRelease && mc.options != null
                && GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) {
            mc.options.forwardKey.setPressed(true);
        }
        resetState();
    }
}
