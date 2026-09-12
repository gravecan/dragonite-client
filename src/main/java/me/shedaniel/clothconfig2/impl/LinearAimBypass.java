package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class LinearAimBypass extends ConfigCategoryImpl {

    private final BooleanToggleBuilder enabled = new BooleanToggleBuilder("Linear Aim", "", false);
    private final DoubleFieldBuilder distance = new DoubleFieldBuilder("Distance", "Aim at whole body within this distance", 3.0, 1.0, 6.0, 0.5);
    private final EnumSelectorBuilder aimMode = new EnumSelectorBuilder("Aim Mode", "", "Smooth", "Smooth", "Linear");

    private PlayerEntity lockedTarget = null;

    public LinearAimBypass() {
        super("Linear Aim Bypass", "Aim at whole body when close instead of locking to body part", Cat.COMBAT);
        setTooltip("Aims at enemy center when they are close to you");
        
        addSetting(enabled);
        addSetting(distance);
        addSetting(aimMode);
    }

    @Override
    public void onEnable() {
        lockedTarget = null;
    }

    @Override
    public void onDisable() {
        lockedTarget = null;
    }

    public void onFrame(MinecraftClient mc) {
        if (!enabled.get()) return;
        if (mc.player == null || mc.currentScreen != null) return;
        if (mc.player.getMainHandStack().getItem() instanceof AxeItem) return;

        PlayerEntity target = findNearestPlayer(mc);
        if (target == null) {
            lockedTarget = null;
            return;
        }

        double dist = mc.player.distanceTo(target);
        
        if (dist <= distance.get()) {
            if (lockedTarget == null || lockedTarget != target) {
                lockedTarget = target;
            }
        } else {
            lockedTarget = null;
        }

        if (lockedTarget == null) return;

        net.minecraft.util.math.Box box = lockedTarget.getBoundingBox();
        Vec3d eyePos = mc.player.getEyePos();
        
        double cx = MathHelper.clamp(eyePos.x, box.minX, box.maxX);
        double cy = MathHelper.clamp(eyePos.y, box.minY, box.maxY);
        double cz = MathHelper.clamp(eyePos.z, box.minZ, box.maxZ);
        
        String mode = aimMode.get();
        Vec3d aimPoint = new Vec3d(cx, cy, cz);

        float[] rot = getRotation(mc, aimPoint);
        
        if (mode.equals("Smooth")) {
            float smoothYaw = smoothLerp(0.3f, mc.player.getYaw(), rot[0]);
            float smoothPitch = smoothLerp(0.3f, mc.player.getPitch(), rot[1]);
            mc.player.setYaw(smoothYaw);
            mc.player.setPitch(MathHelper.clamp(smoothPitch, -90f, 90f));
        } else {
            mc.player.setYaw(rot[0]);
            mc.player.setPitch(MathHelper.clamp(rot[1], -90f, 90f));
        }
    }

    private PlayerEntity findNearestPlayer(MinecraftClient mc) {
        PlayerEntity nearest = null;
        double minDist = Double.MAX_VALUE;
        
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive()) continue;
            
            double dist = mc.player.distanceTo(player);
            if (dist < minDist && dist <= 6.0) {
                minDist = dist;
                nearest = player;
            }
        }
        return nearest;
    }

    private float[] getRotation(MinecraftClient mc, Vec3d target) {
        Vec3d eye = mc.player.getEyePos();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) (-(Math.atan2(dy, dist) * 180.0 / Math.PI));
        
        return new float[]{yaw, pitch};
    }

    private float smoothLerp(float factor, float current, float target) {
        float diff = target - current;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        return current + diff * factor;
    }
}
