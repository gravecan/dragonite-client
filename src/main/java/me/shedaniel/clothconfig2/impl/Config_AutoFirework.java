package me.shedaniel.clothconfig2.impl;



import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;

import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;



import java.util.ArrayList;

import java.util.List;

import java.util.concurrent.ThreadLocalRandom;





public class Config_AutoFirework extends ConfigCategoryImpl {



    public static Config_AutoFirework INSTANCE;



    private final DoubleFieldBuilder delay;

    private final BooleanToggleBuilder rocketRefill;



    private long nextUseMs;

    private int usesSincePause;

    private long nextRefillMs;

    private final List<Long> recentIntervals = new ArrayList<>();

    private final ThreadLocalRandom rng = ThreadLocalRandom.current();



    public Config_AutoFirework() {

        super("AutoFirework", "Elytra firework boost — no hotbar slot change", Cat.MOVEMENT);

        INSTANCE = this;

        setTooltip("Only boosts while gliding. Glide yourself (jump in air) — rockets in any hotbar slot.");

        delay = new DoubleFieldBuilder(

                "Delay", "Base ms between rocket uses", 520, 100, 2000, 5);

        rocketRefill = new BooleanToggleBuilder(
                "Rocket Refill", "SWAP rockets from inventory to hotbar when empty (no GUI)", false);

        addSetting(delay);

        addSetting(rocketRefill);

    }



    @Override

    public void onEnable() {

        nextUseMs = 0;

        usesSincePause = 0;

        recentIntervals.clear();

        nextRefillMs = 0;

    }



    public void onMovementTick(MinecraftClient mc) {

        if (!isEnabled() || mc.player == null || mc.world == null || mc.currentScreen != null) {

            return;

        }

        if (mc.player.isUsingItem()) {

            return;

        }

        if (!mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {

            return;

        }

        if (!mc.player.isFallFlying()) {

            return;

        }



        int rocketSlot = findRocketHotbar(mc);

        if (rocketSlot < 0) {

            tryRocketRefill(mc);

            rocketSlot = findRocketHotbar(mc);

            if (rocketSlot < 0) {

                return;

            }

        }



        long now = System.currentTimeMillis();

        if (now < nextUseMs) {

            return;

        }



        if (shouldSkipMomentum(mc)) {

            nextUseMs = now + jitterMs(120, 280);

            return;

        }



        FireworkSilentUse.use(mc, rocketSlot);

        nextUseMs = now + nextIntervalMs();

        usesSincePause++;

    }



    private void tryRocketRefill(MinecraftClient mc) {
        if (!rocketRefill.get() || mc.interactionManager == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextRefillMs) {
            return;
        }

        Integer invSlot = findRocketInventory(mc);
        if (invSlot == null) {
            return;
        }

        int hotbarButton = InventoryHelper.firstEmptyHotbarSlot(mc);
        if (hotbarButton < 0) {
            hotbarButton = mc.player.getInventory().selectedSlot;
        }

        if (HotbarSilentUse.swapInventoryToHotbar(mc, invSlot, hotbarButton)) {
            nextRefillMs = now + 120;
        }
    }



    private boolean shouldSkipMomentum(MinecraftClient mc) {

        Vec3d vel = mc.player.getVelocity();

        double horiz = Math.hypot(vel.x, vel.z);

        if (horiz < 0.72) {

            return false;

        }

        float skipChance = horiz > 1.35 ? 0.72f : horiz > 1.0 ? 0.52f : 0.28f;

        return rng.nextFloat() < skipChance;

    }



    private long nextIntervalMs() {

        long base = (long) delay.get();

        long minGap = Math.max(100L, base - 40L);

        long jittered = base + rng.nextLong(140) - 55;



        if (recentIntervals.size() >= 4) {

            long avg = (long) recentIntervals.stream().mapToLong(l -> l).average().orElse(jittered);

            if (Math.abs(jittered - avg) < 45) {

                jittered += rng.nextLong(160) - 70;

            }

        }

        recentIntervals.add(jittered);

        if (recentIntervals.size() > 8) {

            recentIntervals.remove(0);

        }



        if (usesSincePause >= 5 + rng.nextInt(4)) {

            usesSincePause = 0;

            jittered += 180 + rng.nextLong(420);

        }



        if (rng.nextFloat() < 0.12f) {

            jittered += 90 + rng.nextLong(220);

        }



        return Math.max(minGap, jittered);

    }



    private static long jitterMs(int min, int max) {

        return min + ThreadLocalRandom.current().nextInt(Math.max(1, max - min + 1));

    }



    private static int findRocketHotbar(MinecraftClient mc) {

        for (int i = 0; i < 9; i++) {

            if (mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) {

                return i;

            }

        }

        return -1;

    }



    private static Integer findRocketInventory(MinecraftClient mc) {

        PlayerInventory inv = mc.player.getInventory();

        for (int i = 9; i <= 35; i++) {

            if (inv.getStack(i).isOf(Items.FIREWORK_ROCKET)) {

                return i;

            }

        }

        return null;

    }

}


