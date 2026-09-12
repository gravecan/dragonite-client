package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;


public class Config_BoatFly extends ConfigCategoryImpl {

    public static Config_BoatFly INSTANCE;

    private static final double BLOCK_SPEED_MUL = 0.42;
    private static final double CHECK_DISTANCE = 0.5;

    private final DoubleFieldBuilder speed;
    private final BooleanToggleBuilder noclip;

    private boolean useKeyWasDown;

    public Config_BoatFly() {
        super("Boat Fly", "Fly while riding a boat. WASD to move, jump up, shift down.", Cat.MOVEMENT);
        INSTANCE = this;
        setTooltip("WASD move. Jump up, shift down. Use items always on.");
        speed = new DoubleFieldBuilder("Speed", "Movement speed", 0.6, 0.05, 5.0, 0.05);
        noclip = new BooleanToggleBuilder("NoClip", "Pass through blocks", true);
        addSetting(speed);
        addSetting(noclip);
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.player.getVehicle() instanceof BoatEntity boat) {
            resetBoat(boat);
        }
        useKeyWasDown = false;
    }

    public void tick(MinecraftClient mc) {
        if (!isEnabled() || mc.player == null || mc.world == null) {
            return;
        }
        if (!(mc.player.getVehicle() instanceof BoatEntity boat)) {
            useKeyWasDown = false;
            return;
        }

        tickUseItems(mc);

        boat.noClip = noclip.get();
        boat.setNoGravity(true);
        boat.setYaw(mc.player.getYaw());
        boat.prevYaw = boat.getYaw();

        double moveSpeed = speed.get();
        if (insideSolid(mc, mc.player.getBoundingBox().expand(0.001)) || pathBlocked(mc, boat)) {
            moveSpeed *= BLOCK_SPEED_MUL;
        }

        float yawRad = boat.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        double sinYaw = MathHelper.sin(yawRad);
        double cosYaw = MathHelper.cos(yawRad);
        double vx = 0.0;
        double vz = 0.0;

        if (mc.player.input != null) {
            float forward = mc.player.input.movementForward;
            float sideways = mc.player.input.movementSideways;
            if (forward != 0.0f) {
                vx -= sinYaw * moveSpeed * Math.signum(forward);
                vz += cosYaw * moveSpeed * Math.signum(forward);
            }
            if (sideways != 0.0f) {
                vx += cosYaw * moveSpeed * Math.signum(sideways);
                vz += sinYaw * moveSpeed * Math.signum(sideways);
            }
        }

        double vert = moveSpeed * 0.66;
        double vy = 0.0;
        if (mc.options.jumpKey.isPressed()) {
            vy += vert;
        }
        if (mc.options.sneakKey.isPressed()) {
            vy -= vert;
            mc.player.setSneaking(false);
            if (mc.player.input != null) {
                mc.player.input.sneaking = false;
            }
        }

        boat.setVelocity(vx, vy, vz);
        boat.fallDistance = 0.0f;

        if (!mc.player.hasVehicle()) {
            mc.player.startRiding(boat, true);
        }
    }

    private void tickUseItems(MinecraftClient mc) {
        boolean useDown = mc.options.useKey.isPressed();
        if (useDown && !useKeyWasDown) {
            if (!mc.player.getMainHandStack().isEmpty()) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            } else if (!mc.player.getOffHandStack().isEmpty()) {
                mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            }
        }
        if (!useDown && useKeyWasDown && mc.player.isUsingItem()) {
            mc.player.stopUsingItem();
        }
        useKeyWasDown = useDown;
    }

    public boolean isBlockingShiftDismount() {
        return isEnabled();
    }

    public static net.minecraft.network.packet.Packet<?> sanitizeOutboundPacket(net.minecraft.network.packet.Packet<?> packet) {
        Config_BoatFly fly = INSTANCE;
        if (fly == null || !fly.isEnabled()) {
            return packet;
        }
        if (!(packet instanceof net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket input)) {
            return packet;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof BoatEntity)) {
            return packet;
        }
        if (!input.isSneaking()) {
            return packet;
        }
        return new net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket(
                input.getSideways(), input.getForward(), input.isJumping(), false);
    }

    private static void resetBoat(BoatEntity boat) {
        boat.noClip = false;
        boat.setNoGravity(false);
        boat.setVelocity(Vec3d.ZERO);
    }

    private static boolean insideSolid(MinecraftClient mc, Box box) {
        int minX = MathHelper.floor(box.minX);
        int minY = MathHelper.floor(box.minY);
        int minZ = MathHelper.floor(box.minZ);
        int maxX = MathHelper.floor(box.maxX);
        int maxY = MathHelper.floor(box.maxY);
        int maxZ = MathHelper.floor(box.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockState state = mc.world.getBlockState(new BlockPos(x, y, z));
                    if (state.blocksMovement()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean pathBlocked(MinecraftClient mc, BoatEntity boat) {
        if (mc.player == null || mc.player.input == null) {
            return false;
        }
        Vec3d pos = boat.getPos();
        float yawRad = boat.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        double sinYaw = MathHelper.sin(yawRad);
        double cosYaw = MathHelper.cos(yawRad);
        double dist = CHECK_DISTANCE;
        double sampleX = pos.x;
        double sampleY = pos.y;
        double sampleZ = pos.z;

        float forward = mc.player.input.movementForward;
        float sideways = mc.player.input.movementSideways;
        if (forward != 0.0f) {
            sampleX -= sinYaw * dist * Math.signum(forward);
            sampleZ += cosYaw * dist * Math.signum(forward);
        }
        if (sideways != 0.0f) {
            sampleX += cosYaw * dist * Math.signum(sideways);
            sampleZ += sinYaw * dist * Math.signum(sideways);
        }
        if (mc.options.jumpKey.isPressed()) {
            sampleY += dist;
        }
        if (mc.options.sneakKey.isPressed()) {
            sampleY -= dist;
        }

        Box sample = new Box(sampleX - 0.5, sampleY - 0.5, sampleZ - 0.5,
                sampleX + 0.5, sampleY + 0.5, sampleZ + 0.5);
        return insideSolid(mc, sample);
    }
}
