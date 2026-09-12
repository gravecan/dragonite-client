package me.shedaniel.clothconfig2.impl;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;


final class EspBoxScratch {
    double minX, minY, minZ, maxX, maxY, maxZ;

    void setLerpedWorld(Entity e, float tickDelta) {
        Vec3d pos = e.getLerpedPos(tickDelta);
        double halfW = e.getWidth() / 2.0;
        double h = e.getHeight();
        if (e instanceof net.minecraft.entity.player.PlayerEntity) {
            h += 0.04D;
        }
        minX = pos.x - halfW;
        maxX = pos.x + halfW;
        minY = pos.y;
        maxY = pos.y + h;
        minZ = pos.z - halfW;
        maxZ = pos.z + halfW;
    }

    void offsetCamera(Vec3d cam) {
        minX -= cam.x;
        minY -= cam.y;
        minZ -= cam.z;
        maxX -= cam.x;
        maxY -= cam.y;
        maxZ -= cam.z;
    }
}
