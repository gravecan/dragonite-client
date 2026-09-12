package me.shedaniel.math.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;


public class HitboxHelper {

    
    public static void resetAllBoundingBoxes() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity player)) continue;
            if (entity == mc.player) continue;

            float hw = player.getWidth() / 2.0f;
            player.setBoundingBox(new Box(
                player.getX() - hw,
                player.getY(),
                player.getZ() - hw,
                player.getX() + hw,
                player.getY() + player.getHeight(),
                player.getZ() + hw
            ));
        }
    }
}
