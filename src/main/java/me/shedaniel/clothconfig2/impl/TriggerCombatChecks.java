package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;


public final class TriggerCombatChecks {
    
    private static final double ELYTRA_REACH = 2.72;
    
    private static final double REACH_VELOCITY_MARGIN = 0.12;

    private TriggerCombatChecks() {}

    public static boolean shouldSkipTriggerAttack(MinecraftClient mc) {
        if (mc.player == null) {
            return true;
        }
        if (CombatMovementSync.shouldSkipAuraStrike(mc)) {
            return true;
        }
        if (CombatMovementSync.shouldDelayAuraHit()) {
            return true;
        }
        if (mc.player.isSprinting() || mc.player.isSneaking()) {
            return true;
        }
        if (mc.player.isFallFlying()) {
            return true;
        }
        double hSpeed = mc.player.getVelocity().horizontalLength();
        if (hSpeed > 0.32) {
            return true;
        }
        if (!mc.player.isOnGround() && (hSpeed > 0.18 || Math.abs(mc.player.getVelocity().y) > 0.35)) {
            return true;
        }
        return false;
    }

    public static double getGrimMeleeReach(MinecraftClient mc) {
        if (mc.player == null) {
            return 2.95;
        }
        double limit = 2.95;
        if (mc.player.isFallFlying()) {
            limit = ELYTRA_REACH;
        }
        limit -= mc.player.getVelocity().horizontalLength() * REACH_VELOCITY_MARGIN;
        return Math.max(2.5, limit);
    }

    public static boolean isWithinGrimMeleeReach(MinecraftClient mc, Entity target) {
        if (mc.player == null || target == null) {
            return false;
        }
        Box box = target.getBoundingBox();
        Vec3d eye = mc.player.getEyePos();
        Vec3d closest = new Vec3d(
                MathHelper.clamp(eye.x, box.minX, box.maxX),
                MathHelper.clamp(eye.y, box.minY, box.maxY),
                MathHelper.clamp(eye.z, box.minZ, box.maxZ));
        double limit = getGrimMeleeReach(mc);
        return eye.squaredDistanceTo(closest) <= limit * limit;
    }

    public static boolean canMeleeHitWithLook(MinecraftClient mc, Entity target) {
        if (mc.player == null || target == null) {
            return false;
        }
        if (!isWithinGrimMeleeReach(mc, target)) {
            return false;
        }
        if (mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() == target) {
            return true;
        }
        double reach = getGrimMeleeReach(mc);
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0F);
        HitResult hit = mc.world.raycast(new RaycastContext(
                eye,
                eye.add(look.multiply(reach)),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player));
        return hit instanceof EntityHitResult entityHit && entityHit.getEntity() == target;
    }
}
