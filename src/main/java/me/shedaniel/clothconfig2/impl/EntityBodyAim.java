package me.shedaniel.clothconfig2.impl;

import net.minecraft.entity.Entity;


public final class EntityBodyAim {

    private EntityBodyAim() {}

    public static double worldY(Entity entity, String part, float tickDelta) {
        
        double baseY = entity.getLerpedPos(tickDelta).y;
        double h = entity.getHeight();
        double offset = switch (part == null ? "Chest" : part) {
            case "Head" -> h * 0.92;
            case "Legs" -> h * 0.32;
            case "Feet" -> h * 0.06;
            default -> h * 0.55; 
        };
        return baseY + offset;
    }
}
