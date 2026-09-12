package me.shedaniel.clothconfig2.gui.prestige;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

import java.awt.Color;

/** Panel color/gradient drawing helpers for the ClickGUI. */
public final class PrestigeRenderUtil {

    private PrestigeRenderUtil() {}

    public static Color getColor(int n, float f) {
        float v = (14 + n) / 255f;
        return new Color(v, v, v, MathHelper.clamp(f, 0f, 1f));
    }

    public static Color getColor(Color color, float f) {
        return new Color(
                color.getRed() / 255f,
                color.getGreen() / 255f,
                color.getBlue() / 255f,
                MathHelper.clamp(f, 0f, 1f));
    }

    public static Color getColor(float brightness, float f) {
        return new Color(brightness, brightness, brightness, MathHelper.clamp(f, 0f, 1f));
    }

    public static void renderColoredQuad(float x0, float y0, float x1, float y1,
                                         Color topLeft, Color topRight, Color bottomLeft, Color bottomRight) {
        MatrixStack stack = PrestigeRenderHelper.getMatrixStack();
        stack.push();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Matrix4f m = stack.peek().getPositionMatrix();
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        vtx(bb, m, x0, y0, topLeft);
        vtx(bb, m, x0, y1, bottomLeft);
        vtx(bb, m, x1, y1, bottomRight);
        vtx(bb, m, x1, y0, topRight);
        BufferRenderer.drawWithGlobalProgram(bb.end());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        stack.pop();
    }

    public static void renderGradient(float x0, float y0, float x1, float y1, Color hue, float alpha) {
        MatrixStack stack = PrestigeRenderHelper.getMatrixStack();
        stack.push();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Color white = new Color(1f, 1f, 1f, alpha);
        Color hueC = new Color(hue.getRed() / 255f, hue.getGreen() / 255f, hue.getBlue() / 255f, alpha);
        Color clear = new Color(0f, 0f, 0f, 0f);
        Color black = new Color(0f, 0f, 0f, alpha);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Matrix4f m = stack.peek().getPositionMatrix();
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        vtx(bb, m, x0, y0, white);
        vtx(bb, m, x0, y1, white);
        vtx(bb, m, x1, y1, hueC);
        vtx(bb, m, x1, y0, hueC);
        BufferRenderer.drawWithGlobalProgram(bb.end());
        RenderSystem.disableDepthTest();
        bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        vtx(bb, m, x0, y0, clear);
        vtx(bb, m, x0, y1, black);
        vtx(bb, m, x1, y1, black);
        vtx(bb, m, x1, y0, clear);
        BufferRenderer.drawWithGlobalProgram(bb.end());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        stack.pop();
    }

    public static void renderFilledCircle(float cx, float cy, float radius, Color color) {
        MatrixStack stack = PrestigeRenderHelper.getMatrixStack();
        stack.push();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(
                color.getRed() / 255f,
                color.getGreen() / 255f,
                color.getBlue() / 255f,
                color.getAlpha() / 255f);
        RenderSystem.setShader(GameRenderer::getPositionProgram);
        Matrix4f m = stack.peek().getPositionMatrix();
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION);
        for (int i = 0; i < 360; i++) {
            double ang = i * Math.PI / 180;
            bb.vertex(m, (float) (cx + Math.sin(ang) * radius), (float) (cy + Math.cos(ang) * radius), 0);
        }
        BufferRenderer.drawWithGlobalProgram(bb.end());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        stack.pop();
    }

    /** Rounded rect outline; radius 0 draws a flat rect outline. */
    public static void renderRoundedRectOutline(float x0, float y0, float x1, float y1, Color color, float radius) {
        MatrixStack stack = PrestigeRenderHelper.getMatrixStack();
        stack.push();
        double pi = Math.PI;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(
                color.getRed() / 255f,
                color.getGreen() / 255f,
                color.getBlue() / 255f,
                color.getAlpha() / 255f);
        RenderSystem.setShader(GameRenderer::getPositionProgram);
        Matrix4f m = stack.peek().getPositionMatrix();
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION);
        for (int i = 0; i < 90; i++) {
            double a = i * pi / 180;
            bb.vertex(m, (float) ((x0 + radius) + Math.sin(a) * radius * -1), (float) ((y0 + radius) + Math.cos(a) * radius * -1), 0);
        }
        for (int i = 90; i < 180; i++) {
            double a = i * pi / 180;
            bb.vertex(m, (float) ((x0 + radius) + Math.sin(a) * radius * -1), (float) ((y1 - radius) + Math.cos(a) * radius * -1), 0);
        }
        for (int i = 0; i < 90; i++) {
            double a = i * pi / 180;
            bb.vertex(m, (float) ((x1 - radius) + Math.sin(a) * radius), (float) ((y1 - radius) + Math.cos(a) * radius), 0);
        }
        for (int i = 90; i < 180; i++) {
            double a = i * pi / 180;
            bb.vertex(m, (float) ((x1 - radius) + Math.sin(a) * radius), (float) ((y0 + radius) + Math.cos(a) * radius), 0);
        }
        BufferRenderer.drawWithGlobalProgram(bb.end());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        stack.pop();
    }

    private static void vtx(BufferBuilder bb, Matrix4f m, float x, float y, Color c) {
        bb.vertex(m, x, y, 0).color(
                c.getRed() / 255f,
                c.getGreen() / 255f,
                c.getBlue() / 255f,
                c.getAlpha() / 255f);
    }
}
