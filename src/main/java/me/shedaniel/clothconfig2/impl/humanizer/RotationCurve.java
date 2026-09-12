package me.shedaniel.clothconfig2.impl.humanizer;

import me.shedaniel.clothconfig2.impl.humanizer.AimHumanizer.AimState;


public class RotationCurve {
    
    
    public enum CurveType {
        LINEAR,         
        EASE_IN,        
        EASE_OUT,       
        EASE_IN_OUT,    
        MIN_JERK,       
        SPRING,         
        OVERSHOOT       
    }
    
    
    private float springVelocity = 0f;
    private float springStiffness = 0.15f;
    private float springDamping = 0.45f;
    
    
    private final PlayerProfile profile;
    
    
    private CurveType currentCurve = CurveType.MIN_JERK;
    
    public RotationCurve(PlayerProfile profile) {
        this.profile = profile;
    }
    
    
    public void setCurveForState(AimState state) {
        switch (state) {
            case REACTING:
                currentCurve = CurveType.LINEAR;
                break;
            case ACQUIRING:
                currentCurve = CurveType.EASE_OUT;
                break;
            case LOCKED:
                currentCurve = CurveType.SPRING;
                break;
            case CORRECTING:
                currentCurve = CurveType.EASE_IN;
                break;
            case FAILING:
                currentCurve = CurveType.OVERSHOOT;
                break;
            default:
                currentCurve = CurveType.MIN_JERK;
        }
    }
    
    
    public void setCurveType(CurveType type) {
        this.currentCurve = type;
    }
    
    
    public CurveType getCurveType() {
        return currentCurve;
    }
    
    
    public float getFactor(float progress) {
        
        float variance = 0.9f + profile.nextFloat() * 0.2f; 
        
        switch (currentCurve) {
            case LINEAR:
                return progress * variance;
                
            case EASE_IN:
                
                return progress * progress * variance;
                
            case EASE_OUT:
                
                return (1f - (1f - progress) * (1f - progress)) * variance;
                
            case EASE_IN_OUT:
                
                return (progress * progress * (3f - 2f * progress)) * variance;
                
            case MIN_JERK:
                
                
                return (10f * progress * progress * progress 
                      - 15f * progress * progress * progress * progress
                      + 6f * progress * progress * progress * progress * progress) * variance;
                
            case SPRING:
                
                return progress * variance;
                
            case OVERSHOOT:
                
                if (progress < 0.6f) {
                    
                    float overshootProgress = progress / 0.6f;
                    return (1.2f * easeOutQuad(overshootProgress)) * variance;
                } else {
                    
                    float settleProgress = (progress - 0.6f) / 0.4f;
                    return (1.2f - 0.2f * easeOutQuad(settleProgress)) * variance;
                }
                
            default:
                return progress * variance;
        }
    }
    
    
    public float applySpringRotation(float current, float target, float speed) {
        float diff = angleDiff(target, current);
        
        
        float force = diff * springStiffness;
        
        
        springVelocity = springVelocity * springDamping + force;
        
        
        return current + springVelocity * speed;
    }
    
    
    public float applySpringRotation(float current, float target, float speed, float stiffness, float damping) {
        float diff = angleDiff(target, current);
        float force = diff * stiffness;
        springVelocity = springVelocity * damping + force;
        return current + springVelocity * speed;
    }
    
    
    public void resetSpring() {
        springVelocity = 0f;
    }
    
    
    public float getSpringVelocity() {
        return springVelocity;
    }
    
    
    public static float angleDiff(float target, float current) {
        float diff = target - current;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        return diff;
    }
    
    
    private static float easeOutQuad(float t) {
        return 1f - (1f - t) * (1f - t);
    }
    
    
    private static float easeInQuad(float t) {
        return t * t;
    }
    
    
    public void randomizeCurve() {
        CurveType[] types = CurveType.values();
        currentCurve = types[profile.nextInt(types.length)];
    }
    
    
    public float getRandomFactor(float progress) {
        CurveType original = currentCurve;
        randomizeCurve();
        float factor = getFactor(progress);
        currentCurve = original;
        return factor;
    }
}
