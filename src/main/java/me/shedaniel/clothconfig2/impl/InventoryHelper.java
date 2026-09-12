package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;


public final class InventoryHelper {

    private InventoryHelper() {}

    public static void setSelectedSlot(MinecraftClient mc, int slot) {
        if (mc.player == null || slot < 0 || slot > 8) {
            return;
        }
        HotbarSlotSync.sendIfChanged(mc, slot);
    }

    
    public static void setSelectedSlotClientOnly(MinecraftClient mc, int slot) {
        HotbarSlotSync.applyVisualSlot(mc, slot);
    }

    public static int findHotbarItem(MinecraftClient mc, net.minecraft.item.Item item) {
        if (mc.player == null) {
            return -1;
        }
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isOf(item)) {
                return i;
            }
        }
        return -1;
    }

    public static Integer findPotionSlot(MinecraftClient mc, int start, int endInclusive, boolean healthOnly) {
        if (mc.player == null) {
            return null;
        }
        PlayerInventory inv = mc.player.getInventory();
        int end = Math.min(endInclusive, inv.size() - 1);
        for (int i = Math.max(0, start); i <= end; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isOf(Items.AIR)) {
                continue;
            }
            if (!isSplashPotion(stack)) {
                continue;
            }
            if (healthOnly && !hasEffect(stack, StatusEffects.INSTANT_HEALTH)) {
                continue;
            }
            return i;
        }
        return null;
    }

    public static boolean isSplashPotion(ItemStack stack) {
        return stack.isOf(Items.SPLASH_POTION) || stack.isOf(Items.LINGERING_POTION);
    }

    public static boolean hasEffect(ItemStack stack, RegistryEntry<StatusEffect> effect) {
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) {
            return false;
        }
        for (StatusEffectInstance inst : contents.getEffects()) {
            if (inst.getEffectType() == effect) {
                return true;
            }
        }
        return false;
    }

    public static int firstEmptyHotbarSlot(MinecraftClient mc) {
        if (mc.player == null) {
            return -1;
        }
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.AIR)) {
                return i;
            }
        }
        return -1;
    }
}
