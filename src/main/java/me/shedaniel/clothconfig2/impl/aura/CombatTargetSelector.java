package me.shedaniel.clothconfig2.impl.aura;

import java.util.ArrayList;
import java.util.List;
import me.shedaniel.clothconfig2.impl.ListEntryImpl;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.RaycastContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;

public final class CombatTargetSelector {
   private CombatTargetSelector() {
   }

   public static PlayerEntity selectAimTarget(
      ClientPlayerEntity player,
      float range,
      float fov,
      boolean throughWalls,
      boolean lockTarget,
      PlayerEntity locked,
      String priority,
      ListEntryImpl antibot,
      float viewYaw,
      float viewPitch
   ) {
      if (locked != null && (!locked.isAlive() || locked.isRemoved())) {
         locked = null;
      }

      if (lockTarget && locked != null && isAimCandidateValid(player, locked, range, throughWalls)) {
         return locked;
      } else {
         float activeFov = lockTarget && locked != null ? Math.min(fov * 2.0F, 360.0F) : fov;
         float halfFov = activeFov * 0.5F;
         Vec3d eye = player.getEyePos();
         boolean antibotOn = antibot != null && antibot.isEnabled();
         PlayerEntity best = null;
         double bestScore = Double.MAX_VALUE;

         for (PlayerEntity other : player.getWorld().getPlayers()) {
            if (other != player && other.isAlive() && !other.isRemoved() && (!antibotOn || !antibot.isBot(other))) {
               double dist = player.distanceTo(other);
               if (!(dist > range) && isAimCandidateValid(player, other, range, throughWalls)) {
                  Vec3d targetPoint = aimPoint(other, 0.55F, 1.0F);
                  if (activeFov < 359.5F) {
                     float yawToTarget = aimYaw(eye, targetPoint);
                     if (Math.abs(MathHelper.wrapDegrees(yawToTarget - viewYaw)) > halfFov) {
                        continue;
                      }
                  }

                  double score = lockTarget ? scoreAimTarget(player, viewYaw, viewPitch, eye, locked, other, dist, priority) : dist;
                  if (score < bestScore) {
                     bestScore = score;
                     best = other;
                  }
               }
            }
         }

         return best;
      }
   }

   private static boolean isAimCandidateValid(ClientPlayerEntity player, PlayerEntity target, float range, boolean throughWalls) {
      if (target.isAlive() && !target.isRemoved()) {
         if (player.distanceTo(target) > range) {
            return false;
         } else if (throughWalls) {
            return true;
         } else {
            Vec3d eye = player.getEyePos();
            Vec3d point = aimPoint(target, 0.55F, 1.0F);
            return hasLineOfSight(player, eye, point) || hasRelaxedLineOfSight(player, target, eye, 0.55F) || player.canSee(target);
         }
      } else {
         return false;
      }
   }

   private static double scoreAimTarget(
      ClientPlayerEntity player, float playerYaw, float playerPitch, Vec3d eye, PlayerEntity current, PlayerEntity candidate, double dist, String priority
   ) {
      Vec3d point = aimPoint(candidate, 0.55F, 1.0F);
      String var10 = priority == null ? "Distance" : priority;

      return switch (var10) {
         case "Yaw" -> crosshairDistanceDegrees(playerYaw, playerPitch, eye, point);
         case "Health" -> candidate.getHealth();
         case "Armor" -> -candidate.getArmor();
         case "Threat" -> player.distanceTo(candidate) / (candidate.getHealth() + 1.0F);
         default -> current != null && candidate.getId() == current.getId()
            ? dist * 0.7
            : (current != null ? dist * switchDistancePenalty(player, eye, playerYaw, playerPitch, current, candidate) : dist);
      };
   }

   private static double switchDistancePenalty(
      ClientPlayerEntity player, Vec3d eye, float playerYaw, float playerPitch, PlayerEntity currentTarget, PlayerEntity candidate
   ) {
      Vec3d cur = aimPoint(currentTarget, 0.55F, 1.0F);
      Vec3d cand = aimPoint(candidate, 0.55F, 1.0F);
      double currentScore = crosshairDistanceDegrees(playerYaw, playerPitch, eye, cur);
      double candidateScore = crosshairDistanceDegrees(playerYaw, playerPitch, eye, cand);
      if (candidateScore < currentScore * 0.72) {
         return 0.92;
      } else {
         return candidateScore < currentScore * 0.88 ? 1.45 : 2.35;
      }
   }

   private static Vec3d aimPoint(PlayerEntity entity, float aimHeightNorm, float partialTicks) {
      Vec3d pos = entity.getLerpedPos(partialTicks);
      double height = entity.getBoundingBox().getLengthY();
      return entity.isFallFlying()
         ? pos.add(0.0, height * 0.5, 0.0)
         : pos.add(0.0, height * MathHelper.clamp(aimHeightNorm, 0.0F, 1.0F), 0.0);
   }

   private static float aimYaw(Vec3d eye, Vec3d target) {
      Vec3d delta = target.subtract(eye);
      return (float)MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
   }

   private static boolean hasRelaxedLineOfSight(ClientPlayerEntity from, PlayerEntity target, Vec3d eye, float aimHeight) {
      return from.getBoundingBox().intersects(target.getBoundingBox().expand(0.15)) ? true : hasLineOfSight(from, eye, aimPoint(target, aimHeight, 1.0F));
   }

   private static boolean hasLineOfSight(ClientPlayerEntity from, Vec3d eye, Vec3d target) {
      HitResult hit = from.getWorld().raycast(new RaycastContext(eye, target, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, from));
      return hit.getType() != HitResult.Type.MISS ? true : hit.getPos().squaredDistanceTo(eye) >= target.squaredDistanceTo(eye) - 0.2;
   }

   private static double crosshairDistanceDegrees(float yaw, float pitch, Vec3d eye, Vec3d aimPoint) {
      Vec3d delta = aimPoint.subtract(eye);
      double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
      float targetYaw = (float)MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
      float targetPitch = (float)MathHelper.wrapDegrees(-Math.toDegrees(Math.atan2(delta.y, horiz)));
      return Math.abs(MathHelper.wrapDegrees(targetYaw - yaw)) + Math.abs(targetPitch - pitch) * 0.65;
   }
}
