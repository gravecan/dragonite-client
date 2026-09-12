package me.shedaniel.clothconfig2.impl;



import com.google.common.collect.Streams;

import net.minecraft.client.MinecraftClient;

import net.minecraft.client.input.Input;

import net.minecraft.util.math.Box;

import net.minecraft.util.math.MathHelper;

import net.minecraft.util.shape.VoxelShape;



import java.util.stream.Stream;





public class Config_AutoParkour extends ConfigCategoryImpl {



    public static Config_AutoParkour INSTANCE;



    private static final double CHECK_HEIGHT = 0.0;

    private static final double EDGE_DISTANCE = 0.10;



    public Config_AutoParkour() {

        super("Auto Parkour", "Automatically jumps at block edges while moving forward.", Cat.MOVEMENT);

        INSTANCE = this;

    }



    public static void apply(Input input, MinecraftClient mc) {

        if (INSTANCE == null || !INSTANCE.isEnabled() || mc.player == null || mc.world == null) {

            return;

        }

        if (mc.currentScreen != null || !mc.player.isOnGround() || input == null) {

            return;

        }

        if (input.sneaking) {

            return;

        }



        float forward = input.movementForward;

        float sideways = input.movementSideways;

        if (forward == 0.0f && sideways == 0.0f) {

            return;

        }



        float yawRad = mc.player.getYaw() * MathHelper.RADIANS_PER_DEGREE;

        double sinYaw = MathHelper.sin(yawRad);

        double cosYaw = MathHelper.cos(yawRad);

        double moveX = -sinYaw * forward + cosYaw * sideways;

        double moveZ = cosYaw * forward + sinYaw * sideways;

        double moveLen = Math.sqrt(moveX * moveX + moveZ * moveZ);

        if (moveLen < 1.0E-4) {

            return;

        }

        moveX /= moveLen;

        moveZ /= moveLen;



        double lookX = -sinYaw;

        double lookZ = cosYaw;

        if (moveX * lookX + moveZ * lookZ <= 0.0) {

            return;

        }



        double speedX = mc.player.getX() - mc.player.prevX;
        double speedZ = mc.player.getZ() - mc.player.prevZ;
        double speed = Math.sqrt(speedX * speedX + speedZ * speedZ);

        double ahead = speed > 0.01 ? speed + 0.05 : 0.15;
        double edgeDist = 0.05;

        Box box = mc.player.getBoundingBox();
        Box shrunk = box
                .offset(moveX * ahead, 0.0, moveZ * ahead)
                .offset(0.0, -0.5, 0.0)
                .expand(-edgeDist, 0.0, -edgeDist);

        Box adjusted = new Box(
                shrunk.minX, shrunk.minY - CHECK_HEIGHT, shrunk.minZ,
                shrunk.maxX, shrunk.maxY, shrunk.maxZ);

        Stream<VoxelShape> collisions = Streams.stream(mc.world.getBlockCollisions(mc.player, adjusted));
        if (collisions.findAny().isEmpty()) {
            input.jumping = true;
        }

    }

}


