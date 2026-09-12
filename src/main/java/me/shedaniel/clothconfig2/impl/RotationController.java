package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;


public class RotationController {
    
    
    private float currentYaw = 0f;
    private float currentPitch = 0f;
    private float lastYaw = 0f;
    private float lastPitch = 0f;
    
    
    private float targetYaw = 0f;
    private float targetPitch = 0f;
    
    
    private float serverYaw = 0f;
    private float serverPitch = 0f;
    
    
    private float smoothSpeed = 0.3f;
    private float acquisitionSpeed = 0.5f; 
    private float lockSpeed = 0.2f;       
    private float jitterAmount = 0.5f;
    private long lastUpdateTime = 0L;
    
    private boolean isSilentAim = false;  
    private int silentDuration = 0;      
    
    
    private boolean isActive = false;
    private boolean needsCorrection = false;
    
    
    public static final RotationController INSTANCE = new RotationController();
    
    private RotationController() {}
    
    
    public void setTargetRotation(float yaw, float pitch, boolean instant) {
        setTargetRotation(yaw, pitch, instant, false, 0);
    }

    public void setTargetRotation(float yaw, float pitch, boolean instant, boolean silent, int duration) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        
        float cameraYaw = mc.player.getYaw();
        float cameraPitch = mc.player.getPitch();
        
        if (!isActive) {
            this.currentYaw = cameraYaw;
            this.currentPitch = cameraPitch;
            this.lastYaw = cameraYaw;
            this.lastPitch = cameraPitch;
        }
        
        this.targetYaw = yaw;
        this.targetPitch = MathHelper.clamp(pitch, -90f, 90f);
        this.isActive = true;
        this.needsCorrection = true;
        this.isSilentAim = silent;
        this.silentDuration = duration;
        
        if (instant) {
            this.currentYaw = this.targetYaw;
            this.currentPitch = this.targetPitch;
        }
        
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    
    private float getSensitivityMultiplier() {
        MinecraftClient mc = MinecraftClient.getInstance();
        float sensitivity = mc.options.getMouseSensitivity().getValue().floatValue();
        float f = sensitivity * 0.6f + 0.2f;
        return f * f * f * 8.0f * 0.15f;
    }

    private float quantize(float value, float multiplier) {
        return Math.round(value / multiplier) * multiplier;
    }

    public void tick() {
        if (!isActive) return;
        
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;
        float totalDiff = (float) Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
        
        
        float t = MathHelper.clamp(1.0f - (totalDiff / 45f), 0.1f, 0.9f);
        float bezierFactor = t * t * (3 - 2 * t); 
        
        float adaptiveSpeed = (totalDiff > 5f) ? acquisitionSpeed : lockSpeed;
        adaptiveSpeed *= (0.5f + bezierFactor * 0.5f);
        
        if (isSilentAim) {
            adaptiveSpeed = 1.0f; 
        }
        
        float jitterYaw = 0f;
        float jitterPitch = 0f;
        if (hasMovementInput(mc)) {
            long time = System.currentTimeMillis();
            jitterYaw = (float) (Math.sin(time / 73.0) * jitterAmount);
            jitterPitch = (float) (Math.cos(time / 97.0) * jitterAmount * 0.5);
        }
        
        lastYaw = currentYaw;
        lastPitch = currentPitch;
        
        float nextYaw = currentYaw + yawDiff * adaptiveSpeed + jitterYaw;
        float nextPitch = currentPitch + pitchDiff * adaptiveSpeed + jitterPitch;
        
        
        float mult = getSensitivityMultiplier();
        currentYaw = MathHelper.wrapDegrees(quantize(nextYaw, mult));
        currentPitch = MathHelper.clamp(quantize(nextPitch, mult), -90f, 90f);
    }
    
    
    public float getSpoofedYaw() {
        return currentYaw;
    }
    
    public float getSpoofedPitch() {
        return currentPitch;
    }
    
    public float getLastYaw() {
        return lastYaw;
    }
    
    public float getLastPitch() {
        return lastPitch;
    }
    
    
    public Vec3d correctVelocity(Vec3d movementInput, float speed, float actualYaw) {
        if (!isActive || !needsCorrection) {
            return calculateVelocity(movementInput, speed, actualYaw);
        }
        
        
        
        return calculateVelocity(movementInput, speed, currentYaw);
    }
    
    
    private Vec3d calculateVelocity(Vec3d movementInput, float speed, float yaw) {
        double d = movementInput.lengthSquared();
        if (d < 1.0E-7) {
            return Vec3d.ZERO;
        }
        
        Vec3d normalized = d > 1.0 ? movementInput.normalize() : movementInput;
        Vec3d scaled = normalized.multiply(speed);
        
        float sin = MathHelper.sin(yaw * 0.017453292f);
        float cos = MathHelper.cos(yaw * 0.017453292f);
        
        return new Vec3d(
            scaled.x * cos - scaled.z * sin,
            scaled.y,
            scaled.z * cos + scaled.x * sin
        );
    }
    
    
    private boolean hasMovementInput(MinecraftClient mc) {
        if (mc.player == null || mc.player.input == null) return false;
        return mc.player.input.movementForward != 0 || mc.player.input.movementSideways != 0;
    }
    
    
    public void startReturn() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        
        
        targetYaw = mc.player.getYaw();
        targetPitch = mc.player.getPitch();
        
        
        smoothSpeed = 0.2f;
    }
    
    
    public boolean shouldStopSpoofing() {
        if (!isActive) return true;
        
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return true;
        
        float cameraYaw = mc.player.getYaw();
        float cameraPitch = mc.player.getPitch();
        
        float yawDiff = MathHelper.wrapDegrees(currentYaw - cameraYaw);
        float pitchDiff = currentPitch - cameraPitch;
        
        float totalDiff = (float) Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
        
        return totalDiff < 2.0f; 
    }
    
    
    public void reset() {
        MinecraftClient mc = MinecraftClient.getInstance();
        
        if (mc != null && mc.player != null) {
            
            currentYaw = mc.player.getYaw();
            currentPitch = mc.player.getPitch();
            lastYaw = currentYaw;
            lastPitch = currentPitch;
            targetYaw = currentYaw;
            targetPitch = currentPitch;
        } else {
            currentYaw = 0f;
            currentPitch = 0f;
            lastYaw = 0f;
            lastPitch = 0f;
            targetYaw = 0f;
            targetPitch = 0f;
        }
        
        isActive = false;
        needsCorrection = false;
        smoothSpeed = 0.3f;
    }
    
    
    public void updateServerRotation(float yaw, float pitch) {
        this.serverYaw = yaw;
        this.serverPitch = pitch;
    }
    
    public boolean isActive() {
        return isActive;
    }

    public boolean isSilentAim() {
        return isSilentAim;
    }

    public void setSpeeds(float smooth, float acquisition, float lock) {
        this.smoothSpeed = smooth;
        this.acquisitionSpeed = acquisition;
        this.lockSpeed = lock;
    }

    public void setJitter(float amount) {
        this.jitterAmount = amount;
    }

    public boolean needsCorrection() {
        return needsCorrection;
    }
    
    
    public float getRotationDifference() {
        float yawDiff = MathHelper.wrapDegrees(currentYaw - targetYaw);
        float pitchDiff = currentPitch - targetPitch;
        return (float) Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
    }
}
