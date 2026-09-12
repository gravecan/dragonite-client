package me.shedaniel.clothconfig2.impl;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;


public final class MovementPacketHooks {

    private MovementPacketHooks() {}

    public static boolean anyMovementModuleActive() {
        return isNoFallActive()
                || isFreeCamActive()
                || isAirStuckActive();
    }

    public static boolean shouldIntercept(Packet<?> packet) {
        return packet instanceof PlayerMoveC2SPacket && anyMovementModuleActive();
    }

    public static boolean isNoFallActive() {
        return Config_NoFall.INSTANCE != null && Config_NoFall.INSTANCE.isEnabled();
    }

    public static boolean isFreeCamActive() {
        return Config_FreeCam.INSTANCE != null && Config_FreeCam.INSTANCE.isEnabled();
    }

    public static boolean isStasisActive() {
        return false;
    }

    public static boolean isStasisBurstActive() {
        return false;
    }

    public static boolean isAirStuckActive() {
        return Config_AirStuck.INSTANCE != null && Config_AirStuck.INSTANCE.isEnabled();
    }
}
