package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;


public final class HotbarSilentUse {

    /** Ticks before packet-only restore (never same batch as the use). */
    private static final int RESTORE_DELAY_TICKS = 2;

    private HotbarSilentUse() {}

    public static boolean useItemFromHotbar(MinecraftClient mc, int hotbarSlot) {
        if (mc.player == null || mc.getNetworkHandler() == null || mc.interactionManager == null) {
            return false;
        }
        if (hotbarSlot < 0 || hotbarSlot > 8) {
            return false;
        }

        if (mc.player.isSprinting()) {
            mc.getNetworkHandler().sendPacket(
                    new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            mc.player.setSprinting(false);
        }

        int selected = mc.player.getInventory().selectedSlot;
        int serverSlot = HotbarSlotSync.getLastSentSlot();
        if (serverSlot < 0 || serverSlot > 8) {
            serverSlot = selected;
        }

        if (hotbarSlot == serverSlot) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            return true;
        }

        // Packet-only — client hotbar stays on the original item.
        DeferredSlotRestore.switchPacketOnly(mc, hotbarSlot);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        DeferredSlotRestore.schedulePacketOnly(selected, RESTORE_DELAY_TICKS);
        return true;
    }

    
    public static boolean swapInventoryToHotbar(MinecraftClient mc, int inventorySlot, int hotbarButton) {
        if (mc.player == null || mc.interactionManager == null) {
            return false;
        }
        if (inventorySlot < 9 || inventorySlot > 35) {
            return false;
        }
        if (hotbarButton < 0 || hotbarButton > 8) {
            return false;
        }
        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                inventorySlot,
                hotbarButton,
                SlotActionType.SWAP,
                mc.player);
        return true;
    }

    /**
     * Prefer an empty hotbar slot. If the hotbar is full, borrow slot 1 (index 0):
     * SWAP item ↔ inventory, use it, SWAP back. Avoids the currently selected slot
     * when possible so the held item does not flash.
     */
    public static int pickBorrowHotbarSlot(MinecraftClient mc) {
        if (mc.player == null) {
            return -1;
        }
        int empty = InventoryHelper.firstEmptyHotbarSlot(mc);
        if (empty >= 0) {
            return empty;
        }
        // Hotbar full → slot 1 (keybind 1 / index 0), unless that is selected.
        int selected = mc.player.getInventory().selectedSlot;
        if (selected != 0) {
            return 0;
        }
        return 1;
    }
}

