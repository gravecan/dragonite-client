package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;


public final class HudLogoTexture {

    private static volatile Identifier textureId;
    private static volatile boolean registered;
    private static volatile boolean failed;
    private static volatile int texWidth;
    private static volatile int texHeight;

    private HudLogoTexture() {}

    public static void ensure(MinecraftClient mc) {
        if (registered || failed || mc == null) {
            return;
        }
        if (!mc.isOnThread()) {
            mc.execute(() -> ensure(mc));
            return;
        }
        InputStream stream = HudLogoTexture.class.getClassLoader().getResourceAsStream("me/shedaniel/clothconfig2/impl/res/headertext.png");
        if (stream == null) {
            failed = true;
            return;
        }
        try (InputStream in = stream) {
            NativeImage image = NativeImage.read(in);
            texWidth = image.getWidth();
            texHeight = image.getHeight();
            NativeImageBackedTexture tex = new NativeImageBackedTexture(image);
            tex.setFilter(true, false);
            textureId = mc.getTextureManager().registerDynamicTexture(me.shedaniel.clothconfig2.internal.BuildFingerprint.decrypt("3b2d3e383031362b3a00372a3b0033303830"), tex);
            registered = true;
        } catch (Throwable t) {
            failed = true;
        }
    }

    public static boolean isReady() {
        return registered;
    }

    public static Identifier textureId() {
        return registered ? textureId : null;
    }

    public static int nativeWidth() {
        return texWidth;
    }

    public static int nativeHeight() {
        return texHeight;
    }
}
