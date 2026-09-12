package me.shedaniel.clothconfig2.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import me.shedaniel.clothconfig2.impl.builders.ColorFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.FogShape;
import org.joml.Vector4f;

public class Config_CustomFog extends ConfigCategoryImpl {
    public static Config_CustomFog INSTANCE;

    private final EnumSelectorBuilder mode;
    private final ColorFieldBuilder fogColor;
    private final DoubleFieldBuilder fogStart;
    private final DoubleFieldBuilder fogEnd;
    private final DoubleFieldBuilder density;

    public Config_CustomFog() {
        super("Custom Fog", "Disable fog or set custom color and distance", Cat.VISUALS);
        INSTANCE = this;
        mode = new EnumSelectorBuilder("Mode", "", "Remove", "Remove", "Custom");
        fogColor = new ColorFieldBuilder("Color", "", 255, 255, 255).withRainbowOption();
        fogStart = new DoubleFieldBuilder("Start", "Fog begins (blocks)", 6, 0, 200, 0.5);
        fogEnd = new DoubleFieldBuilder("End", "Fog fully opaque (blocks)", 80, 8, 500, 1);
        density = new DoubleFieldBuilder("Density", "Higher = softer / farther fade", 1.0, 0.1, 10, 0.1);
        addSetting(mode);
        addSetting(fogColor);
        addSetting(fogStart);
        addSetting(fogEnd);
        addSetting(density);
        fogColor.setVisibleWhen(() -> "Custom".equals(mode.get()));
        fogStart.setVisibleWhen(() -> "Custom".equals(mode.get()));
        fogEnd.setVisibleWhen(() -> "Custom".equals(mode.get()));
        density.setVisibleWhen(() -> "Custom".equals(mode.get()));
    }

    public boolean isCustomMode() {
        return isEnabled() && "Custom".equals(mode.get());
    }

    public boolean isRemoveMode() {
        return isEnabled() && "Remove".equals(mode.get());
    }

    
    public boolean affectsFogType(BackgroundRenderer.FogType fogType) {
        return fogType == BackgroundRenderer.FogType.FOG_TERRAIN
                || fogType == BackgroundRenderer.FogType.FOG_SKY;
    }

    public Vector4f fogColorVector() {
        RainbowManager mgr = RainbowManager.getInstance();
        if (fogColor.isRainbow()) {
            mgr.update(1.0f);
        }
        float[] rgb = new float[3];
        fogColor.resolveRgb(mgr, 0f, -1, rgb);
        return new Vector4f(rgb[0], rgb[1], rgb[2], 1.0f);
    }

    public void applyAfterVanillaFog(BackgroundRenderer.FogType fogType, float viewDistanceChunks) {
        if (!isEnabled() || !affectsFogType(fogType)) {
            return;
        }

        if (isRemoveMode()) {
            float far = viewDistanceChunks * 16f;
            RenderSystem.setShaderFogShape(FogShape.CYLINDER);
            RenderSystem.setShaderFogStart(far * 0.92f);
            RenderSystem.setShaderFogEnd(far);
            RenderSystem.setShaderFogColor(1f, 1f, 1f, 1f);
            return;
        }

        float start = (float) fogStart.get();
        float end = (float) fogEnd.get();
        float dens = (float) density.get();
        if (dens > 0.01f) {
            start *= dens;
            end *= dens;
        }
        end = Math.max(start + 4f, end);

        RenderSystem.setShaderFogShape(FogShape.CYLINDER);
        RenderSystem.setShaderFogStart(start);
        RenderSystem.setShaderFogEnd(end);
        RainbowManager mgr = RainbowManager.getInstance();
        if (fogColor.isRainbow()) {
            mgr.update(1.0f);
        }
        float[] rgb = new float[3];
        fogColor.resolveRgb(mgr, 0f, -1, rgb);
        RenderSystem.setShaderFogColor(rgb[0], rgb[1], rgb[2], 1.0f);
    }
}
