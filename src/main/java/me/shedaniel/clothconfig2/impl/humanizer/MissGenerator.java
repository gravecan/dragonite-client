package me.shedaniel.clothconfig2.impl.humanizer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;


public class MissGenerator {
    
    
    private static final int OVERSHOOT_DURATION_MIN = 120;
    private static final int OVERSHOOT_DURATION_MAX = 200;
    private static final int UNDERSHOOT_DURATION_MIN = 80;
    private static final int UNDERSHOOT_DURATION_MAX = 140;
    private static final int DRIFT_DURATION_MIN = 200;
    private static final int DRIFT_DURATION_MAX = 400;
    private static final int FLICK_DURATION_MIN = 50;
    private static final int FLICK_DURATION_MAX = 100;
    private static final int TREMOR_DURATION_MIN = 150;
    private static final int TREMOR_DURATION_MAX = 300;
    
    
    private static final int RECOVERY_DURATION = 150;
    
    
    private static final float OVERSHOOT_MAG_MIN = 3.0f;
    private static final float OVERSHOOT_MAG_MAX = 10.0f;
    private static final float UNDERSHOOT_MAG_MIN = 2.0f;
    private static final float UNDERSHOOT_MAG_MAX = 6.0f;
    private static final float DRIFT_RATE_MIN = 0.5f;  
    private static final float DRIFT_RATE_MAX = 2.0f;
    private static final float FLICK_MAG_MIN = 5.0f;
    private static final float FLICK_MAG_MAX = 25.0f;
    private static final float TREMOR_AMP_MIN = 0.5f;
    private static final float TREMOR_AMP_MAX = 2.0f;
    
    
    private MissState state = MissState.NONE;
    private PlayerProfile.MissType currentMissType = PlayerProfile.MissType.NONE;
    
    
    private long missStartTime = 0;
    private long missDuration = 0;
    private long recoveryStartTime = 0;
    
    
    private float missMagnitude = 0f;
    private float missDirection = 1f; 
    private float initialYaw = 0f;
    private float initialPitch = 0f;
    
    
    private float driftOffset = 0f;
    private float driftRate = 0f;
    
    
    private float tremorPhase = 0f;
    private float tremorAmplitude = 0f;
    
    
    private final PlayerProfile profile;
    
    
    private final ContextAnalyzer context;
    
    private enum MissState {
        NONE,           
        ACTIVE,         
        RECOVERING      
    }
    
    public MissGenerator(PlayerProfile profile, ContextAnalyzer context) {
        this.profile = profile;
        this.context = context;
    }
    
    
    public boolean shouldGenerateMiss(float baseProbability) {
        if (state != MissState.NONE) return false;
        
        
        float effectiveProb = baseProbability * context.getMissProbabilityMultiplier();
        
        
        effectiveProb *= (0.8f + profile.accuracyBias * 0.4f);
        
        return profile.nextFloat() < effectiveProb;
    }
    
    
    public void startMiss(MinecraftClient mc, PlayerEntity target, float currentYaw, float currentPitch) {
        
        currentMissType = profile.selectMissType();
        state = MissState.ACTIVE;
        missStartTime = System.currentTimeMillis();
        
        initialYaw = currentYaw;
        initialPitch = currentPitch;
        
        
        float difficulty = 1.0f;
        if (target != null && mc.player != null) {
            double dist = mc.player.distanceTo(target);
            difficulty = (float) MathHelper.clamp(dist / 4.0, 0.5, 2.0);
        }
        
        
        float stress = context.getStressLevel();
        difficulty *= (1.0f + stress * 0.5f);
        
        
        switch (currentMissType) {
            case OVERSHOOT:
                initOvershoot(difficulty);
                break;
            case UNDERSHOOT:
                initUndershoot(difficulty);
                break;
            case DRIFT:
                initDrift(difficulty);
                break;
            case FLICK:
                initFlick(difficulty);
                break;
            case TREMOR:
                initTremor(difficulty);
                break;
            default:
                state = MissState.NONE;
        }
    }
    
    private void initOvershoot(float difficulty) {
        missDuration = OVERSHOOT_DURATION_MIN + profile.nextInt(OVERSHOOT_DURATION_MAX - OVERSHOOT_DURATION_MIN);
        missMagnitude = lerp(OVERSHOOT_MAG_MIN, OVERSHOOT_MAG_MAX, profile.nextFloat()) * difficulty;
        missMagnitude *= profile.overshootTendency;
        missDirection = profile.nextFloat() < 0.5f ? 1f : -1f;
    }
    
    private void initUndershoot(float difficulty) {
        missDuration = UNDERSHOOT_DURATION_MIN + profile.nextInt(UNDERSHOOT_DURATION_MAX - UNDERSHOOT_DURATION_MIN);
        missMagnitude = lerp(UNDERSHOOT_MAG_MIN, UNDERSHOOT_MAG_MAX, profile.nextFloat()) * difficulty;
        missMagnitude *= profile.undershootTendency;
        missDirection = -1f; 
    }
    
    private void initDrift(float difficulty) {
        missDuration = DRIFT_DURATION_MIN + profile.nextInt(DRIFT_DURATION_MAX - DRIFT_DURATION_MIN);
        driftRate = lerp(DRIFT_RATE_MIN, DRIFT_RATE_MAX, profile.nextFloat()) * difficulty;
        driftRate *= profile.driftTendency;
        driftOffset = 0f;
        missDirection = profile.nextFloat() < 0.5f ? 1f : -1f;
    }
    
    private void initFlick(float difficulty) {
        missDuration = FLICK_DURATION_MIN + profile.nextInt(FLICK_DURATION_MAX - FLICK_DURATION_MIN);
        missMagnitude = lerp(FLICK_MAG_MIN, FLICK_MAG_MAX, profile.nextFloat()) * difficulty;
        missMagnitude *= profile.flickTendency;
        missDirection = profile.nextFloat() < 0.5f ? 1f : -1f;
    }
    
    private void initTremor(float difficulty) {
        missDuration = TREMOR_DURATION_MIN + profile.nextInt(TREMOR_DURATION_MAX - TREMOR_DURATION_MIN);
        tremorAmplitude = lerp(TREMOR_AMP_MIN, TREMOR_AMP_MAX, profile.nextFloat()) * difficulty;
        tremorPhase = 0f;
    }
    
    
    public float[] updateAndGetOffset(float targetYaw, float targetPitch) {
        if (state == MissState.NONE) {
            return new float[]{0f, 0f};
        }
        
        long now = System.currentTimeMillis();
        long elapsed = now - missStartTime;
        
        if (state == MissState.ACTIVE) {
            if (elapsed >= missDuration) {
                
                state = MissState.RECOVERING;
                recoveryStartTime = now;
                return getRecoveryOffset(elapsed);
            }
            return getActiveOffset(elapsed, targetYaw, targetPitch);
        }
        
        if (state == MissState.RECOVERING) {
            long recoveryElapsed = now - recoveryStartTime;
            if (recoveryElapsed >= RECOVERY_DURATION) {
                
                state = MissState.NONE;
                currentMissType = PlayerProfile.MissType.NONE;
                return new float[]{0f, 0f};
            }
            return getRecoveryOffset(recoveryElapsed);
        }
        
        return new float[]{0f, 0f};
    }
    
    
    private float[] getActiveOffset(long elapsed, float targetYaw, float targetPitch) {
        float progress = (float) elapsed / missDuration;
        
        switch (currentMissType) {
            case OVERSHOOT:
                
                if (progress < 0.7f) {
                    return new float[]{missMagnitude * missDirection, missMagnitude * 0.3f * missDirection};
                } else {
                    float reduction = (progress - 0.7f) / 0.3f;
                    return new float[]{
                        missMagnitude * missDirection * (1f - reduction),
                        missMagnitude * 0.3f * missDirection * (1f - reduction)
                    };
                }
                
            case UNDERSHOOT:
                
                if (progress < 0.5f) {
                    
                    return new float[]{-missMagnitude * progress * 2f, -missMagnitude * 0.2f * progress * 2f};
                } else {
                    
                    return new float[]{-missMagnitude, -missMagnitude * 0.2f};
                }
                
            case DRIFT:
                
                driftOffset += driftRate * (missDuration / 1000f) * 0.05f;
                float driftMax = missMagnitude > 0 ? missMagnitude : 5f;
                driftOffset = MathHelper.clamp(driftOffset, -driftMax, driftMax);
                return new float[]{driftOffset * missDirection, driftOffset * 0.3f * missDirection};
                
            case FLICK:
                
                if (progress < 0.3f) {
                    
                    return new float[]{missMagnitude * missDirection, missMagnitude * 0.5f * missDirection};
                } else {
                    
                    float correctionProgress = (progress - 0.3f) / 0.7f;
                    float remaining = 1f - easeOutQuad(correctionProgress);
                    return new float[]{
                        missMagnitude * missDirection * remaining,
                        missMagnitude * 0.5f * missDirection * remaining
                    };
                }
                
            case TREMOR:
                
                tremorPhase += 0.8f; 
                float tremor = (float) Math.sin(tremorPhase) * tremorAmplitude;
                float tremorPitch = (float) Math.cos(tremorPhase * 1.3f) * tremorAmplitude * 0.6f;
                return new float[]{tremor, tremorPitch};
                
            default:
                return new float[]{0f, 0f};
        }
    }
    
    
    private float[] getRecoveryOffset(long recoveryElapsed) {
        float progress = (float) recoveryElapsed / RECOVERY_DURATION;
        float remaining = 1f - easeOutQuad(progress);
        
        
        switch (currentMissType) {
            case OVERSHOOT:
                return new float[]{missMagnitude * missDirection * remaining * 0.3f, 
                                   missMagnitude * 0.3f * missDirection * remaining * 0.3f};
            case UNDERSHOOT:
                return new float[]{-missMagnitude * remaining * 0.5f, -missMagnitude * 0.2f * remaining * 0.5f};
            case DRIFT:
                return new float[]{driftOffset * remaining, driftOffset * 0.3f * remaining};
            case FLICK:
                return new float[]{missMagnitude * missDirection * remaining * 0.2f,
                                   missMagnitude * 0.5f * missDirection * remaining * 0.2f};
            case TREMOR:
                return new float[]{tremorAmplitude * remaining * 0.5f, tremorAmplitude * 0.6f * remaining * 0.5f};
            default:
                return new float[]{0f, 0f};
        }
    }
    
    
    public boolean isMissActive() {
        return state != MissState.NONE;
    }
    
    
    public PlayerProfile.MissType getCurrentMissType() {
        return currentMissType;
    }
    
    
    public void reset() {
        state = MissState.NONE;
        currentMissType = PlayerProfile.MissType.NONE;
        missStartTime = 0;
        missDuration = 0;
        recoveryStartTime = 0;
        missMagnitude = 0f;
        driftOffset = 0f;
        tremorPhase = 0f;
    }
    
    
    
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
    
    private static float easeOutQuad(float t) {
        return 1f - (1f - t) * (1f - t);
    }
}
