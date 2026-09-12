package me.shedaniel.clothconfig2.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;


public final class WorldBoxRender {

    private WorldBoxRender() {}

    public static void draw(WorldRenderContext ctx, Box worldBox, float r, float g, float b, float a) {
        if (ctx == null || worldBox == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        Vec3d cam = ctx.camera().getPos();
        Box rel = worldBox.offset(-cam.x, -cam.y, -cam.z);

        MatrixStack matrices = ctx.matrixStack();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(2f);

        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        WorldRenderer.drawBox(matrices, lines, rel, r, g, b, a);
        consumers.draw();

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
