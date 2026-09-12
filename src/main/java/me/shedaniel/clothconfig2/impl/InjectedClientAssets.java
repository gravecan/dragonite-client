package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Late-inject does not register the client JAR as a Fabric/Minecraft resource pack,
 * so {@code cloth-config2:} fonts/textures are invisible to {@code ResourceManager}.
 * This bridges essential textures from the JAR classpath / zip into TextureManager,
 * and reports whether Inter font definitions are present for optional styling.
 */
public final class InjectedClientAssets {

    private static final Identifier ICONS =
            Identifier.of("cloth-config2", "textures/gui/icons.png");
    private static final Identifier LOGO =
            Identifier.of("cloth-config2", "textures/gui/logo.png");
    private static final Identifier FONT_DEF =
            Identifier.of("cloth-config2", "font/inter.json");

    /** Every PNG the late-inject client draws via Identifier (not ResourceManager). */
    private static final Map<Identifier, String> TEXTURE_ENTRIES = new LinkedHashMap<>();

    static {
        TEXTURE_ENTRIES.put(ICONS, "assets/cloth-config2/textures/gui/icons.png");
        TEXTURE_ENTRIES.put(LOGO, "assets/cloth-config2/textures/gui/logo.png");
        TEXTURE_ENTRIES.put(
                Identifier.of("cloth-config2", "textures/gui/cloth_config.png"),
                "assets/cloth-config2/textures/gui/cloth_config.png");
        TEXTURE_ENTRIES.put(
                Identifier.of("cloth-config2", "textures/gui/vertical_header_separator.png"),
                "assets/cloth-config2/textures/gui/vertical_header_separator.png");
        TEXTURE_ENTRIES.put(
                Identifier.of("cloth-config2", "textures/gui/vertical_footer_separator.png"),
                "assets/cloth-config2/textures/gui/vertical_footer_separator.png");
        TEXTURE_ENTRIES.put(
                Identifier.of("cloth-config2", "textures/world/vertical_arrow_separator.png"),
                "assets/cloth-config2/textures/world/vertical_arrow_separator.png");
        TEXTURE_ENTRIES.put(
                Identifier.of("cloth-config2", "textures/target_marker.png"),
                "assets/cloth-config2/textures/target_marker.png");
        TEXTURE_ENTRIES.put(
                Identifier.of("cloth-config2", "textures/bloom.png"),
                "assets/cloth-config2/textures/bloom.png");
        TEXTURE_ENTRIES.put(
                Identifier.of("cloth-config2", "textures/hats/santa_hat.png"),
                "assets/cloth-config2/textures/hats/santa_hat.png");
    }

    private static final AtomicBoolean texturesRegistered = new AtomicBoolean(false);
    private static volatile Boolean fontsPresent;

    private InjectedClientAssets() {
    }

    /** True when Minecraft can resolve Inter via the resource manager (Fabric mod path). */
    public static boolean fontsAvailable(MinecraftClient client) {
        if (fontsPresent != null) {
            return fontsPresent;
        }
        if (client == null) {
            return false;
        }
        try {
            fontsPresent = client.getResourceManager().getResource(FONT_DEF).isPresent();
        } catch (Throwable ignored) {
            fontsPresent = false;
        }
        return fontsPresent;
    }

    /**
     * Ensures glass GUI icons/logo are bound in TextureManager even when the
     * resource pack is missing (native inject / URLClassLoader path).
     * Call only from the Minecraft client thread.
     */
    public static void ensureGuiTextures(MinecraftClient client) {
        ensureClientTextures(client);
    }

    /**
     * Registers every late-inject PNG the client draws (arrows, markers, bloom, GUI).
     * Call only from the Minecraft client thread.
     */
    public static void ensureClientTextures(MinecraftClient client) {
        if (client == null || texturesRegistered.get()) {
            return;
        }
        try {
            if (client.getResourceManager().getResource(ICONS).isPresent()) {
                texturesRegistered.set(true);
                return;
            }
        } catch (Throwable ignored) {
            // Resource manager may throw during early boot; fall through to jar load.
        }

        TextureManager tm = client.getTextureManager();
        int ok = 0;
        for (Map.Entry<Identifier, String> entry : TEXTURE_ENTRIES.entrySet()) {
            if (registerTexture(tm, entry.getKey(), entry.getValue())) {
                ok++;
            }
        }
        if (ok > 0) {
            texturesRegistered.set(true);
        }
    }

    /** True after a successful TextureManager bridge or when the resource pack is present. */
    public static boolean texturesReady() {
        return texturesRegistered.get();
    }

    private static boolean registerTexture(TextureManager tm, Identifier id, String jarEntry) {
        byte[] bytes = readAsset(jarEntry);
        if (bytes == null || bytes.length == 0) {
            System.err.println("[ClothConfig] Missing inject asset: " + jarEntry);
            return false;
        }
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            NativeImage image = NativeImage.read(in);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            texture.setFilter(false, false);
            tm.registerTexture(id, texture);
            return true;
        } catch (Throwable t) {
            System.err.println("[ClothConfig] Failed to register " + id + ": "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
            return false;
        }
    }

    private static byte[] readAsset(String jarEntry) {
        ClassLoader cl = InjectedClientAssets.class.getClassLoader();
        for (String path : new String[]{jarEntry, "/" + jarEntry}) {
            try (InputStream in = cl.getResourceAsStream(path)) {
                if (in != null) {
                    return in.readAllBytes();
                }
            } catch (Throwable ignored) {
            }
        }

        String path = InjectedJarLocator.getJarPath();
        if (path == null || path.isBlank()) {
            try {
                var loc = InjectedClientAssets.class.getProtectionDomain().getCodeSource().getLocation();
                if (loc != null) {
                    Path p = Path.of(loc.toURI());
                    if (Files.isRegularFile(p)) {
                        path = p.toAbsolutePath().toString();
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (path == null) {
            return null;
        }
        try (ZipFile zip = new ZipFile(path)) {
            ZipEntry entry = zip.getEntry(jarEntry);
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return in.readAllBytes();
            }
        } catch (Throwable ignored) {
            return null;
        }
    }
}
