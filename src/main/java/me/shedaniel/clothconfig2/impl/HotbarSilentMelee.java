package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;


public final class HotbarSilentMelee {

    private static final int RESTORE_DELAY_TICKS = 2;

    private HotbarSilentMelee() {}

    public static boolean attackFromHotbar(MinecraftClient mc, LivingEntity target, int hotbarSlot, boolean sprintReset) {
        if (mc.player == null || mc.getNetworkHandler() == null || mc.interactionManager == null || target == null) {
            return false;
        }
        if (hotbarSlot < 0 || hotbarSlot > 8) {
            return false;
        }

        int serverSlot = HotbarSlotSync.getLastSentSlot();
        if (serverSlot < 0 || serverSlot > 8) {
            serverSlot = mc.player.getInventory().selectedSlot;
        }

        int selected = mc.player.getInventory().selectedSlot;
        boolean swapped = hotbarSlot != serverSlot;

        // Packet-only — never touch the client's visible selected slot.
        if (swapped) {
            DeferredSlotRestore.switchPacketOnly(mc, hotbarSlot);
        }

        CombatMovementSync.prepareFriendSprintReset(mc, sprintReset);

        mc.getNetworkHandler().sendPacket(
                PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.resetLastAttackedTicks();

        if (swapped) {
            DeferredSlotRestore.schedulePacketOnly(selected, RESTORE_DELAY_TICKS);
        }
        return true;
    }
}
