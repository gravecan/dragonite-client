package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;


public final class ChamsRenderLayerHelper {
    private ChamsRenderLayerHelper() {}

    public static boolean isGlintLayer(RenderLayer layer) {
        String name = layer.toString().toLowerCase();
        return name.contains("glint") || name.contains("crumbling");
    }

    public static RenderLayer forChams(RenderLayer layer) {
        if (isGlintLayer(layer)) {
            return null;
        }
        Identifier texture = RenderLayerGlassHelper.extractTexture(layer);
        if (texture == null) {
            return layer;
        }
        if (layer.isTranslucent()) {
            return RenderLayer.getEntityTranslucent(texture);
        }
        return RenderLayer.getEntityCutoutNoCull(texture);
    }
}
