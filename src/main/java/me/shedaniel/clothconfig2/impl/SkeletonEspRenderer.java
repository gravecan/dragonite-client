package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;


public final class SkeletonEspRenderer {

    private SkeletonEspRenderer() {}

    public static void drawPlayer(
            MatrixStack matrices,
            BufferBuilder buffer,
            PlayerEntity player,
            Vec3d camera,
            float tickDelta,
            float r, float g, float b, float a
    ) {
        double px = MathHelper.lerp(tickDelta, player.prevX, player.getX()) - camera.x;
        double py = MathHelper.lerp(tickDelta, player.prevY, player.getY()) - camera.y;
        double pz = MathHelper.lerp(tickDelta, player.prevZ, player.getZ()) - camera.z;

        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
        boolean sneak = player.isSneaking();
        boolean swim = player.isInSwimmingPose();
        boolean glide = player.isFallFlying();

        float foot = sneak ? 0.6f : 0.7f;
        float shoulder = sneak ? 1.05f : 1.4f;
        float head = shoulder + 0.25f;
        float forward = sneak ? 0.23f : 0.0f;

        matrices.push();
        matrices.translate(px, py, pz);
        if (swim) {
            matrices.translate(0.0, 0.35, 0.0);
        }
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw - 180.0f));
        if (swim || glide) {
            float pitch = MathHelper.lerp(tickDelta, player.prevPitch, player.getPitch());
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-(90.0f + pitch)));
            if (swim) {
                matrices.translate(0.0, -0.95, 0.0);
            }
        }

        float rightLeg = 0f;
        float leftLeg = 0f;
        float rightArm = 0f;
        float leftArm = 0f;

        
        if (player.isAlive() && !player.hasVehicle() && player.limbAnimator.isLimbMoving()) {
            float limbSpeed = Math.min(player.limbAnimator.getSpeed(tickDelta), 1.0f);
            if (limbSpeed > 0.01f) {
                float limp = player.limbAnimator.getPos(tickDelta) * 0.6662f;
                rightLeg = MathHelper.cos(limp) * 0.6f * limbSpeed;
                leftLeg = MathHelper.cos(limp + (float) Math.PI) * 0.6f * limbSpeed;
                rightArm = MathHelper.cos(limp + (float) Math.PI) * 0.55f * limbSpeed;
                leftArm = MathHelper.cos(limp) * 0.55f * limbSpeed;
            }
        }

        float swingProgress = player.getHandSwingProgress(tickDelta);
        if (swingProgress > 0.01f) {
            float swing = MathHelper.cos(swingProgress * (float) Math.PI) * 0.35f;
            rightArm += swing;
            leftArm -= swing;
        }

        lineLocal(matrices, buffer, 0, foot, forward, 0, shoulder, forward, r, g, b, a);
        lineLocal(matrices, buffer, -0.15f, foot, forward, 0.15f, foot, forward, r, g, b, a);
        lineLocal(matrices, buffer, -0.37f, shoulder - 0.05f, 0, 0.37f, shoulder - 0.05f, 0, r, g, b, a);
        lineLocal(matrices, buffer, 0, shoulder, forward, 0, head, forward, r, g, b, a);

        limb(matrices, buffer, 0.37f, shoulder - 0.05f, 0, rightArm, r, g, b, a);
        limb(matrices, buffer, -0.37f, shoulder - 0.05f, 0, leftArm, r, g, b, a);
        limb(matrices, buffer, 0.15f, foot, forward, rightLeg, r, g, b, a);
        limb(matrices, buffer, -0.15f, foot, forward, leftLeg, r, g, b, a);

        matrices.pop();
    }

    private static void limb(
            MatrixStack matrices,
            BufferBuilder buffer,
            float x, float y, float z,
            float angle,
            float r, float g, float b, float a
    ) {
        matrices.push();
        matrices.translate(x, y, z);
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(angle));
        lineLocal(matrices, buffer, 0, 0, 0, 0, -0.55f, 0, r, g, b, a);
        matrices.pop();
    }

    private static void lineLocal(
            MatrixStack matrices,
            BufferBuilder buffer,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float r, float g, float b, float a
    ) {
        WorldLineRender.line(matrices, buffer, x1, y1, z1, x2, y2, z2, r, g, b, a);
    }
}
