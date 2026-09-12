package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.ColorFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;

public class Config_GlassHands extends ConfigCategoryImpl {
    public static final String MODE_GLASS = "Glass";
    public static final String MODE_OUTLINE = "Outline";
    
    public static final String MODE_GLOW = "Glow";

    public static Config_GlassHands INSTANCE;

    private final EnumSelectorBuilder mode;
    private final DoubleFieldBuilder alpha;
    private final BooleanToggleBuilder hideDepth;
    private final DoubleFieldBuilder blurStrength;
    private final DoubleFieldBuilder blurSteps;
    private final DoubleFieldBuilder glassBrightness;
    private final DoubleFieldBuilder distortStrength;
    private final DoubleFieldBuilder edgeGlow;
    private final DoubleFieldBuilder edgeRadius;
    private final ColorFieldBuilder glassTint;
    private final ColorFieldBuilder glowColor;
    private final DoubleFieldBuilder glowIntensity;
    private final DoubleFieldBuilder outlineWidth;

    public Config_GlassHands() {
        super("Hands", "Liquid glass or colored outline on held items", Cat.VISUALS);
        INSTANCE = this;
        mode = new EnumSelectorBuilder(
                "Mode",
                "Glass = blurred world through hands, Outline = colored edge on held item",
                MODE_GLASS,
                MODE_GLASS,
                MODE_OUTLINE);
        alpha = new DoubleFieldBuilder("Glass Mix", "Legacy mix (unused in liquid glass)", 0.85, 0.05, 1.0, 0.05);
        hideDepth = new BooleanToggleBuilder("See Through", "Draw through blocks (x-ray hands)", false);
        blurStrength = new DoubleFieldBuilder("Blur Strength", "Background blur amount", 3.0, 0.5, 10.0, 0.25);
        blurSteps = new DoubleFieldBuilder("Blur Passes", "Smoother glass with more passes", 3.0, 1.0, 6.0, 1.0);
        glassBrightness = new DoubleFieldBuilder("Glass Brightness", "Brightness of blurred world through glass", 1.15, 0.5, 2.0, 0.05);
        distortStrength = new DoubleFieldBuilder("Edge Distort", "Refraction at hand edges", 0.06, 0.0, 0.25, 0.01);
        edgeGlow = new DoubleFieldBuilder("Edge Highlight", "White fresnel shine on glass edges", 0.12, 0.0, 0.5, 0.02);
        edgeRadius = new DoubleFieldBuilder("Edge Radius", "Edge detection radius (pixels)", 5.0, 1.0, 12.0, 1.0);
        glassTint = new ColorFieldBuilder("Glass Tint", "Optional tint on blur (subtle)", 255, 255, 255).withRainbowOption();
        glowColor = new ColorFieldBuilder("Outline Color", "Edge glow color on held item", 100, 160, 255).withRainbowOption();
        glowIntensity = new DoubleFieldBuilder("Outline Intensity", "Edge glow brightness", 2.2, 0.3, 5.0, 0.1);
        outlineWidth = new DoubleFieldBuilder("Outline Width", "Edge ring thickness (pixels)", 3.5, 1.5, 10.0, 0.5);

        blurStrength.setVisibleWhen(this::isGlassMode);
        blurSteps.setVisibleWhen(this::isGlassMode);
        glassBrightness.setVisibleWhen(this::isGlassMode);
        distortStrength.setVisibleWhen(this::isGlassMode);
        edgeGlow.setVisibleWhen(this::isGlassMode);
        edgeRadius.setVisibleWhen(this::isGlassMode);
        glassTint.setVisibleWhen(this::isGlassMode);
        glowColor.setVisibleWhen(this::isOutlineMode);
        glowIntensity.setVisibleWhen(this::isOutlineMode);
        outlineWidth.setVisibleWhen(this::isOutlineMode);

        addSetting(mode);
        addSetting(hideDepth);
        addSetting(blurStrength);
        addSetting(blurSteps);
        addSetting(glassBrightness);
        addSetting(distortStrength);
        addSetting(edgeGlow);
        addSetting(edgeRadius);
        addSetting(glassTint);
        addSetting(glowColor);
        addSetting(glowIntensity);
        addSetting(outlineWidth);
    }

    public boolean isGlassMode() {
        return MODE_GLASS.equals(mode.get());
    }

    public boolean isOutlineMode() {
        String value = mode.get();
        return MODE_OUTLINE.equals(value) || MODE_GLOW.equals(value);
    }

    
    public boolean isGlowMode() {
        return isOutlineMode();
    }

    public float getAlpha() {
        return (float) alpha.get();
    }

    public boolean seeThrough() {
        return hideDepth.get();
    }

    public float getBlurStrength() {
        return (float) blurStrength.get();
    }

    public int getBlurSteps() {
        return Math.max(1, Math.min(6, (int) Math.round(blurSteps.get())));
    }

    public float getGlassBrightness() {
        return (float) glassBrightness.get();
    }

    public float getDistortStrength() {
        return (float) distortStrength.get();
    }

    public float getEdgeGlow() {
        return (float) edgeGlow.get();
    }

    public float getEdgeRadius() {
        return (float) edgeRadius.get();
    }

    public float getOutlineWidth() {
        return (float) outlineWidth.get();
    }

    
    public float[] getGlassTint() {
        RainbowManager mgr = RainbowManager.getInstance();
        if (ColorFieldBuilder.anyRainbow(glassTint, glowColor)) {
            mgr.update(1.0f);
        }
        float[] rgb = new float[3];
        glassTint.resolveRgb(mgr, 0f, -1, rgb);
        return rgb;
    }

    public float[] getGlowRgb() {
        RainbowManager mgr = RainbowManager.getInstance();
        if (ColorFieldBuilder.anyRainbow(glassTint, glowColor)) {
            mgr.update(1.0f);
        }
        float[] rgb = new float[3];
        glowColor.resolveRgb(mgr, 120f, -1, rgb);
        return rgb;
    }

    public float getGlowIntensity() {
        return (float) glowIntensity.get();
    }
}
