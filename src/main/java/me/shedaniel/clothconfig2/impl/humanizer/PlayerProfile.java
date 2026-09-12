package me.shedaniel.clothconfig2.impl.humanizer;

import java.util.Random;


public class PlayerProfile {
    
    
    public final float speedBias;
    
    
    public final float accuracyBias;
    
    
    public final float jitterStyle;
    
    
    public final float correctionDelay;
    
    
    public final float overshootTendency;
    
    
    public final float undershootTendency;
    
    
    public final float driftTendency;
    
    
    public final float flickTendency;
    
    
    public final float reactionTimeMultiplier;
    
    
    public final long sessionSeed;
    
    
    public final Random sessionRandom;
    
    
    private long sessionStartTime;
    private float fatigueLevel = 0f;
    
    
    public PlayerProfile() {
        this.sessionSeed = System.nanoTime() + System.currentTimeMillis();
        this.sessionRandom = new Random(sessionSeed);
        this.sessionStartTime = System.currentTimeMillis();
        
        
        
        
        
        this.speedBias = clamp(0.5f + (float) nextGaussian() * 0.15f, 0.2f, 0.8f);
        
        
        this.accuracyBias = clamp(0.6f + (float) nextGaussian() * 0.12f, 0.3f, 0.9f);
        
        
        this.jitterStyle = clamp(0.3f + (float) nextGaussian() * 0.15f, 0.1f, 0.7f);
        
        
        this.correctionDelay = clamp(0.4f + (float) nextGaussian() * 0.12f, 0.2f, 0.7f);
        
        
        this.overshootTendency = clamp(0.5f + (float) nextGaussian() * 0.15f, 0.25f, 0.75f);
        this.undershootTendency = clamp(0.35f + (float) nextGaussian() * 0.12f, 0.15f, 0.55f);
        this.driftTendency = clamp(0.25f + (float) nextGaussian() * 0.1f, 0.1f, 0.45f);
        this.flickTendency = clamp(0.2f + (float) nextGaussian() * 0.1f, 0.1f, 0.4f);
        
        
        this.reactionTimeMultiplier = clamp(1.0f + (float) nextGaussian() * 0.05f, 0.85f, 1.15f);
    }
    
    
    public float getFatigueLevel() {
        long sessionDuration = System.currentTimeMillis() - sessionStartTime;
        
        long fatigueStartMs = 20 * 60 * 1000;
        if (sessionDuration > fatigueStartMs) {
            fatigueLevel = Math.min(0.3f, (float)((sessionDuration - fatigueStartMs) / (2.0 * 60 * 60 * 1000)));
        }
        return fatigueLevel;
    }
    
    
    public float getSessionDurationMinutes() {
        return (System.currentTimeMillis() - sessionStartTime) / (60f * 1000f);
    }
    
    
    public float getFatigueAccuracyPenalty() {
        return 1.0f - fatigueLevel * 0.5f; 
    }
    
    
    public int getFatigueReactionPenaltyMs() {
        return (int) (fatigueLevel * 50f); 
    }
    
    
    public float nextFloat() {
        return sessionRandom.nextFloat();
    }
    
    
    public double nextGaussian() {
        return sessionRandom.nextGaussian();
    }
    
    
    public int nextInt(int bound) {
        return sessionRandom.nextInt(bound);
    }
    
    
    public double nextGamma(double shape, double scale) {
        
        if (shape < 1) {
            return nextGamma(shape + 1, scale) * Math.pow(nextFloat(), 1.0 / shape);
        }
        
        double d = shape - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);
        
        while (true) {
            double x, v;
            do {
                x = nextGaussian();
                v = 1.0 + c * x;
            } while (v <= 0);
            
            v = v * v * v;
            double u = nextFloat();
            
            if (u < 1.0 - 0.0331 * (x * x) * (x * x)) {
                return d * v * scale;
            }
            
            if (Math.log(u) < 0.5 * x * x + d * (1.0 - v + Math.log(v))) {
                return d * v * scale;
            }
        }
    }
    
    
    public int generateReactionTime() {
        
        
        
        double reaction = nextGamma(2.0, 60.0);
        
        
        reaction *= reactionTimeMultiplier;
        
        
        reaction += getFatigueReactionPenaltyMs();
        
        
        return (int) clamp((float)reaction, 100, 350);
    }
    
    
    public MissType selectMissType() {
        float roll = nextFloat();
        float cumulative = 0f;
        
        
        cumulative += 0.35f * overshootTendency;
        if (roll < cumulative) return MissType.OVERSHOOT;
        
        
        cumulative += 0.25f * undershootTendency;
        if (roll < cumulative) return MissType.UNDERSHOOT;
        
        
        cumulative += 0.20f * driftTendency;
        if (roll < cumulative) return MissType.DRIFT;
        
        
        cumulative += 0.15f * flickTendency;
        if (roll < cumulative) return MissType.FLICK;
        
        
        return MissType.TREMOR;
    }
    
    
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    
    
    public enum MissType {
        NONE,
        OVERSHOOT,    
        UNDERSHOOT,   
        DRIFT,        
        FLICK,        
        TREMOR        
    }
}
