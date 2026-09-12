package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.internal.IntegrityProbe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;


public class HitRegistration {
    
    
    private static Entity lastTarget = null;
    private static long lastAttackTime = 0;
    private static long lastTargetTime = 0;
    private static long lastConfirmedTime = 0;
    private static boolean wasGhostHit = false;
    private static boolean hitConfirmed = false;
    private static int lastTargetId = -1;
    
    
    private static boolean wasSprinting = false;
    private static boolean wasFalling = false;
    private static boolean hadCooldown = false;
    
    
    private static boolean enabled = true;
    
    public static void setEnabled(boolean e) { enabled = e; }
    public static boolean wasGhostHit() { return wasGhostHit; }
    public static boolean isHitConfirmed() { return hitConfirmed; }

    public static Entity getLastTarget() { return lastTarget; }
    public static long getLastAttackTime() { return lastAttackTime; }
    
    
    public static void onAttack(MinecraftClient mc, Entity target) {
        if (mc.player == null || mc.world == null) return;
        if (!enabled) return;
        if (!IntegrityProbe.passMid(0x5C)) return;
        if (me.shedaniel.clothconfig2.internal.SecurityVault.isArmed()
                && !me.shedaniel.clothconfig2.internal.SecurityVault.invariantHolds()) {
            return;
        }

        long now = System.currentTimeMillis();

        
        
        boolean isEarlyHit = lastAttackTime != 0 && (now - lastAttackTime) <= 450;

        
        
        lastTarget = target;
        lastTargetId = target.getId();
        lastAttackTime = now;
        lastTargetTime = now; 
        hitConfirmed = false;
        wasGhostHit = false;
        
        if (isEarlyHit) {
            if (target instanceof LivingEntity) {
                OverlayRenderer hud = HudConfigInit.getManager() != null
                    ? HudConfigInit.getManager().getModuleByClass(OverlayRenderer.class)
                    : null;
                if (hud != null) {
                    hud.triggerHitShake();
                }
            }
            return;
        }
        
        
        wasSprinting = mc.player.isSprinting();
        wasFalling = mc.player.getVelocity().y < -0.08;
        hadCooldown = mc.player.getAttackCooldownProgress(0.5f) <= 0.9f;
        
        
        playHitEffects(mc, target);
        
        
        if (target instanceof LivingEntity) {
            OverlayRenderer hud = HudConfigInit.getManager() != null 
                ? HudConfigInit.getManager().getModuleByClass(OverlayRenderer.class) 
                : null;
            if (hud != null) {
                hud.triggerHitShake();
            }
        }
    }
    
    
    public static void onDirectAttack(MinecraftClient mc, Entity target) {
        if (mc.player == null || mc.world == null || target == null) return;
        if (!enabled) return;
        
        long now = System.currentTimeMillis();
        
        
        lastTarget = target;
        lastTargetId = target.getId();
        lastAttackTime = now;
        hitConfirmed = true; 
        wasGhostHit = false;
        
        wasSprinting = mc.player.isSprinting();
        wasFalling = mc.player.getVelocity().y < -0.08;
        hadCooldown = mc.player.getAttackCooldownProgress(0.5f) <= 0.9f;
        
        playHitEffects(mc, target);
        
        
        if (target instanceof LivingEntity) {
            OverlayRenderer hud = HudConfigInit.getManager() != null 
                ? HudConfigInit.getManager().getModuleByClass(OverlayRenderer.class) 
                : null;
            if (hud != null) {
                hud.triggerHitShake();
            }
        }
    }
    
    
    public static void onDamagePacket(int entityId) {
        if (entityId == lastTargetId) {
            hitConfirmed = true;
            lastConfirmedTime = System.currentTimeMillis();
            wasGhostHit = false;
        }
    }
    
    
    public static void tick(MinecraftClient mc) {
        if (!enabled || lastTarget == null || lastAttackTime == 0) return;
        
        long now = System.currentTimeMillis();
        
        
        
        if (!hitConfirmed && !wasGhostHit && (now - lastAttackTime) > 800) {
            if (lastTarget.isAlive() && mc.player != null && mc.player.isAlive()) {
                wasGhostHit = true;
            }
        }
        
        
        if (!lastTarget.isAlive()) {
            lastTarget = null;
            lastTargetId = -1;
            hitConfirmed = false;
            wasGhostHit = false;
            lastAttackTime = 0;
        }
    }
    
    
    private static void playHitEffects(MinecraftClient mc, Entity target) {
        if (mc.world == null || mc.player == null) return;
        
        if (Config_Hitsound.INSTANCE != null && Config_Hitsound.INSTANCE.isEnabled()) {
            Config_Hitsound.INSTANCE.playHitSound();
        }
        
        
        
        Vec3d pos = target.getPos();
        
        
        if (!hadCooldown && wasFalling) {
            
            mc.world.addParticle(ParticleTypes.CRIT, 
                pos.x, pos.y + target.getHeight() / 2, pos.z,
                0, 0.1, 0);
            mc.world.addParticle(ParticleTypes.CRIT,
                pos.x + 0.3, pos.y + target.getHeight() / 2, pos.z,
                0, 0.1, 0);
            mc.world.addParticle(ParticleTypes.CRIT,
                pos.x - 0.3, pos.y + target.getHeight() / 2, pos.z,
                0, 0.1, 0);
        }
        
        
        if (mc.player.getMainHandStack().getItem() instanceof net.minecraft.item.SwordItem) {
            mc.world.addParticle(ParticleTypes.SWEEP_ATTACK,
                pos.x, pos.y + 0.5, pos.z,
                0, 0, 0);
        }
    }
}
