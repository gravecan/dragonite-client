package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;


public final class LagCombatHelper {

    private LagCombatHelper() {}

    public static PlayerEntity nearestEnemy(MinecraftClient mc, double range, boolean playersOnly, boolean skipFriends) {
        if (mc.player == null || mc.world == null) {
            return null;
        }
        double maxSq = range * range;
        PlayerEntity best = null;
        double bestSq = maxSq;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator()) {
                continue;
            }
            if (playersOnly && !(player instanceof PlayerEntity)) {
                continue;
            }
            if (skipFriends && FriendManager.isCombatExempt(player)) {
                continue;
            }
            double sq = mc.player.squaredDistanceTo(player);
            if (sq <= bestSq) {
                bestSq = sq;
                best = player;
            }
        }
        return best;
    }

    public static boolean hasEnemyWithin(MinecraftClient mc, double range) {
        return nearestEnemy(mc, range, true, true) != null;
    }

    public static PlayerEntity crosshairPlayer(MinecraftClient mc, double maxRange) {
        if (mc.player == null || mc.crosshairTarget == null) {
            return null;
        }
        if (mc.crosshairTarget.getType() != HitResult.Type.ENTITY) {
            return null;
        }
        if (!(mc.crosshairTarget instanceof EntityHitResult hit)) {
            return null;
        }
        if (!(hit.getEntity() instanceof PlayerEntity player) || player == mc.player || !player.isAlive()) {
            return null;
        }
        if (FriendManager.isCombatExempt(player)) {
            return null;
        }
        if (mc.player.squaredDistanceTo(player) > maxRange * maxRange) {
            return null;
        }
        return player;
    }
}
