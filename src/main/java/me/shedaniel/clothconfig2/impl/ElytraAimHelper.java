package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;


public final class ElytraAimHelper {

    private static final double IMPULSE_ACCEL_SQ = 0.065;
    private static final double MAX_EDGE_OFFSET   = 0.32;

    // Vanilla elytra physics constants
    private static final double ELYTRA_DRAG       = 0.99;   // per-tick horizontal drag multiplier
    private static final double ELYTRA_GRAVITY     = 0.08;   // blocks/tick² downward gravity

    // Elytra bounding box when fall-flying: height=0.6, eye at 0.4 from bottom
    private static final double ELYTRA_BOX_HEIGHT  = 0.6;
    private static final double ELYTRA_EYE_FRAC    = 0.4 / 0.6; // ~0.667 relative eye height

    private static Vec3d smoothedAim        = Vec3d.ZERO;
    private static int   smoothedTargetId   = -1;
    private static boolean smoothedInitialized;

    // Smoothed acceleration for rocket-burst compensation
    private static Vec3d smoothedAccel      = Vec3d.ZERO;

    private ElytraAimHelper() {}

    public static void resetForTarget(int targetId) {
        smoothedTargetId     = targetId;
        smoothedInitialized  = false;
        smoothedAccel        = Vec3d.ZERO;
    }

    public static boolean shouldUse(ClientPlayerEntity player, PlayerEntity target) {
        return player != null && target != null && target.isFallFlying();
    }

    public static Vec3d getAimPoint(
            MinecraftClient client,
            ClientPlayerEntity player,
            PlayerEntity target,
            float aimHeightNorm,
            float partialTick
    ) {
        if (smoothedTargetId != target.getId()) {
            resetForTarget(target.getId());
        }

        Vec3d raw = computeRawAimPoint(client, player, target, aimHeightNorm, partialTick);
        if (!smoothedInitialized) {
            smoothedAim         = raw;
            smoothedInitialized = true;
            return smoothedAim;
        }

        smoothedAim = smoothedAim.lerp(raw, 0.55);
        return smoothedAim;
    }

    private static Vec3d computeRawAimPoint(
            MinecraftClient client,
            ClientPlayerEntity player,
            PlayerEntity target,
            float aimHeightNorm,
            float partialTick
    ) {
        Vec3d pos = target.getLerpedPos(partialTick);
        Vec3d eye = player.getCameraPosVec(partialTick);

        // ── Idea A: Tilt-Aware Body Center ────────────────────────────────────
        // When fall-flying the MC bounding box shrinks to 0.6 blocks tall and the
        // player is tilted horizontal.  pos.y is the *bottom* of that short box.
        // We derive the flight pitch from velocity and shift the vertical centre
        // point toward the actual fuselage mid-point rather than using a standing
        // eye-height which ends up too high.
        Vec3d vel = target.getVelocity();
        Vec3d bodyCenter;
        double tiltCenterY;
        if (target.isFallFlying()) {
            double horizSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            // flight pitch: negative = nose down (diving), positive = nose up (climbing)
            double flightPitch = Math.atan2(-vel.y, Math.max(horizSpeed, 0.001));
            // project half the player length along the flight vector to find chest mid
            double halfLen = ELYTRA_BOX_HEIGHT * 0.5;
            // tilt shifts the Y aim point: when diving, player body is lower than eye
            double tiltOffset = Math.sin(flightPitch) * halfLen;
            tiltCenterY = pos.y + halfLen - tiltOffset * 0.55;
            bodyCenter   = new Vec3d(pos.x, tiltCenterY, pos.z);
        } else {
            tiltCenterY = pos.y + target.getHeight() * 0.5;
            bodyCenter   = pos.add(0.0, target.getHeight() * 0.5, 0.0);
        }

        // ── Idea B: Quadratic Acceleration Prediction ─────────────────────────
        // Smooth the raw tick-delta accel to avoid rocket-burst noise spikes.
        Vec3d tickVel = new Vec3d(
                target.getX() - target.prevX,
                target.getY() - target.prevY,
                target.getZ() - target.prevZ
        );
        Vec3d rawAccel = vel.subtract(tickVel);
        // Exponential smooth: weight new accel more when it's large (burst detect)
        double accelMag = rawAccel.lengthSquared();
        double accelBlend = accelMag > IMPULSE_ACCEL_SQ ? 0.65 : 0.18;
        smoothedAccel = smoothedAccel.lerp(rawAccel, accelBlend);

        float behindBlend = getBehindBlend(eye, bodyCenter, vel);
        float frontBlend  = getFrontBlend(eye, bodyCenter, vel);

        double leadTicks = getLeadTicks(client, player, target, vel, smoothedAccel);
        leadTicks *= MathHelper.lerp(1.0, 0.55, behindBlend);

        // Quadratic XZ prediction: pos + vel*t + 0.5*accel*t²
        double leadSq = leadTicks * leadTicks;
        double predX  = pos.x + vel.x * leadTicks + smoothedAccel.x * leadSq * 0.5;
        double predZ  = pos.z + vel.z * leadTicks + smoothedAccel.z * leadSq * 0.5;

        // ── Idea C: Gravity + Air-Drag Y Compensation ─────────────────────────
        // Vanilla elytra applies ELYTRA_DRAG per tick and ELYTRA_GRAVITY downward.
        // We simulate the Y trajectory forward by leadTicks steps analytically:
        //   Y(t) = Y0 + Vy*(1 - drag^t)/(1-drag) - gravity * t
        // For small t a linear approximation is accurate enough:
        //   deltaY ≈ vel.y * leadTicks * drag_avg - gravity * leadTicks
        double dragAvg = Math.pow(ELYTRA_DRAG, leadTicks * 0.5); // mid-point drag
        double predDeltaY = vel.y * leadTicks * dragAvg - ELYTRA_GRAVITY * leadTicks * 0.5;

        // Tilt-aware Y: use the corrected body centre and add physics delta
        double predY = tiltCenterY + predDeltaY;

        // Apply aimHeightNorm offset relative to the compressed elytra box height
        double boxH    = target.isFallFlying() ? ELYTRA_BOX_HEIGHT : target.getHeight();
        double yOffset = MathHelper.lerp(-0.15, 0.15, MathHelper.clamp(aimHeightNorm, 0.0F, 1.0F));
        predY += yOffset * boxH;

        // ── Edge / Behind offset (unchanged logic) ────────────────────────────
        Vec3d horizVel  = new Vec3d(vel.x, 0.0, vel.z);
        double horizSpeed = horizVel.length();
        Vec3d edgeOffset = Vec3d.ZERO;
        if (horizSpeed > 0.05 && frontBlend > 0.08) {
            double edge = Math.min(MAX_EDGE_OFFSET, horizSpeed * 0.14 + 0.05) * frontBlend;
            edgeOffset  = horizVel.normalize().multiply(edge);
        }
        if (behindBlend > 0.12) {
            Vec3d toEye    = eye.subtract(bodyCenter);
            Vec3d pullHoriz = new Vec3d(toEye.x, 0.0, toEye.z);
            if (pullHoriz.lengthSquared() > 1.0E-4) {
                edgeOffset = edgeOffset.add(pullHoriz.normalize().multiply(0.12 * behindBlend));
            }
        }

        return new Vec3d(
                predX + edgeOffset.x,
                predY,
                predZ + edgeOffset.z
        );
    }

    private static float getBehindBlend(Vec3d eye, Vec3d targetCenter, Vec3d velocity) {
        Vec3d toTarget = new Vec3d(targetCenter.x - eye.x, 0.0, targetCenter.z - eye.z);
        double dist    = toTarget.length();
        if (dist < 0.15) return 0.0F;
        Vec3d velHoriz = new Vec3d(velocity.x, 0.0, velocity.z);
        if (velHoriz.lengthSquared() < 0.0025) return 0.0F;
        double dot = toTarget.normalize().dotProduct(velHoriz.normalize());
        return MathHelper.clamp((float) ((dot - 0.2) / 0.55), 0.0F, 1.0F);
    }

    private static float getFrontBlend(Vec3d eye, Vec3d targetCenter, Vec3d velocity) {
        Vec3d toTarget = new Vec3d(targetCenter.x - eye.x, 0.0, targetCenter.z - eye.z);
        double dist    = toTarget.length();
        if (dist < 0.15) return 0.0F;
        Vec3d velHoriz = new Vec3d(velocity.x, 0.0, velocity.z);
        if (velHoriz.lengthSquared() < 0.0025) return 0.0F;
        double dot = toTarget.normalize().dotProduct(velHoriz.normalize());
        return MathHelper.clamp((float) ((-dot - 0.2) / 0.55), 0.0F, 1.0F);
    }

    public static float rotationBoost(ClientPlayerEntity player, PlayerEntity target) {
        if (!shouldUse(player, target)) return 1.0F;
        Vec3d rel      = target.getVelocity().subtract(player.getVelocity());
        double relSpeed = rel.length();
        return MathHelper.clamp(1.14F + (float) Math.min(relSpeed * 0.22, 0.22), 1.14F, 1.32F);
    }

    public static float[] capRotationStep(float yawStep, float pitchStep) {
        float yaw   = MathHelper.clamp(yawStep,   -14.5F, 14.5F);
        float pitch = MathHelper.clamp(pitchStep, -11.0F, 11.0F);
        return new float[]{yaw, pitch};
    }

    public static float desiredAngleSmoothScale() {
        return 1.12F;
    }

    private static double getLeadTicks(
            MinecraftClient client,
            ClientPlayerEntity player,
            PlayerEntity target,
            Vec3d velocity,
            Vec3d accel
    ) {
        double pingSeconds = 0.05;
        if (client.getNetworkHandler() != null) {
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(player.getUuid());
            if (entry != null) {
                pingSeconds = entry.getLatency() / 1000.0;
            }
        }

        double dist = player.distanceTo(target);
        double lead = pingSeconds * 20.0 + MathHelper.clamp(dist * 0.055, 0.0, 2.0);

        // Boost lead when firework-burst detected (large acceleration spike)
        if (accel.lengthSquared() > IMPULSE_ACCEL_SQ) {
            lead *= 1.35;   // was *0.55 which actually REDUCED lead on boost – wrong direction
        } else if (velocity.lengthSquared() > 0.5) {
            lead *= 1.12;
        }

        return MathHelper.clamp(lead, 0.35, 4.5);
    }
}
