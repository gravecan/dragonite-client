package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public class Config_IntegerField extends ConfigCategoryImpl {

    private static final String MODE_FLY   = "Fly";
    private static final String MODE_BOOST = "Vulcan";

    private static final int DEFAULT_BOOST_INTERVAL = 10;

    private final EnumSelectorBuilder mode = new EnumSelectorBuilder(
            "Mode", "Flight mode", MODE_FLY, MODE_FLY, MODE_BOOST);

    private final DoubleFieldBuilder flySpeed = new DoubleFieldBuilder(
            "Speed", "Flight speed", 5.0, 0.5, 40.0, 0.5);

    private final DoubleFieldBuilder boostStrength = new DoubleFieldBuilder(
            "Strength", "Boost power", 1.5, 0.5, 10.0, 0.5);

    private final Random rng = new Random();
    private int boostTick = 0;
    private int tickCounter = 0;
    private boolean wasFlying = false;

    private int packetCounter = 0;
    private double lastY = 0;
    private int hoverTicks = 0;

    public Config_IntegerField() {
        super("Elytra Fly", "Fly with elytra using WASD", Cat.MOVEMENT);
        setTooltip("Fly: creative flight with anti-kick. Boost: directional boost.");
        addSetting(mode);
        addSetting(flySpeed);
        addSetting(boostStrength);

        flySpeed.setVisibleWhen(() -> mode.get().equals(MODE_FLY));
        boostStrength.setVisibleWhen(() -> mode.get().equals(MODE_BOOST));
    }

    @Override
    public void onEnable() {
        boostTick = 0;
        tickCounter = 0;
        wasFlying = false;
        packetCounter = 0;
        lastY = 0;
        hoverTicks = 0;
    }

    public void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (mc.player.getInventory().getStack(38).getItem() != Items.ELYTRA) return;

        tickCounter++;

        if (!mc.player.isFallFlying() && !mc.player.isOnGround()) {
            mc.player.startFallFlying();
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(
                    new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
            return;
        }
        if (!mc.player.isFallFlying()) return;

        maintainElytraState(mc);

        String m = mode.get();
        if (m.equals(MODE_FLY)) tickFly(mc);
        else if (m.equals(MODE_BOOST)) tickBoost(mc);
    }

    private void maintainElytraState(MinecraftClient mc) {
        if (mc.player.isFallFlying()) {
            wasFlying = true;
            Vec3d vel = mc.player.getVelocity();
            if (Math.abs(vel.x) > 0.01 || Math.abs(vel.z) > 0.01) {
                hoverTicks = 0;
            }
        }
    }

    private void tickFly(MinecraftClient mc) {
        double spd = flySpeed.get() * 0.22;
        float yaw = mc.player.getYaw();
        double dx = 0, dy = 0, dz = 0;

        boolean hasInput = false;

        if (mc.options.forwardKey.isPressed()) {
            dx += -Math.sin(Math.toRadians(yaw)) * spd;
            dz += Math.cos(Math.toRadians(yaw)) * spd;
            hasInput = true;
        }
        if (mc.options.backKey.isPressed()) {
            dx += Math.sin(Math.toRadians(yaw)) * spd * 0.7;
            dz += -Math.cos(Math.toRadians(yaw)) * spd * 0.7;
            hasInput = true;
        }
        if (mc.options.leftKey.isPressed()) {
            dx += Math.sin(Math.toRadians(yaw + 90)) * spd * 0.8;
            dz += -Math.cos(Math.toRadians(yaw + 90)) * spd * 0.8;
            hasInput = true;
        }
        if (mc.options.rightKey.isPressed()) {
            dx += Math.sin(Math.toRadians(yaw - 90)) * spd * 0.8;
            dz += -Math.cos(Math.toRadians(yaw - 90)) * spd * 0.8;
            hasInput = true;
        }
        if (mc.options.jumpKey.isPressed()) {
            dy += spd * 0.8;
            hasInput = true;
        }
        if (mc.options.sneakKey.isPressed()) {
            dy -= spd * 0.8;
            hasInput = true;
        }

        if (!hasInput) {
            hoverTicks++;
            dx = (rng.nextDouble() - 0.5) * 0.002;
            dy = (rng.nextDouble() - 0.5) * 0.001;
            dz = (rng.nextDouble() - 0.5) * 0.002;

            if (hoverTicks > 30 && mc.getNetworkHandler() != null) {
                dy = -0.032;
                hoverTicks = 0;
            }
        } else {
            hoverTicks = 0;
        }

        dx *= (0.97 + rng.nextDouble() * 0.06);
        dy *= (0.97 + rng.nextDouble() * 0.06);
        dz *= (0.97 + rng.nextDouble() * 0.06);

        mc.player.setVelocity(dx, dy, dz);
        mc.player.fallDistance = 0;

        sendBypassPackets(mc);
    }

    private void sendBypassPackets(MinecraftClient mc) {
        if (mc.getNetworkHandler() == null) return;

        packetCounter++;
        if (packetCounter >= 20) {
            packetCounter = 0;

            double currentY = mc.player.getY();
            if (Math.abs(currentY - lastY) < 0.1 && hoverTicks > 20) {
                mc.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.Full(
                        mc.player.getX(),
                        mc.player.getY() - 0.1,
                        mc.player.getZ(),
                        mc.player.getYaw(),
                        mc.player.getPitch(),
                        false
                    ));
            }

            lastY = currentY;
        }
    }

    private void tickBoost(MinecraftClient mc) {
        boostTick++;
        if (boostTick < DEFAULT_BOOST_INTERVAL) return;
        boostTick = 0;

        double amt = boostStrength.get() * 0.05 * (0.9 + rng.nextDouble() * 0.2);
        Vec3d look = mc.player.getRotationVector();
        mc.player.addVelocity(look.x * amt, look.y * amt * 0.4, look.z * amt);

        if (mc.options.jumpKey.isPressed()) {
            mc.player.addVelocity(0, amt * 0.3, 0);
        }
        mc.player.fallDistance = 0;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        boostTick = 0;
        tickCounter = 0;
        wasFlying = false;
        packetCounter = 0;
        hoverTicks = 0;
    }
}
