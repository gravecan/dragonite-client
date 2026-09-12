package me.shedaniel.clothconfig2.impl.humanizer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class TargetSelector {
    
    
    private static final int MIN_LOCK_DELAY_MS = 50;
    private static final int MAX_LOCK_DELAY_MS = 150;
    private static final int SWITCH_HESITATION_MS = 80;
    
    
    private static final double SWITCH_DISTANCE_THRESHOLD = 1.5; 
    
    
    private static final float SUBOPTIMAL_SELECTION_CHANCE = 0.15f; 
    
    
    private PlayerEntity lockedTarget = null;
    private long lockStartTime = 0;
    private long switchHesitationEndTime = 0;
    private boolean isHesitating = false;
    
    
    private final PlayerProfile profile;
    
    
    private final ContextAnalyzer context;
    
    public TargetSelector(PlayerProfile profile, ContextAnalyzer context) {
        this.profile = profile;
        this.context = context;
    }
    
    
    public PlayerEntity selectTarget(MinecraftClient mc, List<PlayerEntity> candidates) {
        if (mc.player == null || candidates.isEmpty()) {
            lockedTarget = null;
            return null;
        }
        
        long now = System.currentTimeMillis();
        
        
        candidates.sort(Comparator.comparingDouble(e -> mc.player.distanceTo(e)));
        
        
        PlayerEntity optimalTarget = candidates.get(0);
        
        
        if (lockedTarget == null) {
            
            lockedTarget = pickTargetWithPsychology(mc, candidates);
            lockStartTime = now + MIN_LOCK_DELAY_MS + profile.nextInt(MAX_LOCK_DELAY_MS - MIN_LOCK_DELAY_MS);
            
            
            lockStartTime += context.getReactionTimePenalty();
            
            return null; 
        }
        
        
        if (!candidates.contains(lockedTarget)) {
            
            lockedTarget = null;
            return null;
        }
        
        
        if (now < lockStartTime) {
            return null;
        }
        
        
        if (isHesitating && now < switchHesitationEndTime) {
            return lockedTarget; 
        }
        isHesitating = false;
        
        
        if (!optimalTarget.equals(lockedTarget)) {
            double currentDist = mc.player.distanceTo(lockedTarget);
            double optimalDist = mc.player.distanceTo(optimalTarget);
            
            
            if (currentDist - optimalDist > SWITCH_DISTANCE_THRESHOLD) {
                
                isHesitating = true;
                switchHesitationEndTime = now + SWITCH_HESITATION_MS + profile.nextInt(80);
                
                
                lockedTarget = pickTargetWithPsychology(mc, candidates);
                lockStartTime = now + MIN_LOCK_DELAY_MS + profile.nextInt(MAX_LOCK_DELAY_MS - MIN_LOCK_DELAY_MS);
                
                return null; 
            }
        }
        
        return lockedTarget;
    }
    
    
    private PlayerEntity pickTargetWithPsychology(MinecraftClient mc, List<PlayerEntity> candidates) {
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        
        
        if (profile.nextFloat() < SUBOPTIMAL_SELECTION_CHANCE && candidates.size() > 1) {
            
            int maxIdx = Math.min(3, candidates.size()) - 1;
            int idx = profile.nextInt(maxIdx + 1);
            return candidates.get(idx);
        }
        
        
        return selectByWeightedDistance(mc, candidates);
    }
    
    
    private PlayerEntity selectByWeightedDistance(MinecraftClient mc, List<PlayerEntity> candidates) {
        
        Map<PlayerEntity, Double> weights = new HashMap<>();
        double totalWeight = 0.0;
        
        for (PlayerEntity candidate : candidates) {
            double dist = mc.player.distanceTo(candidate);
            double weight = 1.0 / (dist + 0.5); 
            weights.put(candidate, weight);
            totalWeight += weight;
        }
        
        
        double roll = profile.nextFloat() * totalWeight;
        double cumulative = 0.0;
        
        for (Map.Entry<PlayerEntity, Double> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (roll <= cumulative) {
                return entry.getKey();
            }
        }
        
        
        return candidates.get(0);
    }
    
    
    public PlayerEntity getLockedTarget() {
        return lockedTarget;
    }
    
    
    public boolean isHesitating() {
        return isHesitating;
    }
    
    
    public void forceSwitch() {
        lockedTarget = null;
        isHesitating = false;
    }
    
    
    public void reset() {
        lockedTarget = null;
        lockStartTime = 0;
        switchHesitationEndTime = 0;
        isHesitating = false;
    }
}
