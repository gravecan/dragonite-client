package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;


public final class HotbarSlotSync {

    private static int lastSentSlot = -1;
    
    private static boolean suppressVisualResync;
    private static int visualOnlySlot = -1;

    private HotbarSlotSync() {}

    public static void noteSent(int slot) {
        if (slot >= 0 && slot <= 8) {
            lastSentSlot = slot;
            if (suppressVisualResync && slot != visualOnlySlot) {
                suppressVisualResync = false;
                visualOnlySlot = -1;
            }
        }
    }

    public static void reset() {
        lastSentSlot = -1;
        suppressVisualResync = false;
        visualOnlySlot = -1;
    }

    public static int getLastSentSlot() {
        return lastSentSlot;
    }

    
    public static void applyVisualSlot(MinecraftClient mc, int slot) {
        if (mc == null || mc.player == null || slot < 0 || slot > 8) {
            return;
        }
        mc.player.getInventory().selectedSlot = slot;
        visualOnlySlot = slot;
        suppressVisualResync = true;
    }

    
    public static boolean shouldBlockResyncPacket(int outboundSlot) {
        if (!suppressVisualResync || visualOnlySlot < 0) {
            return false;
        }
        if (outboundSlot == visualOnlySlot && outboundSlot != lastSentSlot) {
            return true;
        }
        suppressVisualResync = false;
        visualOnlySlot = -1;
        return false;
    }

    
    public static boolean sendIfChanged(MinecraftClient mc, int slot) {
        if (mc == null || mc.player == null || mc.getNetworkHandler() == null) {
            return false;
        }
        if (slot < 0 || slot > 8) {
            return false;
        }
        suppressVisualResync = false;
        visualOnlySlot = -1;
        if (slot == lastSentSlot) {
            mc.player.getInventory().selectedSlot = slot;
            return false;
        }
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        mc.player.getInventory().selectedSlot = slot;
        lastSentSlot = slot;
        return true;
    }

    /**
     * Server-only hotbar switch. Leaves the client's visible selected slot alone
     * so silent modules stay invisible.
     */
    public static boolean sendPacketOnly(MinecraftClient mc, int slot) {
        if (mc == null || mc.player == null || mc.getNetworkHandler() == null) {
            return false;
        }
        if (slot < 0 || slot > 8) {
            return false;
        }
        if (slot == lastSentSlot) {
            return false;
        }
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        lastSentSlot = slot;
        return true;
    }
}
