package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;


public final class RotationBreakGuard {
    private RotationBreakGuard() {}

    public static boolean shouldCancelBlockBreak(MinecraftClient mc, BlockPos pos) {
        return false;
    }

    public static void cancelActiveDig(MinecraftClient mc) {
        if (mc == null || mc.interactionManager == null) {
            return;
        }
        if (mc.interactionManager.isBreakingBlock()) {
            mc.interactionManager.cancelBlockBreaking();
        }
    }

    private static boolean rayHitsBlock(MinecraftClient mc, BlockPos pos) {
        float yaw = packetYaw(mc);
        float pitch = packetPitch(mc);
        double reach = mc.player.getBlockInteractionRange();
        Vec3d eyes = mc.player.getEyePos();
        Vec3d look = rotationToVector(yaw, pitch);
        Vec3d end = eyes.add(look.multiply(reach));
        BlockHitResult hit = mc.world.raycast(new RaycastContext(
                eyes,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        return hit.getBlockPos().equals(pos);
    }

    private static float packetYaw(MinecraftClient mc) {
        return mc.player.getYaw();
    }

    private static float packetPitch(MinecraftClient mc) {
        return mc.player.getPitch();
    }

    private static Vec3d rotationToVector(float yaw, float pitch) {
        float pitchRad = pitch * MathHelper.RADIANS_PER_DEGREE;
        float yawRad = -yaw * MathHelper.RADIANS_PER_DEGREE;
        float h = MathHelper.cos(pitchRad);
        return new Vec3d(
                MathHelper.sin(yawRad) * h,
                -MathHelper.sin(pitchRad),
                MathHelper.cos(yawRad) * h
        );
    }
}
