package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.RangeSliderBuilder;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.ThreadLocalRandom;


public class Config_ShiftTap extends ConfigCategoryImpl {

    public static Config_ShiftTap INSTANCE;

    private final DoubleFieldBuilder chance;
    private final RangeSliderBuilder holdTime;

    private long releaseAt = 0L;
    private boolean sneaking = false;

    public Config_ShiftTap() {
        super("Shift Tap", "Briefly sneaks on hit to reset sprint and improve knockback in fights.", Cat.MOVEMENT);
        INSTANCE = this;
        chance = new DoubleFieldBuilder("Chance", "Chance to shift-tap after hit %", 100, 0, 100, 1);
        chance.setSuffix("%");
        holdTime = new RangeSliderBuilder("Hold Time", "Sneak duration in ms", 25, 50, 10, 200, 1);
        holdTime.setSuffix("ms");
        addSetting(chance);
        addSetting(holdTime);
    }

    @Override
    public void onDisable() {
        releaseSneak();
    }

    public void onAttack() {
        if (!isEnabled()) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble(100.0) > chance.get()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) {
            return;
        }
        long lo = (long) Math.min(holdTime.getMinVal(), holdTime.getMaxVal());
        long hi = (long) Math.max(holdTime.getMinVal(), holdTime.getMaxVal());
        releaseAt = System.currentTimeMillis() + (lo >= hi ? lo : lo + ThreadLocalRandom.current().nextLong(hi - lo + 1));
        if (!sneaking) {
            mc.options.sneakKey.setPressed(true);
            sneaking = true;
        }
    }

    public void tick(MinecraftClient mc) {
        if (!isEnabled()) {
            releaseSneak();
            return;
        }
        if (mc.player == null || mc.options == null) {
            releaseSneak();
            return;
        }
        if (sneaking && System.currentTimeMillis() > releaseAt) {
            releaseSneak();
        }
    }

    private void releaseSneak() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (sneaking && mc.options != null) {
            mc.options.sneakKey.setPressed(false);
        }
        sneaking = false;
        releaseAt = 0L;
    }
}
