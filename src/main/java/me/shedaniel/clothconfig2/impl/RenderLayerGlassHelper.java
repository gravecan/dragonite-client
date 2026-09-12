package me.shedaniel.clothconfig2.impl;

import java.lang.reflect.Field;
import java.util.Optional;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;


public final class RenderLayerGlassHelper {
    private static final Class<?> MULTI_PHASE_CLASS;
    private static final Class<?> TEXTURE_CLASS;
    private static final Field MULTI_PHASE_PHASES;
    private static final Field PARAMS_TEXTURE;
    private static final Field TEXTURE_ID;

    static {
        Class<?> multiPhase = null;
        Class<?> texture = null;
        Field phases = null;
        Field texField = null;
        Field id = null;
        try {
            multiPhase = Class.forName("net.minecraft.client.render.RenderLayer$MultiPhase");
            texture = Class.forName("net.minecraft.client.render.RenderPhase$Texture");
            Class<?> paramsClass =
                    Class.forName("net.minecraft.client.render.RenderLayer$MultiPhaseParameters");
            phases = field(multiPhase, "phases", "field_21403");
            texField = field(paramsClass, "texture", "field_21406");
            id = field(texture, "id", "field_21397");
        } catch (ReflectiveOperationException ignored) {
        }
        MULTI_PHASE_CLASS = multiPhase;
        TEXTURE_CLASS = texture;
        MULTI_PHASE_PHASES = phases;
        PARAMS_TEXTURE = texField;
        TEXTURE_ID = id;
    }

    private RenderLayerGlassHelper() {}

    private static Field field(Class<?> type, String yarn, String intermediary)
            throws NoSuchFieldException {
        try {
            Field f = type.getDeclaredField(yarn);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            Field f = type.getDeclaredField(intermediary);
            f.setAccessible(true);
            return f;
        }
    }

    public static Identifier extractTexture(RenderLayer layer) {
        if (MULTI_PHASE_CLASS == null
                || !MULTI_PHASE_CLASS.isInstance(layer)
                || MULTI_PHASE_PHASES == null
                || PARAMS_TEXTURE == null
                || TEXTURE_ID == null) {
            return null;
        }
        try {
            Object params = MULTI_PHASE_PHASES.get(layer);
            Object texBase = PARAMS_TEXTURE.get(params);
            if (texBase == null || !TEXTURE_CLASS.isInstance(texBase)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Optional<Identifier> opt = (Optional<Identifier>) TEXTURE_ID.get(texBase);
            return opt == null ? null : opt.orElse(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public static RenderLayer forGlass(RenderLayer layer) {
        String layerName = layer.toString().toLowerCase();
        if (layerName.contains("glint") || layerName.contains("crumbling")) {
            return layer;
        }
        Identifier texture = extractTexture(layer);
        if (texture == null) {
            return layer;
        }
        if (layer.isTranslucent()) {
            return RenderLayer.getEntityAlpha(texture);
        }
        String path = texture.getPath();
        if (path.contains("/item/") || path.startsWith("item/")) {
            return RenderLayer.getItemEntityTranslucentCull(texture);
        }
        return RenderLayer.getEntityAlpha(texture);
    }
}
