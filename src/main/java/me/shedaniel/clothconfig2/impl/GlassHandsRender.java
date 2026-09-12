package me.shedaniel.clothconfig2.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;


public final class GlassHandsRender {
    private static int handPassDepth;
    private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Identifier> FALLBACK_TEXTURE = new ThreadLocal<>();

    private GlassHandsRender() {}

    public static boolean isActive() {
        Config_GlassHands mod = Config_GlassHands.INSTANCE;
        return mod != null && mod.isEnabled();
    }

    public static boolean isHandPass() {
        return isActive() && handPassDepth > 0;
    }

    public static boolean usePostProcess() {
        return isHandPass() && HandCompositeHelper.isReady();
    }

    public static boolean isReentrantGetBuffer() {
        return Boolean.TRUE.equals(REENTRANT.get());
    }

    public static void setReentrantGetBuffer(boolean value) {
        REENTRANT.set(value);
    }

    public static boolean isFirstPersonMode(ModelTransformationMode mode) {
        return mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND
                || mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND;
    }

    public static float getAlpha() {
        Config_GlassHands mod = Config_GlassHands.INSTANCE;
        return mod == null ? 1f : mod.getAlpha();
    }

    public static Identifier getFallbackTexture() {
        Identifier item = FALLBACK_TEXTURE.get();
        if (item != null) {
            return item;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            return mc.player.getSkinTextures().texture();
        }
        return null;
    }

    public static void setItemFallback(ItemStack stack) {
        if (!isActive() || stack == null || stack.isEmpty()) {
            FALLBACK_TEXTURE.remove();
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return;
        }
        try {
            var model = mc.getItemRenderer().getModel(stack, mc.world, mc.player, 0);
            Sprite sprite = model.getParticleSprite();
            Identifier id = sprite.getContents().getId();
            FALLBACK_TEXTURE.set(id);
        } catch (RuntimeException ignored) {
            FALLBACK_TEXTURE.remove();
        }
    }

    public static void clearItemFallback() {
        FALLBACK_TEXTURE.remove();
    }

    
    public static void beginHandPass() {
        if (!isActive()) {
            return;
        }
        Config_GlassHands mod = Config_GlassHands.INSTANCE;
        handPassDepth++;
        if (handPassDepth > 1) {
            return;
        }

        HandCompositeHelper.capturePreHand(mod);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (mod.seeThrough()) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        }
    }

    public static void endHandPass() {
        clearItemFallback();
        if (!isActive() || handPassDepth <= 0) {
            return;
        }
        handPassDepth--;
        if (handPassDepth > 0) {
            return;
        }

        Config_GlassHands mod = Config_GlassHands.INSTANCE;
        HandCompositeHelper.applyPostProcess(mod);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }
}
