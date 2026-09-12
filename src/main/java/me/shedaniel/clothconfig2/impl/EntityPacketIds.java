package me.shedaniel.clothconfig2.impl;

import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;

import java.lang.reflect.Field;

final class EntityPacketIds {

    private static Field positionEntityIdField;

    private EntityPacketIds() {}

    static int positionEntityId(EntityPositionS2CPacket packet) {
        try {
            if (positionEntityIdField == null) {
                for (String name : new String[]{"entityId", "id"}) {
                    try {
                        Field field = EntityPositionS2CPacket.class.getDeclaredField(name);
                        field.setAccessible(true);
                        positionEntityIdField = field;
                        break;
                    } catch (NoSuchFieldException ignored) {
                    }
                }
            }
            if (positionEntityIdField != null) {
                return positionEntityIdField.getInt(packet);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return -1;
    }
}
