package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.render.VertexConsumer;


public final class GlowVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float r;
    private final float g;
    private final float b;
    private final float intensity;
    private final float alphaMul;

    public GlowVertexConsumer(VertexConsumer delegate, float r, float g, float b, float intensity, float alphaMul) {
        this.delegate = delegate;
        this.r = r;
        this.g = g;
        this.b = b;
        this.intensity = intensity;
        this.alphaMul = alphaMul;
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        return delegate.vertex(x, y, z);
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        int a = scaleByte(alpha);
        return delegate.color(
                toByte(r * intensity),
                toByte(g * intensity),
                toByte(b * intensity),
                a);
    }

    @Override
    public VertexConsumer color(float red, float green, float blue, float alpha) {
        return delegate.color(r * intensity, g * intensity, b * intensity, alpha * alphaMul);
    }

    @Override
    public VertexConsumer color(int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) {
            a = 255;
        }
        int na = scaleByte(a);
        int nr = toByte(r * intensity);
        int ng = toByte(g * intensity);
        int nb = toByte(b * intensity);
        return delegate.color((na << 24) | (nr << 16) | (ng << 8) | nb);
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        return delegate.texture(u, v);
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        return delegate.overlay(u, v);
    }

    @Override
    public VertexConsumer light(int u, int v) {
        return delegate.light(u, v);
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        return delegate.normal(x, y, z);
    }

    @Override
    public void vertex(
            float x,
            float y,
            float z,
            int color,
            float u,
            float v,
            int overlay,
            int light,
            float normalX,
            float normalY,
            float normalZ) {
        int a = (color >>> 24) & 0xFF;
        if (a == 0) {
            a = 255;
        }
        int na = scaleByte(a);
        int nr = toByte(r * intensity);
        int ng = toByte(g * intensity);
        int nb = toByte(b * intensity);
        delegate.vertex(x, y, z, (na << 24) | (nr << 16) | (ng << 8) | nb, u, v, overlay, light, normalX, normalY, normalZ);
    }

    private int scaleByte(int alpha) {
        return Math.min(255, Math.round(alpha * alphaMul));
    }

    private static int toByte(float channel) {
        return Math.max(0, Math.min(255, Math.round(channel * 255f)));
    }
}
