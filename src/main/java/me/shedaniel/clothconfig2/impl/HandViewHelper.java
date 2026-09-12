package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;


public final class HandViewHelper {

    private static boolean wasSwinging;
    private static long lastSwingStartTime;

    private HandViewHelper() {}

    public static void applyViewModel(MatrixStack matrices, Hand hand, AbstractClientPlayerEntity player) {
        Config_ViewModel mod = Config_ViewModel.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        boolean main = hand == Hand.MAIN_HAND;
        Arm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean right = arm == Arm.RIGHT;

        float x = (float) mod.x();
        float y = (float) mod.y();
        float z = (float) mod.z();

        if (!right) {
            x = -x;
        }

        matrices.translate(x, y, z);
    }

    public static void applyViewModelScale(MatrixStack matrices) {
        Config_ViewModel mod = Config_ViewModel.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        float scale = (float) mod.scale();
        if (scale != 1f) {
            matrices.scale(scale, scale, scale);
        }
    }

    public static boolean shouldCustomSwing(AbstractClientPlayerEntity player, Hand hand) {
        return false;
    }

    
    public static boolean isSwingActive(AbstractClientPlayerEntity player, float swingProgress) {
        return player.handSwinging
                || player.handSwingTicks > 0
                || swingProgress > 0.001f;
    }

    public static boolean shouldApplyCustomSwing(
            AbstractClientPlayerEntity player,
            Hand hand,
            float swingProgress
    ) {
        if (!shouldCustomSwing(player, hand)) {
            return false;
        }
        return swingProgress > 0.0f;
    }

    public static void updateSwingState(AbstractClientPlayerEntity player, float swingProgress) {
        
    }

    
    public static void onOutboundHandSwing() {
        
    }

    public static float getIndependentSwingProgress() {
        return 0f;
    }

    
    public static void applyCustomSwing(
            MatrixStack matrices,
            Arm arm,
            float swingProgress,
            float equipProgress,
            AbstractClientPlayerEntity player) {
        applyViewModelScale(matrices);
    }

    public static void resetSwingSmooth() {
        lastSwingStartTime = 0;
        wasSwinging = false;
    }

    
    private static float shapedProgress(float swingProgress, float smoothness) {
        return swingProgress;
    }

    private static void applyPunchSwing(MatrixStack m, int side, float p, float s) {
        float f = (float) Math.sin(p * (Math.PI / 2) * 2.0);
        m.translate(0.55F * side, -0.5F, -(0.7F + f * 0.002 * s));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0F * f * s));
    }

    private static void applySlideSwing(MatrixStack m, int side, float p, float s) {
        float f = (float) Math.sin(p * (Math.PI / 2) * 2.0);
        m.translate(side * 0.75F, -0.35F, -1.0F);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 90));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * -60));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0F - s * 50.0F * f));
    }
}
