package me.shedaniel.clothconfig2.impl.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;


public class WorldUtils {
    
    
    public static void hitEntity(Entity entity, boolean swingHand) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.interactionManager.attackEntity(mc.player, entity);
        if (swingHand) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }
    
    
    public static boolean isShieldFacingAway(PlayerEntity player) {
        if (!player.isBlocking()) return true;
        
        MinecraftClient mc = MinecraftClient.getInstance();
        double dx = mc.player.getX() - player.getX();
        double dz = mc.player.getZ() - player.getZ();
        double angle = Math.atan2(dz, dx) * 180.0 / Math.PI - 90.0;
        double yawDiff = Math.abs(normalizeAngle(angle - player.getYaw()));
        return yawDiff >= 90.0;
    }
    
    private static double normalizeAngle(double angle) {
        while (angle > 180.0) angle -= 360.0;
        while (angle < -180.0) angle += 360.0;
        return angle;
    }
}
