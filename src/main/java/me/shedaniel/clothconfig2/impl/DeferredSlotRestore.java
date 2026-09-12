package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;

/**
 * Packet-only hotbar restore + optional inventory SWAP-back.
 * Never touches {@code selectedSlot} on the client, so silent swaps stay invisible.
 * Always flushed from {@code sendMovementPackets} HEAD (pre-flying).
 */
public final class DeferredSlotRestore {

    private static int pendingSlot = -1;
    private static int ticksLeft = 0;

    /** Player inventory slot (9–35) to SWAP back with {@link #pendingInvHotbar}. */
    private static int pendingInvSlot = -1;
    private static int pendingInvHotbar = -1;
    private static int invTicksLeft = 0;

    private DeferredSlotRestore() {}

    public static void schedulePacketOnly(int slot, int delayTicks) {
        if (slot < 0 || slot > 8) {
            return;
        }
        pendingSlot = slot;
        ticksLeft = Math.max(1, delayTicks);
    }

    /**
     * After a silent inventory→hotbar borrow, SWAP the same pair again to put the item back.
     * Runs on the pre-flying tick path, same as slot restore.
     */
    public static void scheduleInventorySwapBack(int inventorySlot, int hotbarButton, int delayTicks) {
        if (inventorySlot < 9 || inventorySlot > 35) {
            return;
        }
        if (hotbarButton < 0 || hotbarButton > 8) {
            return;
        }
        pendingInvSlot = inventorySlot;
        pendingInvHotbar = hotbarButton;
        invTicksLeft = Math.max(1, delayTicks);
    }

    public static void cancel() {
        pendingSlot = -1;
        ticksLeft = 0;
        pendingInvSlot = -1;
        pendingInvHotbar = -1;
        invTicksLeft = 0;
    }

    public static boolean isPending() {
        return (pendingSlot >= 0 && ticksLeft > 0)
                || (pendingInvSlot >= 0 && invTicksLeft > 0);
    }

    /** Send UpdateSelectedSlot without changing the client's visible hotbar. */
    public static void switchPacketOnly(MinecraftClient mc, int slot) {
        if (mc == null || mc.player == null || mc.getNetworkHandler() == null) {
            return;
        }
        if (slot < 0 || slot > 8) {
            return;
        }
        cancel();
        HotbarSlotSync.sendPacketOnly(mc, slot);
    }

    public static void tick(MinecraftClient mc) {
        if (pendingSlot >= 0 && ticksLeft > 0) {
            ticksLeft--;
            if (ticksLeft == 0) {
                int slot = pendingSlot;
                pendingSlot = -1;
                if (mc != null && mc.player != null && mc.getNetworkHandler() != null) {
                    HotbarSlotSync.sendPacketOnly(mc, slot);
                }
            }
        }

        if (pendingInvSlot >= 0 && invTicksLeft > 0) {
            invTicksLeft--;
            if (invTicksLeft == 0) {
                int inv = pendingInvSlot;
                int hotbar = pendingInvHotbar;
                pendingInvSlot = -1;
                pendingInvHotbar = -1;
                if (mc != null && mc.player != null && mc.interactionManager != null) {
                    HotbarSilentUse.swapInventoryToHotbar(mc, inv, hotbar);
                }
            }
        }
    }
}
