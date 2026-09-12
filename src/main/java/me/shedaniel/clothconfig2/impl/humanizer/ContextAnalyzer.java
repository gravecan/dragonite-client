package me.shedaniel.clothconfig2.impl.humanizer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;


public class ContextAnalyzer {
    
    
    private static final long UNDER_FIRE_DURATION = 500;      
    private static final long SURPRISE_DURATION = 200;        
    private static final long FLINCH_DURATION = 150;          
    
    
    private static final float UNDER_FIRE_ACCURACY_MULT = 0.70f;    
    private static final float UNDER_FIRE_JITTER_MULT = 1.4f;       
    private static final float HIGH_SPEED_ACCURACY_MULT = 0.85f;     
    private static final float AIRBORNE_ACCURACY_MULT = 0.75f;       
    private static final float LOW_HEALTH_ACCURACY_MULT = 0.80f;     
    private static final float SURPRISE_ACCURACY_MULT = 0.70f;       
    private static final float FLINCH_ACCURACY_MULT = 0.50f;         
    
    
    private static final double HIGH_SPEED_THRESHOLD = 0.25;        
    private static final double VERY_HIGH_SPEED_THRESHOLD = 0.4;    
    
    
    private static final float LOW_HEALTH_THRESHOLD = 10.0f;        
    private static final float CRITICAL_HEALTH_THRESHOLD = 6.0f;    
    
    
    private long lastDamageTime = 0;
    private long lastTargetAcquiredTime = 0;
    private PlayerEntity currentTarget = null;
    private int consecutiveHits = 0;
    private int consecutiveMisses = 0;
    private long lastHitTime = 0;
    
    
    private float cachedAccuracyMultiplier = 1.0f;
    private float cachedJitterMultiplier = 1.0f;
    private float cachedSpeedMultiplier = 1.0f;
    private float cachedMissProbabilityMultiplier = 1.0f;
    private int cachedReactionTimePenalty = 0;
    
    
    private final PlayerProfile profile;
    
    public ContextAnalyzer(PlayerProfile profile) {
        this.profile = profile;
    }
    
    
    public void update(MinecraftClient mc, PlayerEntity target) {
        if (mc.player == null) return;
        
        
        if (target != currentTarget) {
            lastTargetAcquiredTime = System.currentTimeMillis();
            currentTarget = target;
        }
        
        
        calculateMultipliers(mc, target);
    }
    
    
    public void notifyDamageTaken() {
        lastDamageTime = System.currentTimeMillis();
    }
    
    
    public void notifyHit() {
        consecutiveHits++;
        consecutiveMisses = 0;
        lastHitTime = System.currentTimeMillis();
    }
    
    
    public void notifyMiss() {
        consecutiveMisses++;
        consecutiveHits = 0;
    }
    
    
    public float getAccuracyMultiplier() {
        return cachedAccuracyMultiplier;
    }
    
    
    public float getJitterMultiplier() {
        return cachedJitterMultiplier;
    }
    
    
    public float getSpeedMultiplier() {
        return cachedSpeedMultiplier;
    }
    
    
    public float getMissProbabilityMultiplier() {
        return cachedMissProbabilityMultiplier;
    }
    
    
    public int getReactionTimePenalty() {
        return cachedReactionTimePenalty;
    }
    
    
    public boolean isUnderFire() {
        return System.currentTimeMillis() - lastDamageTime < UNDER_FIRE_DURATION;
    }
    
    
    public boolean isSurprised() {
        return System.currentTimeMillis() - lastTargetAcquiredTime < SURPRISE_DURATION;
    }
    
    
    public boolean isFlinching() {
        return System.currentTimeMillis() - lastDamageTime < FLINCH_DURATION;
    }
    
    
    public float getStressLevel() {
        float stress = 0f;
        
        if (isUnderFire()) stress += 0.3f;
        if (isFlinching()) stress += 0.2f;
        if (isSurprised()) stress += 0.15f;
        
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null) {
            
            float healthPercent = mc.player.getHealth() / mc.player.getMaxHealth();
            if (healthPercent < 0.5f) stress += 0.15f;
            if (healthPercent < 0.25f) stress += 0.2f;
        }
        
        return Math.min(1.0f, stress);
    }
    
    
    private void calculateMultipliers(MinecraftClient mc, PlayerEntity target) {
        float accuracy = 1.0f;
        float jitter = 1.0f;
        float speed = 1.0f;
        float missProb = 1.0f;
        int reactionPenalty = 0;
        
        
        if (isUnderFire()) {
            accuracy *= UNDER_FIRE_ACCURACY_MULT;
            jitter *= UNDER_FIRE_JITTER_MULT;
            missProb *= 1.3f;
            reactionPenalty += 30;
        }
        
        
        if (isFlinching()) {
            accuracy *= FLINCH_ACCURACY_MULT;
            jitter *= 1.6f;
            speed *= 0.7f;
        }
        
        
        if (isSurprised()) {
            accuracy *= SURPRISE_ACCURACY_MULT;
            missProb *= 1.4f;
            reactionPenalty += 50;
        }
        
        
        Vec3d velocity = mc.player.getVelocity();
        double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        
        if (horizontalSpeed > HIGH_SPEED_THRESHOLD) {
            accuracy *= HIGH_SPEED_ACCURACY_MULT;
            jitter *= 1.15f;
            missProb *= 1.15f;
        }
        if (horizontalSpeed > VERY_HIGH_SPEED_THRESHOLD) {
            accuracy *= 0.9f;
            jitter *= 1.25f;
            missProb *= 1.25f;
        }
        
        
        if (!mc.player.isOnGround()) {
            accuracy *= AIRBORNE_ACCURACY_MULT;
            jitter *= 1.2f;
            speed *= 0.85f;
            missProb *= 1.2f;
        }
        
        
        float health = mc.player.getHealth();
        float maxHealth = mc.player.getMaxHealth();
        float healthPercent = health / maxHealth;
        
        if (health < LOW_HEALTH_THRESHOLD) {
            accuracy *= LOW_HEALTH_ACCURACY_MULT;
            jitter *= 1.15f;
            missProb *= 1.15f;
            reactionPenalty += 20;
        }
        if (health < CRITICAL_HEALTH_THRESHOLD) {
            accuracy *= 0.85f;
            jitter *= 1.3f;
            speed *= 1.1f; 
            missProb *= 1.3f;
            reactionPenalty += 30;
        }
        
        
        float fatigue = profile.getFatigueLevel();
        if (fatigue > 0) {
            accuracy *= profile.getFatigueAccuracyPenalty();
            reactionPenalty += profile.getFatigueReactionPenaltyMs();
            missProb *= 1.0f + fatigue * 0.3f;
        }
        
        
        
        if (consecutiveHits > 8) {
            missProb *= 1.0f + (consecutiveHits - 8) * 0.02f;
        }
        
        
        
        if (consecutiveMisses > 2) {
            missProb *= 0.85f;
            speed *= 0.9f; 
        }
        
        
        if (target != null) {
            double distance = mc.player.distanceTo(target);
            
            if (distance > 4.0) {
                accuracy *= 0.95f;
                missProb *= 1.1f;
            }
            if (distance > 5.0) {
                accuracy *= 0.9f;
                missProb *= 1.2f;
            }
        }
        
        
        accuracy *= (0.9f + profile.accuracyBias * 0.2f);
        speed *= (0.85f + profile.speedBias * 0.3f);
        jitter *= (0.7f + profile.jitterStyle * 0.6f);
        
        
        cachedAccuracyMultiplier = clamp(accuracy, 0.3f, 1.1f);
        cachedJitterMultiplier = clamp(jitter, 0.8f, 2.0f);
        cachedSpeedMultiplier = clamp(speed, 0.5f, 1.2f);
        cachedMissProbabilityMultiplier = clamp(missProb, 0.5f, 2.5f);
        cachedReactionTimePenalty = Math.max(0, reactionPenalty);
    }
    
    
    public float getOverallQuality() {
        return cachedAccuracyMultiplier;
    }
    
    
    public void reset() {
        lastDamageTime = 0;
        lastTargetAcquiredTime = 0;
        currentTarget = null;
        consecutiveHits = 0;
        consecutiveMisses = 0;
        lastHitTime = 0;
        cachedAccuracyMultiplier = 1.0f;
        cachedJitterMultiplier = 1.0f;
        cachedSpeedMultiplier = 1.0f;
        cachedMissProbabilityMultiplier = 1.0f;
        cachedReactionTimePenalty = 0;
    }
    
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
