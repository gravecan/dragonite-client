package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.render.VertexConsumer;


public final class AlphaVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float alphaMul;

    public AlphaVertexConsumer(VertexConsumer delegate, float alphaMul) {
        this.delegate = delegate;
        this.alphaMul = alphaMul;
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        return delegate.vertex(x, y, z);
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        return delegate.color(red, green, blue, scaleByte(alpha));
    }

    @Override
    public VertexConsumer color(float red, float green, float blue, float alpha) {
        return delegate.color(red, green, blue, alpha * alphaMul);
    }

    @Override
    public VertexConsumer color(int argb) {
        return delegate.color(scaleArgb(argb));
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
        delegate.vertex(x, y, z, scaleArgb(color), u, v, overlay, light, normalX, normalY, normalZ);
    }

    private int scaleByte(int alpha) {
        return Math.min(255, Math.round(alpha * alphaMul));
    }

    private int scaleArgb(int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) {
            a = 255;
        }
        int na = scaleByte(a);
        return (na << 24) | (argb & 0x00FFFFFF);
    }
}
