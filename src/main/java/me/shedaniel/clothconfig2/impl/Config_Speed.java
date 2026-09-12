package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class Config_Speed extends ConfigCategoryImpl {

    private static Config_Speed INSTANCE;

    private final EnumSelectorBuilder mode;
    private final DoubleFieldBuilder speedFactor;
    private final DoubleFieldBuilder collisionRadius;
    private final BooleanToggleBuilder onlyPlayers;
    private final BooleanToggleBuilder autoJump;

    public Config_Speed() {
        super("Speed", "Speeds up your movement using Vanilla or Anti-Cheat bypasses", Cat.MOVEMENT);
        INSTANCE = this;
        setEnabled(false);

        mode = new EnumSelectorBuilder("Mode", "Speed adjustment mode", "Vanilla", "Vanilla", "Strafe", "GrimCollision");
        speedFactor = new DoubleFieldBuilder("Speed", "Movement speed multiplier", 3.0, 1.0, 10.0, 0.1);
        collisionRadius = new DoubleFieldBuilder("Radius", "Grim collision check range", 1.0, 0.5, 2.0, 0.1);
        onlyPlayers = new BooleanToggleBuilder("Only Players", "Grim: target players only for collisions", true);
        autoJump = new BooleanToggleBuilder("Auto Jump", "Strafe: jump automatically on ground", true);

        addSetting(mode);
        addSetting(speedFactor);
        addSetting(collisionRadius);
        addSetting(onlyPlayers);
        addSetting(autoJump);

        collisionRadius.setVisibleWhen(() -> mode.get().equals("GrimCollision"));
        onlyPlayers.setVisibleWhen(() -> mode.get().equals("GrimCollision"));
        autoJump.setVisibleWhen(() -> mode.get().equals("Strafe"));
    }

    public static Config_Speed getInstance() {
        return INSTANCE;
    }

    public void tick(MinecraftClient client) {
        if (!isEnabled()) return;

        ClientPlayerEntity player = client.player;
        if (player == null || player.getAbilities().flying) return;

        String speedMode = mode.get();
        if (speedMode.equals("Vanilla")) {
            if (isMoving(player)) {
                double vel = speedFactor.get() / 3.0;
                double[] dir = calculateDirection(player, vel);
                player.setVelocity(dir[0], player.getVelocity().y, dir[1]);
            }
        } else if (speedMode.equals("Strafe")) {
            if (isMoving(player)) {
                if (player.isOnGround()) {
                    if (autoJump.get()) {
                        player.jump();
                    }
                } else {
                    double vel = speedFactor.get() * 0.12;
                    double[] dir = calculateDirection(player, vel);
                    player.setVelocity(dir[0], player.getVelocity().y, dir[1]);
                }
            }
        } else if (speedMode.equals("GrimCollision")) {
            if (isMoving(player)) {
                double[] boost = new double[]{0.0, 0.0};
                int collisions = 0;

                for (Entity entity : client.world.getEntities()) {
                    if (entity == null || entity == player) continue;
                    if (entity instanceof ArmorStandEntity) continue;
                    if (onlyPlayers.get() && !(entity instanceof PlayerEntity)) continue;
                    if (!(entity instanceof LivingEntity || entity instanceof BoatEntity)) continue;

                    double distSq = player.squaredDistanceTo(entity);
                    double radius = collisionRadius.get();
                    if (distSq <= radius * radius) {
                        if (player.getBoundingBox().expand(radius).intersects(entity.getBoundingBox())) {
                            collisions++;
                            double fadeFactor = Math.max(0.0, radius - Math.sqrt(distSq));
                            double[] push = calculateDirection(player, speedFactor.get() * 0.01 * fadeFactor);
                            boost[0] += push[0];
                            boost[1] += push[1];
                        }
                    }
                }

                if (collisions > 0) {
                    double limit = speedFactor.get() * 0.02;
                    double xBoost = MathHelper.clamp(boost[0], -limit, limit);
                    double zBoost = MathHelper.clamp(boost[1], -limit, limit);
                    player.addVelocity(xBoost, 0.0, zBoost);
                }
            }
        }
    }

    private boolean isMoving(ClientPlayerEntity player) {
        return player.input.hasForwardMovement() || player.input.getMovementInput().x != 0.0f || player.input.getMovementInput().y != 0.0f;
    }

    private double[] calculateDirection(ClientPlayerEntity player, double distance) {
        float forward = player.input.movementForward;
        float sideways = player.input.movementSideways;
        float yaw = player.getYaw();

        if (forward != 0.0f) {
            if (sideways > 0.0f) {
                yaw += (forward > 0.0f) ? -45.0f : 45.0f;
            } else if (sideways < 0.0f) {
                yaw += (forward > 0.0f) ? 45.0f : -45.0f;
            }
            sideways = 0.0f;
            forward = (forward > 0.0f) ? 1.0f : -1.0f;
        }

        double sinYaw = Math.sin(Math.toRadians(yaw + 90.0f));
        double cosYaw = Math.cos(Math.toRadians(yaw + 90.0f));
        double xMovement = forward * distance * cosYaw + sideways * distance * sinYaw;
        double zMovement = forward * distance * sinYaw - sideways * distance * cosYaw;

        return new double[]{xMovement, zMovement};
    }
}
