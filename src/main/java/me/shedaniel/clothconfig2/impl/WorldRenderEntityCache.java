package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;

import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public final class WorldRenderEntityCache {

    private static final List<LivingEntity> LIVING = new ArrayList<>(128);

    private WorldRenderEntityCache() {}

    public static void rebuild(MinecraftClient mc, ConfigBuilderImpl manager) {
        LIVING.clear();
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (!anyModuleNeedsCache(manager)) {
            return;
        }

        float queryRange = resolveQueryRange(manager);
        double maxDistSq = (double) queryRange * queryRange;
        Box box = mc.player.getBoundingBox().expand(queryRange + 8.0);
        LIVING.addAll(mc.world.getEntitiesByClass(
                LivingEntity.class,
                box,
                e -> e != mc.player
                        && !e.isRemoved()
                        && e.isAlive()
                        && e.squaredDistanceTo(mc.player) <= maxDistSq
        ));

        
        Set<LivingEntity> seen = new HashSet<>(LIVING);
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isRemoved() || !player.isAlive()) {
                continue;
            }
            if (player.squaredDistanceTo(mc.player) <= maxDistSq && seen.add(player)) {
                LIVING.add(player);
            }
        }
    }

    private static boolean anyModuleNeedsCache(ConfigBuilderImpl manager) {
        if (manager == null) {
            return false;
        }
        ConfigCategoryImpl esp = manager.getModuleByClass(ConfigEntryImpl.class);
        if (esp != null && esp.isEnabled()) {
            return true;
        }
        ConfigCategoryImpl sk = manager.getModuleByClass(Config_SkeletonEsp.class);
        if (sk != null && sk.isEnabled()) {
            return true;
        }
        ConfigCategoryImpl hats = manager.getModuleByClass(RenderContext.class);
        if (hats != null && hats.isEnabled()) {
            return true;
        }
        return false;
    }

    public static List<LivingEntity> livingEntities() {
        return LIVING;
    }

    private static float resolveQueryRange(ConfigBuilderImpl manager) {
        float max = 64f;
        if (manager == null) {
            return max;
        }
        for (ConfigCategoryImpl mod : manager.getModules()) {
            if (!mod.isEnabled()) {
                continue;
            }
            if (mod instanceof ConfigEntryImpl esp) {
                max = Math.max(max, esp.queryRange());
            } else if (mod instanceof Config_Tracers tr) {
                max = Math.max(max, tr.queryRange());
            } else if (mod instanceof Config_SkeletonEsp sk) {
                max = Math.max(max, sk.maxRangeQuery());
            }
        }
        return Math.min(max, 256f);
    }
}
