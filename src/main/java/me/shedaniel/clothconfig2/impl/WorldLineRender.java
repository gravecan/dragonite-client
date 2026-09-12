package me.shedaniel.clothconfig2.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;


public final class WorldLineRender {

    private WorldLineRender() {}

    public static void begin(MatrixStack matrices, boolean throughWalls, float lineWidth) {
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        if (throughWalls) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        } else {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
        }
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(lineWidth);
    }

    public static void end(boolean throughWalls) {
        RenderSystem.lineWidth(1f);
        if (throughWalls) {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    public static void line(
            MatrixStack matrices,
            BufferBuilder buffer,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            float r, float g, float b, float a
    ) {
        Matrix4f mat = matrices.peek().getPositionMatrix();
        buffer.vertex(mat, (float) x1, (float) y1, (float) z1).color(r, g, b, a);
        buffer.vertex(mat, (float) x2, (float) y2, (float) z2).color(r, g, b, a);
    }

    public static BufferBuilder createBuffer() {
        return Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
    }

    public static void draw(BufferBuilder buffer) {
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
}
