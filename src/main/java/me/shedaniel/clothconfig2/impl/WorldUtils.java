package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;


public class WorldUtils {
    
    
    public static void hitEntity(Entity entity, boolean swingHand) {
        MinecraftClient mc = MinecraftClient.getInstance();
        
        mc.interactionManager.attackEntity(mc.player, entity);
        if (swingHand) {
            
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }
}
