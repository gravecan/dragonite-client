package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;

public class RotationSpoofer {
    public static boolean isSpoofing       = false;
    public static boolean silent           = true;
    public static boolean correctMovement  = false;  
    public static boolean useSpoofedValues = false;

    public static float spoofedYaw   = 0f;
    public static float spoofedPitch = 0f;
    public static float actualYaw    = 0f;
    public static float actualPitch  = 0f;
    public static float lastYaw      = 0f;
    public static float lastPitch    = 0f;

    
    public static float actualForward  = 0f;
    public static float actualSideways = 0f;

    public static void setSpoofAngles(float yaw, float pitch) {
        lastYaw      = spoofedYaw;
        lastPitch    = spoofedPitch;
        spoofedYaw   = yaw;
        spoofedPitch = pitch;
        isSpoofing   = true;
    }

    
    public static void stopSpoofing() {
        isSpoofing       = false;
        correctMovement  = false;   
        useSpoofedValues = false;
        silent           = true;    

        
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null) {
            float realYaw   = mc.player.getYaw();
            float realPitch = mc.player.getPitch();
            spoofedYaw   = realYaw;
            spoofedPitch = realPitch;
            lastYaw      = realYaw;
            lastPitch    = realPitch;
            actualYaw    = realYaw;
            actualPitch  = realPitch;
        } else {
            
            spoofedYaw   = 0f;
            spoofedPitch = 0f;
            lastYaw      = 0f;
            lastPitch    = 0f;
            actualYaw    = 0f;
            actualPitch  = 0f;
        }
    }
}
