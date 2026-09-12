package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.util.math.BlockPos;


public class Config_FastBridge extends ConfigCategoryImpl {

    public static Config_FastBridge INSTANCE;

    private final BooleanToggleBuilder autoPlace = new BooleanToggleBuilder("Auto Place", "Automatically places block on edge", true);
    private boolean bridging;

    public Config_FastBridge() {
        super("FastBridge", "Auto-sneaks at edges while holding blocks (godbridge assist)", Cat.MOVEMENT);
        INSTANCE = this;
        setTooltip("Holds sneak when the block under you is air — stops you falling off edges while bridging.");
        addSetting(autoPlace);
    }

    @Override
    public void onDisable() {
        releaseSneak();
    }

    public void tick(MinecraftClient mc) {
        if (!isEnabled() || mc.player == null || mc.world == null || mc.options == null) {
            releaseSneak();
            return;
        }
        boolean holdingBlock = mc.player.getMainHandStack().getItem() instanceof BlockItem
                || mc.player.getOffHandStack().getItem() instanceof BlockItem;
        if (!holdingBlock) {
            releaseSneak();
            return;
        }
        if (mc.player.getPitch() < 70f) {
            releaseSneak();
            return;
        }
        BlockPos under = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() - 1, mc.player.getZ());
        boolean overAir = mc.world.getBlockState(under).isReplaceable();
        if (overAir) {
            mc.options.sneakKey.setPressed(true);
            bridging = true;
            if (autoPlace.get() && mc.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult blockHit 
                    && blockHit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                MouseSimulation.mouseClickAsync(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT, 35);
            }
        } else {
            releaseSneak();
        }
    }

    private void releaseSneak() {
        if (!bridging) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) {
            mc.options.sneakKey.setPressed(false);
        }
        bridging = false;
    }
}
