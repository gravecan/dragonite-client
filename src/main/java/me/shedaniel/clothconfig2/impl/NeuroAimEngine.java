package me.shedaniel.clothconfig2.impl;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.client.render.Camera;
import net.minecraft.client.network.ClientPlayerEntity;
import org.lwjgl.glfw.GLFW;

public final class NeuroAimEngine {
   public static final String MODE_SMOOTH = "Smooth";
   public static final String MODE_BLATANT = "Blatant";
   public static final String MODE_SCREENSHARE = "Screenshare";
   public static final String COUPLING_WITH_PLAYER = "With Player";
   public static final String COUPLING_ALWAYS = "Always";
   public static final String COUPLING_AFTER_IDLE = "After Idle";
   private static final double CLOSE_RANGE = 2.0;
   private static final long INPUT_GRACE_MS = 350L;
   private static final long SWITCH_GRACE_MS = 220L;
   private static final float STABLE_LOCK_THRESHOLD = 0.92F;
   private int currentTargetId = -1;
   private boolean trackingActive;
   private boolean wasEnabled;
   private boolean blatantActive;
   private float desiredYaw;
   private float desiredPitch;
   private float smoothedDesiredYaw;
   private float smoothedDesiredPitch;
   private float smoothVelYaw;
   private float smoothVelPitch;
   private long switchTimer;
   private long lastTargetSwitchMs;
   private int lastLockedId = -1;
   private float mpX;
   private float mpY;
   private float mpZ;
   private long mpSeed;
   private float lastTickYaw;
   private float lastTickPitch;
   private long lastManualInputMs;
   private PlayerEntity focusTarget;
   private PlayerEntity activeTarget;
   private float lastPartial;
   private boolean smoothStable;
   private int stableFrames;
   private boolean smoothedDesiredInitialized;

   public void onEnabledChanged(boolean enabled) {
      this.resetTracking();
      this.wasEnabled = enabled;
   }

   public void resetTracking() {
      this.currentTargetId = -1;
      this.trackingActive = false;
      this.blatantActive = false;
      this.lastManualInputMs = 0L;
      this.focusTarget = null;
      this.activeTarget = null;
      this.lastPartial = 0.0F;
      this.desiredYaw = 0.0F;
      this.desiredPitch = 0.0F;
      this.smoothedDesiredYaw = 0.0F;
      this.smoothedDesiredPitch = 0.0F;
      this.smoothVelYaw = 0.0F;
      this.smoothVelPitch = 0.0F;
      this.switchTimer = 0L;
      this.lastTargetSwitchMs = 0L;
      this.lastLockedId = -1;
      this.mpX = 0.0F;
      this.mpY = 0.0F;
      this.mpZ = 0.0F;
      this.mpSeed = System.nanoTime();
      this.smoothStable = false;
      this.stableFrames = 0;
      this.smoothedDesiredInitialized = false;
      ElytraAimHelper.resetForTarget(-1);
   }

   private void clearTargetState() {
      this.currentTargetId = -1;
      this.trackingActive = false;
      this.blatantActive = false;
      this.focusTarget = null;
      this.activeTarget = null;
      this.smoothedDesiredInitialized = false;
      this.smoothVelYaw = 0.0F;
      this.smoothVelPitch = 0.0F;
      this.smoothStable = false;
      this.stableFrames = 0;
   }

   public void tick(MinecraftClient client, NeuroAimEngine.Settings settings) {
      if (!this.handleEnableState(settings)) {
         this.trackingActive = false;
         this.blatantActive = false;
      } else if (!canRun(client)) {
         this.trackingActive = false;
         this.blatantActive = false;
      } else {
         ClientPlayerEntity player = client.player;
         this.updateManualInput(player);
         if (settings.isSlotLock() && player.getInventory().selectedSlot != MathHelper.clamp(settings.getLockedSlot(), 0, 8)) {
            this.resetTracking();
         } else {
            PlayerEntity target = this.findBestTarget(player, settings, client);
            if (target == null) {
               this.clearTargetState();
            } else {
               boolean targetChanged = this.currentTargetId != target.getId();
               if (targetChanged) {
                  this.onTargetLocked(player, target, settings);
               }

               this.currentTargetId = target.getId();
               if (settings.isFocusOnEnemy()) {
                  this.focusTarget = target;
               }

               this.trackingActive = true;
               this.blatantActive = "Blatant".equals(settings.getMode());
               this.lastLockedId = target.getId();
            }
         }
      }
   }

   public int getLockedTargetId() {
      return this.currentTargetId;
   }

   public void applyFrame(MinecraftClient client, NeuroAimEngine.Settings settings, float partialTick) {
      if (!this.trackingActive || client.player == null || !settings.isEnabled()) {
         this.lastPartial = 0.0F;
      } else if (!canRun(client)) {
         this.lastPartial = 0.0F;
      } else {
         ClientPlayerEntity player = client.player;
         this.updateManualInput(player);
         PlayerEntity target = this.resolveTarget(player, settings, client);
         if (target != null && target.isAlive() && !target.isRemoved()) {
            this.activeTarget = target;
            String mode = settings.getMode();
            if ("Blatant".equals(mode)) {
               this.applyGoonBlatantTrack(player, target, partialTick, settings, client);
            } else if ("Screenshare".equals(mode)) {
               this.applyScreenshareFrame(client, settings, player, target, partialTick);
            } else {
               this.applySmoothFrame(client, settings, player, target, partialTick);
            }

            this.lastPartial = partialTick;
         } else {
            this.clearTargetState();
            this.lastPartial = 0.0F;
         }
      }
   }

   private void applySmoothFrame(MinecraftClient client, NeuroAimEngine.Settings settings, ClientPlayerEntity player, PlayerEntity target, float partialTick) {
      if (settings.isOnlyWhileClicking() && !isAttackHeld(client)) {
         this.decaySmoothState(player);
      } else if (!this.canAssistSmooth(settings, client)) {
         this.decaySmoothState(player);
      } else if (settings.isStopAtTarget() && isCrosshairOnTarget(client, target) && !isElytraAim(settings, player, target)) {
         this.syncSmoothStateToPlayer(player);
      } else {
         float[] desired = this.computeSmoothedDesiredAngles(player, target, partialTick, settings, client);
         this.desiredYaw = desired[0];
         this.desiredPitch = desired[1];
         float lockAmount = this.getSmoothLockAmount(settings, player, target, partialTick, client);
         if (lockAmount >= 0.92F && this.stableFrames > 4) {
            this.applySmoothStableRotation(player, this.desiredYaw, this.desiredPitch, partialTick, settings, client);
         } else {
            this.applySmoothHumanRotation(player, this.desiredYaw, this.desiredPitch, partialTick, settings, client, lockAmount);
         }
      }
   }

   private void applyBlatantFrame(MinecraftClient client, NeuroAimEngine.Settings settings, ClientPlayerEntity player, PlayerEntity target, float partialTick) {
      if (!this.blatantActive) {
         this.blatantActive = true;
      }

      this.applyGoonBlatantTrack(player, target, partialTick, settings, client);
   }

   private void onTargetLocked(ClientPlayerEntity player, PlayerEntity target, NeuroAimEngine.Settings settings) {
      long now = System.currentTimeMillis();
      this.switchTimer = now;
      this.lastTargetSwitchMs = now;
      this.lastLockedId = target.getId();
      this.smoothedDesiredYaw = player.getYaw();
      this.smoothedDesiredPitch = player.getPitch();
      this.smoothVelYaw = 0.0F;
      this.smoothVelPitch = 0.0F;
      this.smoothStable = false;
      this.stableFrames = 0;
      this.mpX = 0.0F;
      this.mpY = 0.0F;
      this.mpZ = 0.0F;
      this.mpSeed = System.nanoTime() ^ target.getId();
      this.smoothedDesiredInitialized = true;
      ElytraAimHelper.resetForTarget(target.getId());
      float[] immediate = getSyncRotations(
         getAimEyePos(player, MinecraftClient.getInstance(), 1.0F),
         this.resolveAimPoint(player, target, settings.getAimHeight(), 1.0F, settings, MinecraftClient.getInstance())
      );
      this.desiredYaw = immediate[0];
      this.desiredPitch = immediate[1];
   }

   private float[] computeSmoothedDesiredAngles(ClientPlayerEntity player, PlayerEntity target, float partialTick, NeuroAimEngine.Settings settings, MinecraftClient client) {
      Vec3d eyePos = getAimEyePos(player, client, partialTick);
      Vec3d aimPoint = this.resolvePredictedAimPoint(player, target, settings.getAimHeight(), partialTick, settings, client);
      float[] raw = getSyncRotations(eyePos, aimPoint);
      float accel = MathHelper.clamp(settings.getAcceleration(), 0.01F, 1.0F);
      float smoothFactor = 0.18F + accel * 0.38F;
      if (isElytraAim(settings, player, target)) {
         smoothFactor *= ElytraAimHelper.desiredAngleSmoothScale();
      }

      if (!this.smoothedDesiredInitialized) {
         float[] view = getViewYawPitch(client, player);
         this.smoothedDesiredYaw = view[0];
         this.smoothedDesiredPitch = view[1];
         this.smoothedDesiredInitialized = true;
      }

      float yawDiff = MathHelper.wrapDegrees(raw[0] - this.smoothedDesiredYaw);
      float pitchDiff = raw[1] - this.smoothedDesiredPitch;
      this.smoothedDesiredYaw = MathHelper.wrapDegrees(this.smoothedDesiredYaw + yawDiff * smoothFactor);
      this.smoothedDesiredPitch += pitchDiff * smoothFactor * 0.9F;
      this.smoothedDesiredPitch = MathHelper.clamp(this.smoothedDesiredPitch, -90.0F, 90.0F);
      this.desiredYaw = this.smoothedDesiredYaw;
      this.desiredPitch = this.smoothedDesiredPitch;
      return new float[]{this.smoothedDesiredYaw, this.smoothedDesiredPitch};
   }

   private void applySmoothHumanRotation(
      ClientPlayerEntity player, float targetYaw, float targetPitch, float partialTick, NeuroAimEngine.Settings settings, MinecraftClient client, float lockAmount
   ) {
      this.smoothStable = false;
      float yaw = player.getYaw();
      float pitch = player.getPitch();
      float accel = MathHelper.clamp(settings.getAcceleration(), 0.01F, 1.0F);
      float speedNorm = MathHelper.clamp(settings.getSpeed(), 1.0F, 10.0F) / 10.0F;
      float baseFactor = 0.08F + accel * 0.42F;
      baseFactor *= 0.65F + speedNorm * 0.55F;
      baseFactor *= 0.55F + lockAmount * 0.55F;
      if (this.activeTarget != null && isElytraAim(settings, player, this.activeTarget)) {
         baseFactor *= ElytraAimHelper.rotationBoost(player, this.activeTarget);
      }

      if (this.switchTimer > 0L) {
         long sinceSwitch = System.currentTimeMillis() - this.switchTimer;
         if (sinceSwitch < 220L) {
            float ramp = (float)sinceSwitch / 220.0F;
            baseFactor *= 0.35F + ramp * 0.65F;
         } else {
            this.switchTimer = 0L;
         }
      }

      float yawDelta = MathHelper.wrapDegrees(targetYaw - yaw);
      float pitchDelta = targetPitch - pitch;
      this.smoothVelYaw = this.smoothVelYaw * 0.72F + yawDelta * baseFactor * 0.28F;
      this.smoothVelPitch = this.smoothVelPitch * 0.72F + pitchDelta * baseFactor * 0.24F;
      float microYaw = pseudoNoise(this.mpSeed, 1) * 0.06F * (1.0F - lockAmount);
      float microPitch = pseudoNoise(this.mpSeed, 2) * 0.04F * (1.0F - lockAmount);
      float stepYaw = this.smoothVelYaw + microYaw;
      float stepPitch = this.smoothVelPitch + microPitch;
      double gcd = getMouseGcd(client);
      stepYaw = (float)quantizeGcd(stepYaw, gcd);
      stepPitch = (float)quantizeGcd(stepPitch, gcd);
      if (this.activeTarget != null && isElytraAim(settings, player, this.activeTarget)) {
         float[] capped = ElytraAimHelper.capRotationStep(stepYaw, stepPitch);
         stepYaw = capped[0];
         stepPitch = capped[1];
      }

      if (!(Math.abs(stepYaw) < gcd * 0.35) || !(Math.abs(stepPitch) < gcd * 0.35)) {
         player.setYaw(yaw + stepYaw);
         player.setPitch(MathHelper.clamp(pitch + stepPitch, -90.0F, 90.0F));
      }
   }

   private void applySmoothStableRotation(
      ClientPlayerEntity player, float targetYaw, float targetPitch, float partialTick, NeuroAimEngine.Settings settings, MinecraftClient client
   ) {
      this.smoothStable = true;
      float yaw = player.getYaw();
      float pitch = player.getPitch();
      float accel = MathHelper.clamp(settings.getAcceleration(), 0.01F, 1.0F);
      float speedNorm = MathHelper.clamp(settings.getSpeed(), 1.0F, 10.0F) / 10.0F;
      float factor = 0.14F + accel * 0.28F;
      factor *= 0.7F + speedNorm * 0.35F;
      if (this.activeTarget != null && isElytraAim(settings, player, this.activeTarget)) {
         factor *= ElytraAimHelper.rotationBoost(player, this.activeTarget);
      }

      float yawDelta = MathHelper.wrapDegrees(targetYaw - yaw);
      float pitchDelta = targetPitch - pitch;
      float stepYaw = yawDelta * factor;
      float stepPitch = pitchDelta * factor * 0.88F;
      double gcd = getMouseGcd(client);
      stepYaw = (float)quantizeGcd(stepYaw, gcd);
      stepPitch = (float)quantizeGcd(stepPitch, gcd);
      if (this.activeTarget != null && isElytraAim(settings, player, this.activeTarget)) {
         float[] capped = ElytraAimHelper.capRotationStep(stepYaw, stepPitch);
         stepYaw = capped[0];
         stepPitch = capped[1];
      }

      if (!(Math.abs(stepYaw) < gcd * 0.4) || !(Math.abs(stepPitch) < gcd * 0.4)) {
         player.setYaw(yaw + stepYaw);
         player.setPitch(MathHelper.clamp(pitch + stepPitch, -90.0F, 90.0F));
      }
   }

   private void applyGoonBlatantTrack(ClientPlayerEntity player, PlayerEntity target, float partialTick, NeuroAimEngine.Settings settings, MinecraftClient client) {
      Vec3d eyePos = getAimEyePos(player, client, partialTick);
      Vec3d targetPoint = this.resolveAimPoint(player, target, settings.getAimHeight(), partialTick, settings, client);
      if (settings.isMultipoint()) {
         this.updateMultipointDrift(settings, partialTick);
         targetPoint = targetPoint.add(this.mpX, this.mpY, this.mpZ);
      }

      double dist = eyePos.distanceTo(targetPoint);
      float[] synced = getSyncRotations(eyePos, targetPoint);
      float yawDelta = MathHelper.wrapDegrees(synced[0] - player.getYaw());
      float pitchDelta = MathHelper.wrapDegrees(synced[1] - player.getPitch());
      float response = MathHelper.clamp(settings.getSpeed(), 1.0F, 10.0F) / 10.0F;
      float distFactor = 1.0F;
      if (dist > 2.5) {
         distFactor = 1.0F + (float)MathHelper.clamp((dist - 2.5) / 1.0, 0.0, 1.0);
      }

      float effectiveResponse = Math.min(1.0F, response * distFactor);
      if (isElytraAim(settings, player, target)) {
         effectiveResponse = Math.min(1.0F, effectiveResponse * ElytraAimHelper.rotationBoost(player, target));
      }

      float stepYaw = yawDelta * effectiveResponse;
      float stepPitch = pitchDelta * effectiveResponse;
      double gcd = getMouseGcd(client);
      stepYaw = (float)quantizeGcd(stepYaw, gcd);
      stepPitch = (float)quantizeGcd(stepPitch, gcd);
      if (isElytraAim(settings, player, target)) {
         float[] capped = ElytraAimHelper.capRotationStep(stepYaw, stepPitch);
         stepYaw = capped[0];
         stepPitch = capped[1];
      }

      if (!(Math.abs(stepYaw) < gcd * 0.5) || !(Math.abs(stepPitch) < gcd * 0.5)) {
         player.setYaw(player.getYaw() + stepYaw);
         player.setPitch(MathHelper.clamp(player.getPitch() + stepPitch, -90.0F, 90.0F));
      }
   }

   private void updateMultipointDrift(NeuroAimEngine.Settings settings, float partialTick) {
      this.mpSeed += 17L;
      float driftScale = 0.045F + settings.getSpeed() * 0.004F;
      this.mpX = this.mpX + (pseudoNoise(this.mpSeed, 3) - 0.5F) * driftScale * partialTick;
      this.mpY = this.mpY + (pseudoNoise(this.mpSeed, 4) - 0.5F) * driftScale * 0.35F * partialTick;
      this.mpZ = this.mpZ + (pseudoNoise(this.mpSeed, 5) - 0.5F) * driftScale * partialTick;
      this.mpX = MathHelper.clamp(this.mpX, -0.22F, 0.22F);
      this.mpY = MathHelper.clamp(this.mpY, -0.08F, 0.08F);
      this.mpZ = MathHelper.clamp(this.mpZ, -0.22F, 0.22F);
      this.mpX *= 0.94F;
      this.mpY *= 0.92F;
      this.mpZ *= 0.94F;
   }

   private Vec3d resolvePredictedAimPoint(
      ClientPlayerEntity player, PlayerEntity target, float aimHeightNorm, float partialTick, NeuroAimEngine.Settings settings, MinecraftClient client
   ) {
      return isElytraAim(settings, player, target)
         ? ElytraAimHelper.getAimPoint(client, player, target, aimHeightNorm, partialTick)
         : this.getPredictedAimPoint(target, aimHeightNorm, partialTick, settings);
   }

   private Vec3d resolveAimPoint(
      ClientPlayerEntity player, PlayerEntity target, float aimHeightNorm, float partialTick, NeuroAimEngine.Settings settings, MinecraftClient client
   ) {
      return isElytraAim(settings, player, target)
         ? ElytraAimHelper.getAimPoint(client, player, target, aimHeightNorm, partialTick)
         : getFocusPoint(target, aimHeightNorm, partialTick);
   }

   private static boolean isElytraAim(NeuroAimEngine.Settings settings, ClientPlayerEntity player, PlayerEntity target) {
      return settings.isElytraTracking() && ElytraAimHelper.shouldUse(player, target);
   }

   private Vec3d getPredictedAimPoint(PlayerEntity target, float aimHeightNorm, float partialTick, NeuroAimEngine.Settings settings) {
      Vec3d base = getFocusPoint(target, aimHeightNorm, partialTick);
      Vec3d velocity = target.getVelocity();
      double dist = target.distanceTo(MinecraftClient.getInstance().player);
      double leadTicks = 1.2 + MathHelper.clamp((float)(dist * 0.08), 0.0F, 2.5F);
      float accelBias = MathHelper.clamp(settings.getAcceleration(), 0.01F, 1.0F);
      leadTicks *= 0.75 + accelBias * 0.45;
      return base.add(velocity.x * leadTicks * 0.5, velocity.y * leadTicks * 0.35, velocity.z * leadTicks * 0.5);
   }

   private void decaySmoothState(ClientPlayerEntity player) {
      this.smoothVelYaw *= 0.55F;
      this.smoothVelPitch *= 0.55F;
      this.smoothedDesiredYaw = player.getYaw();
      this.smoothedDesiredPitch = player.getPitch();
      this.stableFrames = 0;
      this.smoothStable = false;
   }

   private void syncSmoothStateToPlayer(ClientPlayerEntity player) {
      this.smoothedDesiredYaw = player.getYaw();
      this.smoothedDesiredPitch = player.getPitch();
      this.smoothVelYaw = 0.0F;
      this.smoothVelPitch = 0.0F;
      this.stableFrames = 0;
   }

   private float getSmoothLockAmount(NeuroAimEngine.Settings settings, ClientPlayerEntity player, PlayerEntity target, float partialTick, MinecraftClient client) {
      Vec3d eyePos = getAimEyePos(player, client, partialTick);
      Vec3d aim = this.resolvePredictedAimPoint(player, target, settings.getAimHeight(), partialTick, settings, client);
      float[] rot = getSyncRotations(eyePos, aim);
      float yawErr = Math.abs(MathHelper.wrapDegrees(rot[0] - player.getYaw()));
      float pitchErr = Math.abs(rot[1] - player.getPitch());
      float err = yawErr + pitchErr * 0.55F;
      float maxErr = MathHelper.clamp(settings.getFov() * 0.35F, 12.0F, 55.0F);
      float amount = 1.0F - MathHelper.clamp(err / maxErr, 0.0F, 1.0F);
      if (amount >= 0.92F) {
         this.stableFrames++;
      } else {
         this.stableFrames = 0;
         this.smoothStable = false;
      }

      return amount;
   }

   private boolean canAssistSmooth(NeuroAimEngine.Settings settings, MinecraftClient client) {
      return settings.isRequireAttackHeld() && !isAttackHeld(client) ? false : this.canAssistWithCoupling(settings);
   }

   private boolean canAssistWithCoupling(NeuroAimEngine.Settings settings) {
      String var2 = settings.getMovementCoupling();

      return switch (var2) {
         case "Always" -> true;
         case "After Idle" -> {
            if (this.isManualInputActive()) {
               yield false;
            } else if (this.lastManualInputMs == 0L) {
               yield true;
            } else {
               long idleMs = (long)(settings.getIdleDelaySeconds() * 1000.0F);
               yield System.currentTimeMillis() - this.lastManualInputMs >= idleMs;
            }
         }
         default -> this.isManualInputActive();
      };
   }

   private PlayerEntity resolveTarget(ClientPlayerEntity player, NeuroAimEngine.Settings settings, MinecraftClient client) {
      return this.currentTargetId >= 0
            && player.getWorld().getEntityById(this.currentTargetId) instanceof PlayerEntity target
            && target.isAlive()
            && !target.isRemoved()
            && player.distanceTo(target) <= MathHelper.clamp(settings.getRange(), 1.0F, 50.0F)
         ? target
         : this.findBestTarget(player, settings, client);
   }

   private PlayerEntity findBestTarget(ClientPlayerEntity player, NeuroAimEngine.Settings settings, MinecraftClient client) {
      if (this.focusTarget != null && (!this.focusTarget.isAlive() || this.focusTarget.isRemoved())) {
         this.focusTarget = null;
      }

      boolean targetLock = settings.isFocusOnEnemy();
      PlayerEntity locked = targetLock ? this.focusTarget : null;
      if (locked == null
         && targetLock
         && this.currentTargetId >= 0
         && player.getWorld().getEntityById(this.currentTargetId) instanceof PlayerEntity sticky
         && sticky.isAlive()
         && !sticky.isRemoved()) {
         locked = sticky;
      }

      if (targetLock && locked != null && locked.isAlive() && !locked.isRemoved() && !isFilteredByAntibot(locked) && !me.shedaniel.clothconfig2.impl.FriendManager.isCombatExempt(locked)) {
         return locked;
      }

      float maxRange = MathHelper.clamp(settings.getRange(), 1.0F, 50.0F);
      float maxFov = MathHelper.clamp(settings.getFov(), 15.0F, 360.0F);
      float[] view = getViewYawPitch(client, player);
      float viewYaw = view[0];

      PlayerEntity best = null;
      double bestDist = Double.MAX_VALUE;

      for (PlayerEntity other : player.getWorld().getPlayers()) {
         if (other != player && other.isAlive() && !other.isRemoved()) {
            if (isFilteredByAntibot(other)) {
               continue;
            }
            if (me.shedaniel.clothconfig2.impl.FriendManager.isCombatExempt(other)) {
               continue;
            }
            double dist = player.distanceTo(other);
            if (dist <= maxRange) {
               if (!settings.isAimThroughWalls() && !player.canSee(other)) {
                  continue;
               }
               if (maxFov < 360.0F) {
                  Vec3d eye = player.getEyePos();
                  Vec3d targetPoint = other.getLerpedPos(1.0F).add(0.0, other.getBoundingBox().getLengthY() * settings.getAimHeight(), 0.0);
                  Vec3d delta = targetPoint.subtract(eye);
                  float targetYaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
                  if (Math.abs(MathHelper.wrapDegrees(targetYaw - viewYaw)) > maxFov * 0.5F) {
                     continue;
                  }
               }
               if (dist < bestDist) {
                  bestDist = dist;
                  best = other;
               }
            }
         }
      }
      return best;
   }

   private static boolean isFilteredByAntibot(PlayerEntity other) {
      ConfigBuilderImpl mgr = HudConfigInit.getManager();
      if (mgr == null) {
         return false;
      }
      ListEntryImpl ab = mgr.getModuleByClass(ListEntryImpl.class);
      return ab != null && ab.isEnabled() && ab.isBot(other);
   }

   private static Vec3d getAimEyePos(ClientPlayerEntity player, MinecraftClient client, float partialTick) {
      return client.options.getPerspective().isFirstPerson() ? player.getCameraPosVec(partialTick) : client.gameRenderer.getCamera().getPos();
   }

   private static float[] getViewYawPitch(MinecraftClient client, ClientPlayerEntity player) {
      if (client.options.getPerspective().isFirstPerson()) {
         return new float[]{player.getYaw(), player.getPitch()};
      } else {
         Camera camera = client.gameRenderer.getCamera();
         return new float[]{camera.getYaw(), camera.getPitch()};
      }
   }

   private static float pseudoNoise(long seed, int channel) {
      long x = seed * 6364136223846793005L + 1442695040888963407L + channel * -7046029254386353131L;
      x ^= x >>> 33;
      x *= -49064778989728563L;
      x ^= x >>> 33;
      return (float)((x & 65535L) / 65535.0);
   }

   private void updateManualInput(ClientPlayerEntity player) {
      float yaw = player.getYaw();
      float pitch = player.getPitch();
      float dy = MathHelper.wrapDegrees(yaw - this.lastTickYaw);
      float dp = pitch - this.lastTickPitch;
      if (Math.abs(dy) > 0.012F || Math.abs(dp) > 0.012F) {
         this.lastManualInputMs = System.currentTimeMillis();
      }

      this.lastTickYaw = yaw;
      this.lastTickPitch = pitch;
   }

   private boolean isManualInputActive() {
      return this.lastManualInputMs > 0L && System.currentTimeMillis() - this.lastManualInputMs < 350L;
   }

   private static boolean isAttackHeld(MinecraftClient client) {
      return client.player != null && client.getWindow() != null
         ? GLFW.glfwGetMouseButton(client.getWindow().getHandle(), 0) == 1 || client.options.attackKey.isPressed()
         : false;
   }

   private static boolean isCrosshairOnTarget(MinecraftClient client, PlayerEntity target) {
      return client.crosshairTarget instanceof EntityHitResult ehr ? ehr.getEntity() == target : false;
   }

   private boolean handleEnableState(NeuroAimEngine.Settings settings) {
      if (!settings.isEnabled()) {
         if (this.wasEnabled) {
            this.resetTracking();
         }

         this.wasEnabled = false;
         return false;
      } else {
         if (!this.wasEnabled) {
            this.resetTracking();
            this.wasEnabled = true;
         }

         return true;
      }
   }

   private static boolean canRun(MinecraftClient client) {
      return client.player != null && client.world != null && client.currentScreen == null;
   }

   private static Vec3d getFocusPoint(PlayerEntity entity, float aimHeightNorm, float partialTicks) {
      Vec3d pos = entity.getLerpedPos(partialTicks);
      double height = entity.getHeight();
      if (entity.isFallFlying()) {
         return pos.add(0.0, height * 0.5, 0.0);
      } else {
         double y = height * MathHelper.clamp(aimHeightNorm, 0.05, 0.95);
         return pos.add(0.0, y, 0.0);
      }
   }

   private static float[] getSyncRotations(Vec3d eyePos, Vec3d target) {
      double dx = target.x - eyePos.x;
      double dy = target.y - eyePos.y;
      double dz = target.z - eyePos.z;
      double hDist = Math.sqrt(dx * dx + dz * dz);
      float yaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
      float pitch = (float)(-Math.toDegrees(Math.atan2(dy, hDist)));
      return new float[]{yaw, pitch};
   }

   private static double getMouseGcd(MinecraftClient client) {
      double sensitivity = client.options.getMouseSensitivity().getValue();
      double d0 = sensitivity * 0.6 + 0.2;
      return d0 * d0 * d0 * 8.0 * 0.15;
   }

    private boolean isTeammate(net.minecraft.entity.Entity entity, net.minecraft.entity.player.PlayerEntity player) {
        return entity.getScoreboardTeam() != null && 
               entity.getScoreboardTeam().equals(player.getScoreboardTeam());
    }

    private void applyScreenshareFrame(MinecraftClient client, NeuroAimEngine.Settings settings, ClientPlayerEntity player, net.minecraft.entity.Entity currentTarget, float partialTick) {
        Vec3d eyePos = player.getEyePos();
        float bestAngle = Float.MAX_VALUE;
        net.minecraft.entity.Entity bestTarget = null;
        float yawDeltaFinal = 0f;
        float pitchDeltaFinal = 0f;

        for (net.minecraft.entity.Entity entity : client.world.getEntities()) {
            if (entity == null || entity == player) {
                continue;
            }
            if (!entity.isAlive()) {
                continue;
            }
            double distSq = entity.squaredDistanceTo(player);
            double maxDist = settings.getRange();
            if (distSq > maxDist * maxDist) {
                continue;
            }
            if (settings.isTeammates() && entity instanceof net.minecraft.entity.player.PlayerEntity targetPlayer && isTeammate(targetPlayer, player)) {
                continue;
            }
            if (entity.isInvisible()) {
                continue;
            }
            if (settings.isPlayersOnly() && !(entity instanceof net.minecraft.entity.player.PlayerEntity)) {
                continue;
            }

            net.minecraft.util.math.Box box = entity.getBoundingBox();
            Vec3d targetPos = new Vec3d(
                (box.minX + box.maxX) * 0.5,
                box.minY + (box.maxY - box.minY) * 0.85, 
                (box.minZ + box.maxZ) * 0.5
            );

            if (!settings.isAimThroughWalls() && !player.canSee(entity)) {
                continue;
            }

            Vec3d diff = targetPos.subtract(eyePos);
            double horizontalDistance = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
            if (horizontalDistance == 0.0) continue;

            float yaw = (float)(-Math.toDegrees(Math.atan2(diff.x, diff.z)));
            float pitch = (float)(-Math.toDegrees(Math.atan2(diff.y, horizontalDistance))) - 90.0f;

            float yawDelta = MathHelper.wrapDegrees(yaw - player.getYaw());
            float pitchDelta = MathHelper.wrapDegrees(pitch - player.getPitch());

            float angleDiff = Math.abs(yawDelta) + Math.abs(pitchDelta);
            if (angleDiff > settings.getFov()) {
                continue;
            }

            if (angleDiff < bestAngle) {
                bestAngle = angleDiff;
                bestTarget = entity;
                yawDeltaFinal = yawDelta;
                pitchDeltaFinal = pitchDelta;
            }
        }

        if (bestTarget == null) {
            return;
        }

        float smoothFactor = 0.1f * (float) settings.getSpeed();
        float newYaw = player.getYaw() + smoothFactor * yawDeltaFinal;
        float newPitch = player.getPitch() + smoothFactor * pitchDeltaFinal;

        player.setYaw(newYaw);
        player.setPitch(MathHelper.clamp(newPitch, -90.0f, 90.0f));
    }

   private static double quantizeGcd(double rotation, double gcd) {
      return gcd <= 1.0E-4 ? rotation : Math.round(rotation / gcd) * gcd;
   }

   public interface Settings {
      boolean isEnabled();

      String getMode();

      String getMovementCoupling();

      float getIdleDelaySeconds();

      float getSpeed();

      float getAcceleration();

      float getRange();

      float getFov();

      float getAimHeight();

      boolean isMultipoint();

      boolean isSlotLock();

      int getLockedSlot();

      boolean isAimThroughWalls();

      boolean isFocusOnEnemy();

      boolean isRequireAttackHeld();

      boolean isOnlyWhileClicking();

      boolean isStopAtTarget();

      boolean isElytraTracking();

      String getTargetPriority();

      ListEntryImpl getAntibot();

      boolean isTeammates();
      boolean isPriority();
      boolean isPlayersOnly();
   }
}
