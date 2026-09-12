package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class ConfigValueHandler extends ConfigCategoryImpl {

    private final BooleanToggleBuilder prediction = new BooleanToggleBuilder("Prediction", "Predict target position", true);
    private final DoubleFieldBuilder predictTicks = new DoubleFieldBuilder("Predict Ticks", "How many ticks ahead", 1, 0.5, 3, 0.5);
    private final DoubleFieldBuilder aimSpeed = new DoubleFieldBuilder("Aim Speed", "Smooth aim speed", 2.0, 0.5, 5.0, 0.5);
    private final DoubleFieldBuilder aimDistance = new DoubleFieldBuilder("Aim Distance", "Range to target", 5.0, 1.0, 8.0, 0.5);

    
    private final BooleanToggleBuilder overtake = new BooleanToggleBuilder("Overtake", "Lead target for Aura (Rich elytra aim)", true);
    private final DoubleFieldBuilder elytraForward = new DoubleFieldBuilder("Lead Ticks", "Velocity lead for Aura", 3, 0, 6, 0.5);
    private final DoubleFieldBuilder elytraFindRange = new DoubleFieldBuilder("Aura Scan+", "Extra Aura search range while flying", 32, 6, 64, 1);

    private static ConfigValueHandler INSTANCE;

    private Entity currentTarget = null;
    private long lastFrameTime = System.currentTimeMillis();
    private static final float TARGET_FPS = 144f;
    private boolean glidePacketSent;

    private long lastDamageTime = 0;

    public ConfigValueHandler() {
        super("ElytraTarget", "Smooth aim + Aura lead while elytra flying", Cat.MOVEMENT);
        INSTANCE = this;
        setTooltip("Use AutoFirework for rocket boost. This module handles aim/overtake only.");
        addSetting(prediction);
        addSetting(predictTicks);
        addSetting(aimSpeed);
        addSetting(aimDistance);
        addSetting(overtake);
        addSetting(elytraForward);
        addSetting(elytraFindRange);

        elytraForward.setVisibleWhen(overtake::get);
        elytraFindRange.setVisibleWhen(overtake::get);
        predictTicks.setVisibleWhen(() -> prediction.get());
        aimSpeed.setVisibleWhen(() -> prediction.get());
        aimDistance.setVisibleWhen(() -> prediction.get());
    }

    public static ConfigValueHandler getInstance() {
        return INSTANCE;
    }

    public boolean isOvertakeActive() {
        return isEnabled() && overtake.get();
    }

    public float getElytraForward() {
        return (float) elytraForward.get();
    }

    public float getElytraFindRange() {
        return (float) elytraFindRange.get();
    }

    @Override
    public void onEnable() {
        currentTarget = null;
        glidePacketSent = false;
    }

    @Override
    public void onDisable() {
        currentTarget = null;
        glidePacketSent = false;
    }

    public void onPlayerDamage() {
        lastDamageTime = System.currentTimeMillis();
    }

    public void onFrame(MinecraftClient mc) {
        if (!isEnabled() || !prediction.get()) {
            return;
        }
        if (mc.player == null || mc.world == null || mc.currentScreen != null) {
            return;
        }
        if (!mc.player.isFallFlying()) {
            return;
        }

        double range = aimDistance.get();
        currentTarget = findNearestTarget(mc, (float) range);
        if (currentTarget == null) {
            return;
        }

        Vec3d targetPos = getPredictedPosition(currentTarget);
        float[] targetRot = getRotationTo(mc.player.getEyePos(), targetPos);

        long now = System.currentTimeMillis();
        float deltaTime = (now - lastFrameTime) / 1000f;
        lastFrameTime = now;
        float frameMultiplier = MathHelper.clamp(deltaTime * TARGET_FPS, 0.1f, 3f);

        float speed = (float) aimSpeed.get();
        float yawStrength = Math.min((speed / 120f) * frameMultiplier, 0.5f);
        float pitchStrength = Math.min((speed / 120f) * frameMultiplier, 0.5f);

        float newYaw = (float) smoothLerp(yawStrength, mc.player.getYaw(), targetRot[0]);
        float newPitch = (float) smoothLerp(pitchStrength, mc.player.getPitch(), targetRot[1]);

        mc.player.setYaw(newYaw);
        mc.player.setPitch(MathHelper.clamp(newPitch, -90f, 90f));
    }

    public void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) {
            return;
        }
        if (!mc.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
            glidePacketSent = false;
            return;
        }
        if (mc.player.isOnGround() || mc.player.isSubmergedInWater() || mc.player.isInLava()) {
            glidePacketSent = false;
            return;
        }
        if (!mc.player.isFallFlying()) {
            if (!glidePacketSent) {
                mc.player.startFallFlying();
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(
                            new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                }
                glidePacketSent = true;
            }
        }
    }

    private Vec3d getPredictedPosition(Entity target) {
        Vec3d pos = target.getPos();
        Vec3d vel = target.getVelocity();
        int ticks = (int) predictTicks.get();
        double predictedX = pos.x + vel.x * ticks;
        double predictedZ = pos.z + vel.z * ticks;
        double predictedY = pos.y + target.getHeight() * 0.5;
        return new Vec3d(predictedX, predictedY, predictedZ);
    }

    private float[] getRotationTo(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        return new float[]{yaw, pitch};
    }

    private double smoothLerp(double delta, double start, double end) {
        delta = Math.max(0, Math.min(1, delta));
        double t = delta * delta * (3 - 2 * delta);
        return start + MathHelper.wrapDegrees(end - start) * t;
    }

    private Entity findNearestTarget(MinecraftClient mc, float radius) {
        Entity nearest = null;
        double nearestDist = radius * radius;

        for (Entity e : mc.world.getEntitiesByClass(LivingEntity.class,
                mc.player.getBoundingBox().expand(radius),
                entity -> entity != mc.player && entity.isAlive())) {
            double dist = mc.player.squaredDistanceTo(e);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = e;
            }
        }
        return nearest;
    }
}
