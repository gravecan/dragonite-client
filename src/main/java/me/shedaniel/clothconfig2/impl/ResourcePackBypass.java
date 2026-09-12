package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;


public final class ResourcePackBypass {

    private ResourcePackBypass() {}

    public static void acceptAndLoad(MinecraftClient mc, ResourcePackSendS2CPacket packet) {
        if (mc == null || packet == null) {
            return;
        }
        var id = packet.id();
        new Thread(() -> {
            try {
                // Wait slightly for network connection setup
                Thread.sleep(50);
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(
                            new ResourcePackStatusC2SPacket(id, ResourcePackStatusC2SPacket.Status.ACCEPTED));
                }
                Thread.sleep(250);
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(
                            new ResourcePackStatusC2SPacket(id, ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED));
                }
            } catch (Exception ignored) {
            }
        }).start();
    }
}
