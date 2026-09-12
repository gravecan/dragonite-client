package me.shedaniel.clothconfig2.impl.humanizer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;


public class AimHumanizer {
    
    
    public enum AimState {
        IDLE,           
        REACTING,       
        ACQUIRING,      
        LOCKED,         
        CORRECTING,     
        FAILING         
    }
    
    
    private AimState currentState = AimState.IDLE;
    private long stateStartTime = 0;
    
    
    public final PlayerProfile profile;
    public final ContextAnalyzer context;
    public final TargetSelector selector;
    public final MissGenerator missGen;
    public final RotationCurve curve;
    
    
    private float silentYaw = 0f;
    private float silentPitch = 0f;
    private boolean hasSilentRotation = false;
    
    
    private PlayerEntity currentTarget = null;
    private boolean targetChanged = false;
    
    
    private float currentHeightOffset = 0.65f;
    private float targetHeightOffset = 0.65f;
    
    
    private float hitboxOffsetX = 0f;
    private float hitboxOffsetZ = 0f;
    
    
    private long lastUpdateTime = 0;
    private float microDriftPhase = 0f;
    
    
    private float missProbability = 0.12f;      
    private float humanizationStrength = 1.0f;   
    private boolean contextSensitivityEnabled = true;
    
    
    public static AimHumanizer INSTANCE;
    
    public AimHumanizer() {
        this.profile = new PlayerProfile();
        this.context = new ContextAnalyzer(profile);
        this.selector = new TargetSelector(profile, context);
        this.missGen = new MissGenerator(profile, context);
        this.curve = new RotationCurve(profile);
        INSTANCE = this;
    }
    
    
    public boolean update(MinecraftClient mc, List<PlayerEntity> candidates) {
        if (mc.player == null) {
            reset();
            return false;
        }
        
        long now = System.currentTimeMillis();
        float deltaTime = (now - lastUpdateTime) / 1000f;
        lastUpdateTime = now;
        
        
        PlayerEntity selectedTarget = selector.selectTarget(mc, candidates);
        context.update(mc, selectedTarget);
        
        
        targetChanged = (currentTarget != selectedTarget);
        if (targetChanged) {
            currentTarget = selectedTarget;
            onTargetChange();
        }
        
        
        if (currentTarget == null) {
            transitionTo(AimState.IDLE);
            hasSilentRotation = false;
            return false;
        }
        
        
        updateStateMachine(mc, deltaTime);
        
        
        calculateRotation(mc, deltaTime);
        
        return hasSilentRotation;
    }
    
    
    private void updateStateMachine(MinecraftClient mc, float deltaTime) {
        long now = System.currentTimeMillis();
        long stateDuration = now - stateStartTime;
        
        switch (currentState) {
            case IDLE:
                if (currentTarget != null) {
                    transitionTo(AimState.REACTING);
                }
                break;
                
            case REACTING:
                
                int reactionTime = profile.generateReactionTime();
                reactionTime += context.getReactionTimePenalty();
                
                if (stateDuration >= reactionTime) {
                    
                    if (missGen.shouldGenerateMiss(missProbability * humanizationStrength)) {
                        missGen.startMiss(mc, currentTarget, 
                            hasSilentRotation ? silentYaw : mc.player.getYaw(),
                            hasSilentRotation ? silentPitch : mc.player.getPitch());
                        transitionTo(AimState.FAILING);
                    } else {
                        transitionTo(AimState.ACQUIRING);
                    }
                }
                break;
                
            case ACQUIRING:
                
                float acquireProgress = getRotationProgress(mc);
                if (acquireProgress > 0.9f) {
                    transitionTo(AimState.LOCKED);
                }
                
                
                if (missGen.shouldGenerateMiss(missProbability * humanizationStrength * 0.5f)) {
                    missGen.startMiss(mc, currentTarget, silentYaw, silentPitch);
                    transitionTo(AimState.FAILING);
                }
                break;
                
            case LOCKED:
                
                if (missGen.shouldGenerateMiss(missProbability * humanizationStrength * 0.3f)) {
                    missGen.startMiss(mc, currentTarget, silentYaw, silentPitch);
                    transitionTo(AimState.FAILING);
                }
                break;
                
            case CORRECTING:
                
                if (stateDuration > 200) {
                    transitionTo(AimState.LOCKED);
                }
                break;
                
            case FAILING:
                
                if (!missGen.isMissActive()) {
                    transitionTo(AimState.CORRECTING);
                }
                break;
        }
        
        
        curve.setCurveForState(currentState);
    }
    
    
    private void calculateRotation(MinecraftClient mc, float deltaTime) {
        if (currentTarget == null) return;
        
        Vec3d eyePos = mc.player.getEyePos();
        Box box = currentTarget.getBoundingBox();
        
        
        updateBodyPartTargeting();
        
        
        currentHeightOffset += (targetHeightOffset - currentHeightOffset) * 0.12f;
        
        
        Vec3d aimPoint = calculateHumanizedAimPoint(box, eyePos);
        
        
        aimPoint = applyVelocityPrediction(aimPoint, currentTarget);
        
        
        float[] targetAngles = calculateAngles(eyePos, aimPoint);
        float targetYaw = targetAngles[0];
        float targetPitch = targetAngles[1];
        
        
        if (currentState == AimState.FAILING && missGen.isMissActive()) {
            float[] missOffset = missGen.updateAndGetOffset(targetYaw, targetPitch);
            targetYaw += missOffset[0];
            targetPitch += missOffset[1];
        }
        
        
        float currentYaw = hasSilentRotation ? silentYaw : mc.player.getYaw();
        float currentPitch = hasSilentRotation ? silentPitch : mc.player.getPitch();
        
        
        float yawDelta = RotationCurve.angleDiff(targetYaw, currentYaw);
        float pitchDelta = RotationCurve.angleDiff(targetPitch, currentPitch);
        
        
        float baseSpeed = 0.35f * context.getSpeedMultiplier();
        baseSpeed *= (0.85f + profile.speedBias * 0.3f);
        
        
        float newYaw, newPitch;
        
        if (currentState == AimState.LOCKED && curve.getCurveType() == RotationCurve.CurveType.SPRING) {
            
            newYaw = curve.applySpringRotation(currentYaw, targetYaw, baseSpeed);
            newPitch = curve.applySpringRotation(currentPitch, targetPitch, baseSpeed * 0.8f);
        } else {
            
            float progress = getRotationProgress(mc);
            float curveFactor = curve.getFactor(progress);
            
            newYaw = currentYaw + yawDelta * baseSpeed * curveFactor;
            newPitch = currentPitch + pitchDelta * baseSpeed * curveFactor * 0.85f;
        }
        
        
        if (currentState == AimState.LOCKED) {
            microDriftPhase += deltaTime * 2f;
            float drift = (float) Math.sin(microDriftPhase) * 0.15f * profile.jitterStyle;
            newYaw += drift;
            newPitch += drift * 0.5f;
        }
        
        
        if (contextSensitivityEnabled) {
            float jitterAmount = 0.1f * context.getJitterMultiplier() * profile.jitterStyle;
            float jitterYaw = (profile.nextFloat() - 0.5f) * jitterAmount;
            float jitterPitch = (profile.nextFloat() - 0.5f) * jitterAmount * 0.6f;
            newYaw += jitterYaw;
            newPitch += jitterPitch;
        }
        
        
        silentYaw = MathHelper.wrapDegrees(newYaw);
        silentPitch = MathHelper.clamp(newPitch, -90f, 90f);
        hasSilentRotation = true;
    }
    
    
    private Vec3d calculateHumanizedAimPoint(Box box, Vec3d eyePos) {
        
        double cx = (box.minX + box.maxX) / 2.0;
        double cy = box.minY + (box.maxY - box.minY) * currentHeightOffset;
        double cz = (box.minZ + box.maxZ) / 2.0;
        
        
        hitboxOffsetX += (profile.nextFloat() - 0.5f) * 0.02f;
        hitboxOffsetZ += (profile.nextFloat() - 0.5f) * 0.02f;
        hitboxOffsetX *= 0.95f; 
        hitboxOffsetZ *= 0.95f;
        
        
        double maxXOffset = (box.maxX - box.minX) * 0.3;
        double maxZOffset = (box.maxZ - box.minZ) * 0.3;
        hitboxOffsetX = (float) MathHelper.clamp(hitboxOffsetX, -maxXOffset, maxXOffset);
        hitboxOffsetZ = (float) MathHelper.clamp(hitboxOffsetZ, -maxZOffset, maxZOffset);
        
        
        cx += hitboxOffsetX;
        cz += hitboxOffsetZ;
        
        
        float accuracy = context.getAccuracyMultiplier();
        double spread = 0.05 * humanizationStrength * (1.5f - accuracy);
        cx += profile.nextGaussian() * spread;
        cy += profile.nextGaussian() * spread * 0.5;
        cz += profile.nextGaussian() * spread;
        
        
        if (profile.nextFloat() < 0.1f) {
            cx = profile.nextFloat() < 0.5f ? box.minX + 0.1 : box.maxX - 0.1;
        }
        
        return new Vec3d(cx, cy, cz);
    }
    
    
    private Vec3d applyVelocityPrediction(Vec3d aimPoint, PlayerEntity target) {
        Vec3d velocity = target.getVelocity();
        
        
        double predictionTicks = 1.5 + profile.speedBias * 0.5;
        
        return aimPoint.add(
            velocity.x * predictionTicks * 0.5,
            velocity.y * predictionTicks * 0.3,
            velocity.z * predictionTicks * 0.5
        );
    }
    
    
    private float[] calculateAngles(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));
        
        return new float[]{yaw, pitch};
    }
    
    
    private float getRotationProgress(MinecraftClient mc) {
        if (!hasSilentRotation) return 0f;
        
        float yawDiff = Math.abs(RotationCurve.angleDiff(silentYaw, mc.player.getYaw()));
        float pitchDiff = Math.abs(silentPitch - mc.player.getPitch());
        
        
        float maxDiff = 180f;
        float currentDiff = (yawDiff + pitchDiff) / 2f;
        
        return MathHelper.clamp(1f - (currentDiff / maxDiff), 0f, 1f);
    }
    
    
    private void updateBodyPartTargeting() {
        
        float roll = profile.nextFloat();
        
        if (roll < 0.35f) {
            targetHeightOffset = 0.65f; 
        } else if (roll < 0.55f) {
            targetHeightOffset = 0.50f; 
        } else if (roll < 0.70f) {
            targetHeightOffset = 0.85f; 
        } else if (roll < 0.85f) {
            targetHeightOffset = 0.30f; 
        } else {
            targetHeightOffset = 0.10f; 
        }
    }
    
    
    private void transitionTo(AimState newState) {
        if (currentState == newState) return;
        
        currentState = newState;
        stateStartTime = System.currentTimeMillis();
        
        
        if (newState == AimState.ACQUIRING || newState == AimState.CORRECTING) {
            curve.resetSpring();
        }
    }
    
    
    private void onTargetChange() {
        transitionTo(AimState.REACTING);
        curve.resetSpring();
        missGen.reset();
        
        
        hitboxOffsetX = 0f;
        hitboxOffsetZ = 0f;
    }
    
    
    public void notifyDamageTaken() {
        context.notifyDamageTaken();
    }
    
    
    public void notifyHit() {
        context.notifyHit();
    }
    
    
    public void notifyMiss() {
        context.notifyMiss();
    }
    
    
    
    public float getSilentYaw() { return silentYaw; }
    public float getSilentPitch() { return silentPitch; }
    public boolean hasSilentRotation() { return hasSilentRotation; }
    public AimState getCurrentState() { return currentState; }
    public PlayerEntity getCurrentTarget() { return currentTarget; }
    
    
    
    public void setMissProbability(float prob) { this.missProbability = prob; }
    public void setHumanizationStrength(float strength) { this.humanizationStrength = strength; }
    public void setContextSensitivity(boolean enabled) { this.contextSensitivityEnabled = enabled; }
    
    public float getMissProbability() { return missProbability; }
    public float getHumanizationStrength() { return humanizationStrength; }
    public boolean isContextSensitivityEnabled() { return contextSensitivityEnabled; }
    
    
    public void reset() {
        currentState = AimState.IDLE;
        currentTarget = null;
        hasSilentRotation = false;
        targetChanged = false;
        hitboxOffsetX = 0f;
        hitboxOffsetZ = 0f;
        microDriftPhase = 0f;
        
        selector.reset();
        missGen.reset();
        curve.resetSpring();
    }
    
    
    public void fullReset() {
        reset();
        context.reset();
    }
}
