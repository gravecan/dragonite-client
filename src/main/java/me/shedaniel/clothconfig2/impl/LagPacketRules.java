package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;


public final class LagPacketRules {

    private LagPacketRules() {}

    public static boolean isMovement(Packet<?> packet) {
        return packet instanceof PlayerMoveC2SPacket || packet instanceof VehicleMoveC2SPacket;
    }

    
    public static boolean isCombat(Packet<?> packet) {
        return packet instanceof PlayerInteractEntityC2SPacket
                || packet instanceof HandSwingC2SPacket
                || packet instanceof PlayerActionC2SPacket
                || packet instanceof ClientCommandC2SPacket;
    }

    
    public static boolean isStasisPassthrough(Packet<?> packet) {
        return packet instanceof KeepAliveC2SPacket
                || packet instanceof ClickSlotC2SPacket
                || packet instanceof CloseHandledScreenC2SPacket
                || packet instanceof ChatMessageC2SPacket
                || packet instanceof CommandExecutionC2SPacket;
    }

    public static boolean shouldFlushFakeLag(Packet<?> packet) {
        if (isCombat(packet)) {
            return true;
        }
        return packet instanceof PlayerInteractBlockC2SPacket
                || packet instanceof PlayerInteractItemC2SPacket;
    }

    
    public static boolean isEntitySyncFor(Packet<?> packet, MinecraftClient mc, int entityId) {
        if (entityId < 0 || mc.world == null) {
            return false;
        }
        if (packet instanceof EntityVelocityUpdateS2CPacket velocity) {
            return velocity.getEntityId() == entityId;
        }
        if (packet instanceof EntityPositionS2CPacket position) {
            return EntityPacketIds.positionEntityId(position) == entityId;
        }
        if (packet instanceof EntityS2CPacket entityPacket) {
            Entity entity = entityPacket.getEntity(mc.world);
            return entity != null && entity.getId() == entityId;
        }
        return false;
    }

    
    public static boolean isSelfStasisInbound(Packet<?> packet, MinecraftClient mc) {
        if (mc.player == null) {
            return false;
        }
        if (packet instanceof PlayerPositionLookS2CPacket) {
            return true;
        }
        return isEntitySyncFor(packet, mc, mc.player.getId());
    }
}
