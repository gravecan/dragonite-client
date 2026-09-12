package me.shedaniel.clothconfig2.impl;

import java.awt.Color;


public class RainbowManager {
    
    private static RainbowManager INSTANCE;
    
    private float globalHue = 0f;
    private long lastUpdate = 0;
    
    public static RainbowManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new RainbowManager();
        }
        return INSTANCE;
    }
    
    
    public void update(float speed) {
        long now = System.currentTimeMillis();
        if (lastUpdate == 0) lastUpdate = now;
        
        
        float delta = (now - lastUpdate) / 1000f * 180f * speed; 
        globalHue = (globalHue + delta) % 360f;
        lastUpdate = now;
    }
    
    
    public float getRainbowHue() {
        return globalHue;
    }
    
    
    public void getRainbowColorInto(float offset, float saturation, float brightness, float[] out) {
        float hue = (globalHue + offset) % 360f;
        Color c = Color.getHSBColor(hue / 360f, saturation, brightness);
        out[0] = c.getRed() / 255f;
        out[1] = c.getGreen() / 255f;
        out[2] = c.getBlue() / 255f;
    }

    public float[] getRainbowColor(float offset, float saturation, float brightness) {
        float[] out = new float[3];
        getRainbowColorInto(offset, saturation, brightness, out);
        return out;
    }


    public float[] getRainbowColor(float offset) {
        return getRainbowColor(offset, 1.0f, 1.0f);
    }


    public void getRainbowColorForDistanceInto(double distance, float[] out) {
        getRainbowColorInto((float) (distance * 2.5f), 1.0f, 1.0f, out);
    }

    public float[] getRainbowColorForDistance(double distance) {
        float[] out = new float[3];
        getRainbowColorForDistanceInto(distance, out);
        return out;
    }
}
