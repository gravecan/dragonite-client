package me.shedaniel.clothconfig2.impl.builders;


public class HSVColorPicker {
    
    private float hue = 0f;        
    private float saturation = 1f; 
    private float brightness = 1f; 
    private float alpha = 1f;      
    
    public HSVColorPicker() {}
    
    public HSVColorPicker(int rgb) {
        setFromRGB(rgb);
    }
    
    
    
    public float getHue() { return hue; }
    public float getSaturation() { return saturation; }
    public float getBrightness() { return brightness; }
    public float getAlpha() { return alpha; }
    
    
    
    public void setHue(float h) { 
        this.hue = clamp(h, 0f, 360f); 
    }
    
    public void setSaturation(float s) { 
        this.saturation = clamp(s, 0f, 1f); 
    }
    
    public void setBrightness(float b) { 
        this.brightness = clamp(b, 0f, 1f); 
    }
    
    public void setAlpha(float a) { 
        this.alpha = clamp(a, 0f, 1f); 
    }
    
    
    
    
    public int toRGB() {
        return hsvToRgb(hue, saturation, brightness);
    }
    
    
    public int toARGB() {
        int rgb = toRGB();
        int a = (int)(alpha * 255);
        return (a << 24) | rgb;
    }
    
    
    public void setFromRGB(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        
        float[] hsv = rgbToHsv(r, g, b);
        this.hue = hsv[0];
        this.saturation = hsv[1];
        this.brightness = hsv[2];
    }
    
    
    public void setFromRGB(int r, int g, int b) {
        float[] hsv = rgbToHsv(r, g, b);
        this.hue = hsv[0];
        this.saturation = hsv[1];
        this.brightness = hsv[2];
    }
    
    
    
    
    public static int hsvToRgb(float h, float s, float v) {
        h = h % 360f;
        if (h < 0) h += 360f;
        
        float c = v * s;
        float x = c * (1 - Math.abs((h / 60f) % 2 - 1));
        float m = v - c;
        
        float r1, g1, b1;
        
        if (h < 60) {
            r1 = c; g1 = x; b1 = 0;
        } else if (h < 120) {
            r1 = x; g1 = c; b1 = 0;
        } else if (h < 180) {
            r1 = 0; g1 = c; b1 = x;
        } else if (h < 240) {
            r1 = 0; g1 = x; b1 = c;
        } else if (h < 300) {
            r1 = x; g1 = 0; b1 = c;
        } else {
            r1 = c; g1 = 0; b1 = x;
        }
        
        int r = (int)((r1 + m) * 255);
        int g = (int)((g1 + m) * 255);
        int b = (int)((b1 + m) * 255);
        
        return (r << 16) | (g << 8) | b;
    }
    
    
    public static float[] rgbToHsv(int r, int g, int b) {
        float rf = r / 255f;
        float gf = g / 255f;
        float bf = b / 255f;
        
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;
        
        float h, s, v;
        
        
        if (delta == 0) {
            h = 0;
        } else if (max == rf) {
            h = 60f * (((gf - bf) / delta) % 6);
        } else if (max == gf) {
            h = 60f * ((bf - rf) / delta + 2);
        } else {
            h = 60f * ((rf - gf) / delta + 4);
        }
        
        if (h < 0) h += 360f;
        
        
        s = (max == 0) ? 0 : delta / max;
        
        
        v = max;
        
        return new float[] { h, s, v };
    }
    
    
    public static int getHueColor(float hue) {
        return hsvToRgb(hue, 1f, 1f);
    }
    
    
    
    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }
    
    
    public int getRed() { return (toRGB() >> 16) & 0xFF; }
    public int getGreen() { return (toRGB() >> 8) & 0xFF; }
    public int getBlue() { return toRGB() & 0xFF; }
    
    
    public java.awt.Color toAwtColor() {
        return new java.awt.Color(getRed(), getGreen(), getBlue(), (int)(alpha * 255));
    }
    
    
    public static HSVColorPicker fromAwtColor(java.awt.Color c) {
        HSVColorPicker picker = new HSVColorPicker();
        picker.setFromRGB(c.getRed(), c.getGreen(), c.getBlue());
        picker.setAlpha(c.getAlpha() / 255f);
        return picker;
    }
}
