package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import net.minecraft.client.MinecraftClient;

public class DistanceConfig extends ConfigCategoryImpl {
    private static DistanceConfig instance;
    private final DoubleFieldBuilder range = new DoubleFieldBuilder("Reach Distance", "Distance to hit entities", 3.0, 3.0, 7.0, 0.01);
    private final DoubleFieldBuilder chance = new DoubleFieldBuilder("Chance", "Hit chance percentage", 100.0, 1.0, 100.0, 1.0);

    public DistanceConfig() {
        super("Reach", "Extends how far you can hit entities and blocks.", Cat.COMBAT);
        addSetting(range);
        addSetting(chance);
        chance.setSuffix("%");
        instance = this;
    }

    public static DistanceConfig getInstance() {
        return instance;
    }

    public double getRange() {
        return range.get();
    }

    public double getChance() {
        return chance.get();
    }

    public void tick(net.minecraft.client.MinecraftClient mc) {
        if (mc.player == null) return;
        
        try {
            net.minecraft.entity.attribute.EntityAttributeInstance entityAttr = 
                mc.player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE);
            net.minecraft.entity.attribute.EntityAttributeInstance blockAttr = 
                mc.player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE);
            
            boolean active = isEnabled() && (Math.random() * 100.0 < chance.get());
            double targetRange = active ? range.get() : 3.0;
            double targetBlockRange = active ? range.get() : 4.5;
            
            if (entityAttr != null && entityAttr.getBaseValue() != targetRange) {
                entityAttr.setBaseValue(targetRange);
            }
            if (blockAttr != null && blockAttr.getBaseValue() != targetBlockRange) {
                blockAttr.setBaseValue(targetBlockRange);
            }
        } catch (Exception e) {
            
        }
    }
    
    @Override
    public void onDisable() {
        
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            try {
                var entityAttr = mc.player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE);
                var blockAttr = mc.player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE);
                if (entityAttr != null) entityAttr.setBaseValue(3.0);
                if (blockAttr != null) blockAttr.setBaseValue(4.5);
            } catch (Exception ignored) {}
        }
    }
}
