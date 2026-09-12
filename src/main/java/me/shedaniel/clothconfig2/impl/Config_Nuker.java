package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Config_Nuker extends ConfigCategoryImpl {
    public static Config_Nuker INSTANCE;

    private final DoubleFieldBuilder range;
    private final DoubleFieldBuilder blocksPerTick;
    private final BooleanToggleBuilder silentAim;
    private final BooleanToggleBuilder swing;
    private final BooleanToggleBuilder autoTool;

    public Config_Nuker() {
        super("Nuker", "Break blocks around you", Cat.MISC);
        INSTANCE = this;

        range = new DoubleFieldBuilder("Range", "", 4.5, 1.0, 6.0, 0.1);
        blocksPerTick = new DoubleFieldBuilder("Blocks / Tick", "", 1.0, 1.0, 8.0, 1.0);
        silentAim = new BooleanToggleBuilder("Silent Aim", "", true);
        swing = new BooleanToggleBuilder("Swing", "", true);
        autoTool = new BooleanToggleBuilder("Auto Tool", "", true);

        addSetting(range);
        addSetting(blocksPerTick);
        addSetting(silentAim);
        addSetting(swing);
        addSetting(autoTool);
    }

    @Override
    public void onDisable() {
        RotationController.INSTANCE.reset();
    }

    public void tick(MinecraftClient mc) {
        if (!isEnabled() || mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }
        if (mc.currentScreen != null) {
            return;
        }

        List<BlockPos> targets = getTargetBlocks(mc);
        if (targets.isEmpty()) {
            return;
        }

        int limit = Math.max(1, (int) blocksPerTick.get());
        int broken = 0;

        for (BlockPos pos : targets) {
            if (broken >= limit) {
                break;
            }
            if (mc.world.getBlockState(pos).isAir()) {
                continue;
            }

            if (silentAim.get()) {
                float[] rot = rotationsTo(mc, pos);
                RotationController.INSTANCE.setTargetRotation(rot[0], rot[1], false, true, 1);
            }

            if (autoTool.get()) {
                selectBestTool(mc, pos);
            }

            Direction face = pickFace(mc, pos);
            boolean mining = mc.interactionManager.updateBlockBreakingProgress(pos, face);
            if (mining) {
                mc.player.swingHand(Hand.MAIN_HAND);
            } else if (swing.get()) {
                mc.interactionManager.attackBlock(pos, face);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
            broken++;
        }
    }

    private Direction pickFace(MinecraftClient mc, BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        Vec3d delta = center.subtract(eye);
        return Direction.getFacing(
                MathHelper.clamp((int) Math.round(delta.x), -1, 1),
                MathHelper.clamp((int) Math.round(delta.y), -1, 1),
                MathHelper.clamp((int) Math.round(delta.z), -1, 1)
        );
    }

    private List<BlockPos> getTargetBlocks(MinecraftClient mc) {
        List<BlockPos> blocks = new ArrayList<>();
        double r = range.get();
        BlockPos origin = mc.player.getBlockPos();
        int ri = (int) Math.ceil(r);

        for (int x = -ri; x <= ri; x++) {
            for (int y = -ri; y <= ri; y++) {
                for (int z = -ri; z <= ri; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    if (mc.world.getBlockState(pos).isAir()) {
                        continue;
                    }
                    if (mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos)) > r) {
                        continue;
                    }
                    blocks.add(pos);
                }
            }
        }

        blocks.sort(Comparator.comparingDouble(p -> mc.player.getEyePos().distanceTo(Vec3d.ofCenter(p))));
        return blocks;
    }

    private void selectBestTool(MinecraftClient mc, BlockPos pos) {
        var state = mc.world.getBlockState(pos);
        float bestSpeed = 1.0f;
        int bestSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }
        if (bestSlot != -1) {
            mc.player.getInventory().selectedSlot = bestSlot;
        }
    }

    private float[] rotationsTo(MinecraftClient mc, BlockPos pos) {
        Vec3d center = Vec3d.ofCenter(pos);
        double dX = center.x - mc.player.getX();
        double dY = center.y - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double dZ = center.z - mc.player.getZ();
        double dist = Math.sqrt(dX * dX + dZ * dZ);
        float yaw = (float) (Math.atan2(dZ, dX) * 180 / Math.PI) - 90.0f;
        float pitch = (float) (-(Math.atan2(dY, dist) * 180 / Math.PI));
        return new float[]{yaw, pitch};
    }
}
