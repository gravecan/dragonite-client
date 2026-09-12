package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.internal.IntegrationHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Downloads the connected Discord user's avatar from the Discord CDN and exposes it
 * as a circular, alpha-masked dynamic texture for the glass GUI.
 */
public final class DiscordAvatarTexture {
    private static final Identifier TEXTURE_ID = Identifier.of("cloth-config2", "discord_avatar");
    private static final int SIZE = 64;

    private static final AtomicBoolean loading = new AtomicBoolean(false);
    private static volatile boolean ready;
    private static volatile String loadedForUser;
    private static volatile int texWidth = SIZE;
    private static volatile int texHeight = SIZE;

    private DiscordAvatarTexture() {
    }

    /** Call from the render thread each frame; kicks off the async fetch once per user. */
    public static void ensure(MinecraftClient client, IntegrationHandler.DiscordUser user) {
        if (client == null || user == null || user.id == null || ready) {
            return;
        }
        if (user.id.equals(loadedForUser) && loading.get()) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }
        loadedForUser = user.id;
        String url = avatarUrl(user);
        CompletableFuture
                .supplyAsync(() -> download(url))
                .thenAccept(image -> {
                    if (image == null) {
                        loading.set(false);
                        return;
                    }
                    client.execute(() -> {
                        try {
                            TextureManager manager = client.getTextureManager();
                            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
                            texture.setFilter(false, false);
                            manager.registerTexture(TEXTURE_ID, texture);
                            texWidth = image.getWidth();
                            texHeight = image.getHeight();
                            ready = true;
                        } catch (Throwable ignored) {
                            // Avatar is cosmetic only.
                        } finally {
                            loading.set(false);
                        }
                    });
                })
                .exceptionally(throwable -> {
                    loading.set(false);
                    return null;
                });
    }

    /** Texture id when the avatar is downloaded and registered, otherwise null. */
    public static Identifier textureId() {
        return ready ? TEXTURE_ID : null;
    }

    public static boolean isReady() {
        return ready;
    }

    public static int texWidth() {
        return texWidth;
    }

    public static int texHeight() {
        return texHeight;
    }

    private static String avatarUrl(IntegrationHandler.DiscordUser user) {
        if (user.avatar != null && !user.avatar.isEmpty() && !"null".equals(user.avatar)) {
            String extension = user.avatar.startsWith("a_") ? "gif" : "png";
            return "https://cdn.discordapp.com/avatars/" + user.id + "/" + user.avatar + "." + extension + "?size=64";
        }
        long index = 0;
        try {
            index = (Long.parseUnsignedLong(user.id) >>> 22) % 6;
        } catch (NumberFormatException ignored) {
        }
        return "https://cdn.discordapp.com/embed/avatars/" + index + ".png";
    }

    private static NativeImage download(String url) {
        try {
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(6))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body() == null || response.body().length == 0) {
                return null;
            }
            NativeImage image;
            try (InputStream in = new ByteArrayInputStream(response.body())) {
                image = NativeImage.read(in);
            }
            return maskCircle(image);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Applies a circular alpha mask with a 1px soft edge so the avatar reads as a clean pfp. */
    private static NativeImage maskCircle(NativeImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        float cx = (w - 1) / 2f;
        float cy = (h - 1) / 2f;
        float radius = Math.min(w, h) / 2f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float distance = (float) Math.hypot(x - cx, y - cy);
                float coverage = Math.max(0f, Math.min(1f, radius - 0.5f - distance + 0.5f));
                if (coverage <= 0f) {
                    image.setColor(x, y, 0);
                    continue;
                }
                if (coverage < 1f) {
                    int color = image.getColor(x, y);
                    int alpha = (color >>> 24) & 0xFF;
                    int scaled = Math.round(alpha * coverage);
                    image.setColor(x, y, (color & 0x00FFFFFF) | (scaled << 24));
                }
            }
        }
        return image;
    }
}
