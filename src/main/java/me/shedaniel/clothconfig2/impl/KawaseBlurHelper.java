package me.shedaniel.clothconfig2.impl;



import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.MinecraftClient;

import net.minecraft.client.gui.DrawContext;

import net.minecraft.client.gl.Framebuffer;

import net.minecraft.client.gl.SimpleFramebuffer;

import net.minecraft.client.render.BufferBuilder;

import net.minecraft.client.render.BufferRenderer;

import net.minecraft.client.render.GameRenderer;

import net.minecraft.client.render.Tessellator;

import net.minecraft.client.render.VertexFormat;

import net.minecraft.client.render.VertexFormats;

import net.minecraft.util.Identifier;

import org.joml.Matrix4f;

import org.lwjgl.opengl.GL11;

import org.lwjgl.opengl.GL12;

import org.lwjgl.opengl.GL13;

import org.lwjgl.opengl.GL30;





public final class KawaseBlurHelper {

    private static final Identifier QUAD_VERT = Identifier.of("cloth-config2", "shaders/quad.vsh");
    private static final Identifier GAUSSIAN_FRAG = Identifier.of("cloth-config2", "shaders/gaussian_blur.fsh");
    private static final Identifier BLUR_POST_VERT = Identifier.of("cloth-config2", "shaders/blur_post.vsh");
    private static final Identifier BLUR_POST_FRAG = Identifier.of("cloth-config2", "shaders/blur_post.fsh");



    private static GlslProgram gaussianShader;
    private static GlslProgram blurPostShader;

    private static SimpleFramebuffer bufferA;

    private static SimpleFramebuffer bufferB;

    private static SimpleFramebuffer outputBuffer;

    private static int lastW;

    private static int lastH;

    private static long lastBlurFrameBucket = -1L;

    private static float lastBlurOffset = Float.NaN;

    private static int lastBlurSteps = -1;

    private static int lastMainW;

    private static int lastMainH;



    private KawaseBlurHelper() {}



    public static boolean isReady() {

        ensureShaders();

        return gaussianShader != null && gaussianShader.isValid();

    }



    public static int getBlurredTextureId() {

        return outputBuffer != null ? outputBuffer.getColorAttachment() : 0;

    }



    public static void updateBlur(float offset, int steps) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getFramebuffer() == null) return;
        ensureShaders();
        if (!isReady()) {
            logOnce("[DragoniteBlur] gaussian shader not ready: "
                    + (gaussianShader != null ? gaussianShader.getLastError() : "not created"));
            return;
        }

        Framebuffer main = mc.getFramebuffer();
        int w = main.textureWidth;
        int h = main.textureHeight;
        if (w <= 0 || h <= 0) return;

        long frameBucket = System.nanoTime() / 16_666_666L;
        if (outputBuffer != null
                && frameBucket == lastBlurFrameBucket
                && offset == lastBlurOffset
                && steps == lastBlurSteps
                && w == lastMainW
                && h == lastMainH) return;

        // Quarter-resolution target: musicdisplay-style heavy frost comes from
        // wide kernels on a small buffer, which is also much cheaper per frame.
        int bw = Math.max(1, w >> 2);
        int bh = Math.max(1, h >> 2);
        ensureBuffers(w, h, bw, bh);

        int viewportW = main.viewportWidth;
        int viewportH = main.viewportHeight;
        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4f model = new Matrix4f().identity();
        Matrix4f projection = new Matrix4f().ortho(0f, bw, bh, 0f, -1f, 1f);
        boolean completed = false;

        try {
            RenderSystem.setProjectionMatrix(projection,
                    com.mojang.blaze3d.systems.VertexSorter.BY_Z);
            RenderSystem.viewport(0, 0, bw, bh);

            // Sample the main framebuffer texture directly for the first pass. We never
            // write back into it (all passes target bufferA/bufferB), so this is a legal
            // read and removes the fragile snapshot blit that could silently fail.
            int readTex = main.getColorAttachment();
            SimpleFramebuffer write = bufferA;
            SimpleFramebuffer last = null;
            // musicdisplay-style look comes from more taps on a smaller target; extra
            // separable passes are cheap at half resolution and read as "frosted glass".
            int passCount = Math.max(6, steps + 2);
            if ((passCount & 1) != 0) passCount++;

            for (int pass = 0; pass < passCount; pass++) {
                write.beginWrite(true);
                RenderSystem.viewport(0, 0, bw, bh);
                bindLinear(readTex);
                gaussianShader.bind();
                gaussianShader.uniformMatrix4f("ModelViewMat", model);
                gaussianShader.uniformMatrix4f("ProjMat", projection);
                gaussianShader.uniform2f("Resolution", 1f / bw, 1f / bh);
                gaussianShader.uniform2f("Direction", (pass & 1) == 0 ? 1f : 0f,
                        (pass & 1) == 0 ? 0f : 1f);
                gaussianShader.uniform1f("Radius", Math.max(0.5f, offset) * 2.2f * (1f + pass * 0.12f));
                gaussianShader.uniform1i("Sampler0", 0);
                drawFullscreenQuad(bw, bh);
                gaussianShader.unbind();
                write.endWrite();

                readTex = write.getColorAttachment();
                last = write;
                write = write == bufferA ? bufferB : bufferA;
            }

            outputBuffer = last;
            completed = true;
            if (!loggedOnce) {
                System.err.println("[DragoniteBlur] pipeline OK tex=" + outputBuffer.getColorAttachment()
                        + " passes=" + passCount + " halfRes=" + bw + "x" + bh
                        + " srcGrid=" + readGrid(main)
                        + " blurGrid=" + readGrid(outputBuffer));
                loggedOnce = true;
            }
        } catch (Throwable t) {
            logOnce("[DragoniteBlur] updateBlur failed: " + t);
        } finally {
            try {
                gaussianShader.unbind();
            } catch (Throwable ignored) {
            }
            main.beginWrite(false);
            RenderSystem.viewport(0, 0, viewportW, viewportH);
            RenderSystem.setProjectionMatrix(previousProjection,
                    com.mojang.blaze3d.systems.VertexSorter.BY_Z);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        if (completed) {
            lastBlurFrameBucket = frameBucket;
            lastBlurOffset = offset;
            lastBlurSteps = steps;
            lastMainW = w;
            lastMainH = h;
        }
    }

    private static void logOnce(String message) {
        if (!loggedOnce) {
            loggedOnce = true;
            System.err.println(message);
        }
    }

    private static String readGrid(Framebuffer fb) {
        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fb.fbo);
        java.nio.ByteBuffer px = org.lwjgl.BufferUtils.createByteBuffer(4);
        StringBuilder sb = new StringBuilder();
        int[][] pts = {
                { fb.textureWidth / 4, fb.textureHeight / 4 },
                { fb.textureWidth / 2, fb.textureHeight / 2 },
                { fb.textureWidth * 3 / 4, fb.textureHeight * 3 / 4 }
        };
        for (int i = 0; i < pts.length; i++) {
            px.clear();
            GL11.glReadPixels(pts[i][0], pts[i][1], 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, px);
            if (i > 0) sb.append(',');
            int rgb = ((px.get(0) & 0xFF) << 16) | ((px.get(1) & 0xFF) << 8) | (px.get(2) & 0xFF);
            sb.append(Integer.toHexString(rgb));
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        return sb.toString();
    }

    private static volatile boolean loggedOnce;



    private static void ensureShaders() {

        if (gaussianShader == null) gaussianShader = new GlslProgram(QUAD_VERT, GAUSSIAN_FRAG);
        if (blurPostShader == null) blurPostShader = new GlslProgram(BLUR_POST_VERT, BLUR_POST_FRAG);

    }



    private static void ensureBuffers(int sourceW, int sourceH, int w, int h) {

        if (bufferA == null || lastW != w || lastH != h) {

            if (bufferA != null) {

                bufferA.delete();

            }

            if (bufferB != null) {

                bufferB.delete();

            }

            bufferA = new SimpleFramebuffer(w, h, false, false);

            bufferB = new SimpleFramebuffer(w, h, false, false);

            outputBuffer = null;

            bufferA.setTexFilter(GL11.GL_LINEAR);

            bufferB.setTexFilter(GL11.GL_LINEAR);

            lastW = w;

            lastH = h;

        }

    }



    private static void bindLinear(int textureId) {

        RenderSystem.bindTexture(textureId);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

    }



    private static void drawFullscreenQuad(int w, int h) {

        Tessellator tessellator = Tessellator.getInstance();

        BufferBuilder builder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);

        builder.vertex(0, h, 0).texture(0f, 0f);

        builder.vertex(w, h, 0).texture(1f, 0f);

        builder.vertex(w, 0, 0).texture(1f, 1f);

        builder.vertex(0, 0, 0).texture(0f, 1f);

        BufferRenderer.draw(builder.end());

    }



    public static void bindBlurredTextureUnit5() {

        int tex = getBlurredTextureId();

        if (tex == 0) {

            return;

        }

        RenderSystem.assertOnRenderThread();

        GL13.glActiveTexture(GL13.GL_TEXTURE5);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);

    }

    /** Draws the latest blurred framebuffer into a GUI-space rectangle. */
    public static boolean drawBlurredTexture(DrawContext context, int x, int y, int width, int height, float alpha) {
        return drawBlurredTexture(context, x, y, width, height, alpha, 1f, 0f, 0f, 0f, 0f, 0f);
    }

    /**
     * Frosted-glass blit for a GUI rectangle. Samples the matching region of the
     * full-screen blur texture (so the world stays sharp outside the panel) and
     * optionally applies a rounded-corner SDF mask.
     *
     * @param screenW GUI screen width used for UV mapping (usually Screen.width)
     * @param screenH GUI screen height used for UV mapping (usually Screen.height)
     */
    public static boolean drawBlurredTexture(DrawContext context, int x, int y, int width, int height,
            float alpha, float saturation, float cornerRadius, float tintR, float tintG, float tintB, float tintA,
            int screenW, int screenH) {
        int texture = getBlurredTextureId();
        if (context == null || texture == 0 || width <= 0 || height <= 0 || alpha <= 0f
                || screenW <= 0 || screenH <= 0) {
            return false;
        }
        RenderSystem.assertOnRenderThread();
        ensureShaders();
        boolean usePost = blurPostShader != null && blurPostShader.isValid();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, Math.max(0f, Math.min(1f, alpha)));
        RenderSystem.setShaderTexture(0, texture);

        // GUI y grows downward; FB texture v grows upward — match the proven fullscreen mapping.
        float u0 = x / (float) screenW;
        float u1 = (x + width) / (float) screenW;
        float vBottom = (screenH - (y + height)) / (float) screenH;
        float vTop = (screenH - y) / (float) screenH;

        try {
            Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
            Tessellator tessellator = Tessellator.getInstance();
            if (usePost) {
                BufferBuilder builder = tessellator
                        .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                // Color.rg carries local 0..1 coords for the rounded-corner mask.
                builder.vertex(matrix, x, y + height, 0).texture(u0, vBottom).color(0f, 1f, 0f, 1f);
                builder.vertex(matrix, x + width, y + height, 0).texture(u1, vBottom).color(1f, 1f, 0f, 1f);
                builder.vertex(matrix, x + width, y, 0).texture(u1, vTop).color(1f, 0f, 0f, 1f);
                builder.vertex(matrix, x, y, 0).texture(u0, vTop).color(0f, 0f, 0f, 1f);
                blurPostShader.bind();
                blurPostShader.uniformMatrix4f("ProjMat", RenderSystem.getProjectionMatrix());
                blurPostShader.uniformMatrix4f("ModelViewMat", RenderSystem.getModelViewMatrix());
                blurPostShader.uniform1i("Sampler0", 0);
                blurPostShader.uniform1f("Saturation", saturation);
                blurPostShader.uniform2f("Size", width, height);
                blurPostShader.uniform1f("CornerRadius", Math.max(0f, cornerRadius));
                blurPostShader.uniform4f("TintColor", tintR, tintG, tintB, tintA);
                BufferRenderer.draw(builder.end());
                blurPostShader.unbind();
            } else {
                BufferBuilder builder = tessellator
                        .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
                builder.vertex(matrix, x, y + height, 0).texture(u0, vBottom);
                builder.vertex(matrix, x + width, y + height, 0).texture(u1, vBottom);
                builder.vertex(matrix, x + width, y, 0).texture(u1, vTop);
                builder.vertex(matrix, x, y, 0).texture(u0, vTop);
                RenderSystem.setShader(GameRenderer::getPositionTexProgram);
                BufferRenderer.drawWithGlobalProgram(builder.end());
            }
        } finally {
            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
        }
        return true;
    }

    /** @deprecated Prefer the overload that takes screenW/screenH for correct UV mapping. */
    public static boolean drawBlurredTexture(DrawContext context, int x, int y, int width, int height,
            float alpha, float saturation, float cornerRadius, float tintR, float tintG, float tintB, float tintA) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc != null && mc.currentScreen != null ? mc.currentScreen.width : width;
        int sh = mc != null && mc.currentScreen != null ? mc.currentScreen.height : height;
        if (x == 0 && y == 0 && mc != null && mc.currentScreen != null
                && width >= mc.currentScreen.width && height >= mc.currentScreen.height) {
            sw = width;
            sh = height;
        }
        return drawBlurredTexture(context, x, y, width, height, alpha, saturation, cornerRadius,
                tintR, tintG, tintB, tintA, sw, sh);
    }
}

