package me.shedaniel.clothconfig2.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;


public final class HandCompositeHelper {
    private static final Identifier QUAD_VERT = Identifier.of("cloth-config2", "shaders/quad.vsh");
    private static final Identifier BLIT_FRAG = Identifier.of("cloth-config2", "shaders/blit.fsh");
    private static final Identifier GLASS_FRAG = Identifier.of("cloth-config2", "shaders/composite_glass.fsh");
    private static final Identifier GLOW_FRAG = Identifier.of("cloth-config2", "shaders/composite_glow.fsh");

    private static GlslProgram blitShader;
    private static GlslProgram glassShader;
    private static GlslProgram glowShader;

    private static SimpleFramebuffer colorBefore;
    private static SimpleFramebuffer colorAfter;
    private static SimpleFramebuffer depthBefore;
    private static int lastW;
    private static int lastH;
    private static boolean captured;

    private HandCompositeHelper() {}

    public static boolean isReady() {
        ensureShaders();
        return blitShader != null
                && blitShader.isValid()
                && glassShader != null
                && glassShader.isValid()
                && glowShader != null
                && glowShader.isValid();
    }

    
    public static void capturePreHand(Config_GlassHands mod) {
        captured = false;
        if (mod == null) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getFramebuffer() == null) {
            return;
        }
        ensureShaders();
        if (!isReady()) {
            return;
        }

        Framebuffer main = mc.getFramebuffer();
        int w = main.textureWidth;
        int h = main.textureHeight;
        if (w <= 0 || h <= 0) {
            return;
        }
        ensureBuffers(w, h);

        depthBefore.copyDepthFrom(main);

        colorBefore.beginWrite(true);
        blitTexture(main.getColorAttachment(), w, h);
        colorBefore.endWrite();

        main.beginWrite(false);
        if (mod.isGlassMode()) {
            KawaseBlurHelper.updateBlur(mod.getBlurStrength(), mod.getBlurSteps());
            captured = KawaseBlurHelper.getBlurredTextureId() != 0;
        } else {
            captured = true;
        }
    }

    
    public static void applyPostProcess(Config_GlassHands mod) {
        if (!captured || mod == null) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getFramebuffer() == null) {
            return;
        }
        if (!isReady()) {
            return;
        }

        Framebuffer main = mc.getFramebuffer();
        int w = main.textureWidth;
        int h = main.textureHeight;
        int vw = main.viewportWidth;
        int vh = main.viewportHeight;
        if (w <= 0 || h <= 0 || vw <= 0 || vh <= 0) {
            return;
        }
        ensureBuffers(w, h);

        colorAfter.beginWrite(true);
        blitTexture(main.getColorAttachment(), vw, vh);
        colorAfter.endWrite();

        Matrix4f model = new Matrix4f().identity();
        Matrix4f proj = new Matrix4f().ortho(0f, vw, vh, 0f, -1f, 1f);
        RenderSystem.setProjectionMatrix(proj, com.mojang.blaze3d.systems.VertexSorter.BY_Z);

        main.beginWrite(false);
        RenderSystem.viewport(0, 0, vw, vh);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();

        RenderSystem.disableBlend();

        GlslProgram shader = mod.isOutlineMode() ? glowShader : glassShader;
        shader.bind();
        shader.uniformMatrix4f("ModelViewMat", model);
        shader.uniformMatrix4f("ProjMat", proj);
        shader.uniform2f("Resolution", (float) vw, (float) vh);
        shader.uniform1f("DepthEpsilon", 0.00001f);
        shader.uniform1f("ColorEpsilon", 0.012f);

        bindTex(shader, "ColorBeforeSampler", 0, colorBefore.getColorAttachment());
        bindTex(shader, "ColorAfterSampler", 1, colorAfter.getColorAttachment());
        bindTex(shader, "DepthBeforeSampler", 2, depthBefore.getDepthAttachment());
        bindTex(shader, "DepthAfterSampler", 3, main.getDepthAttachment());

        if (mod.isGlassMode()) {
            bindTex(shader, "BlurSampler", 4, KawaseBlurHelper.getBlurredTextureId());
            float[] tint = mod.getGlassTint();
            shader.uniform3f("Tint", tint[0], tint[1], tint[2]);
            shader.uniform1f("DistortStrength", mod.getDistortStrength());
            shader.uniform1f("GlassBrightness", mod.getGlassBrightness());
            shader.uniform1f("EdgeRadius", mod.getEdgeRadius());
            shader.uniform1f("EdgeDirections", 8f);
            shader.uniform1f("FresnelPower", 0.5f);
            shader.uniform1f("EdgeGlow", mod.getEdgeGlow());
        } else {
            float[] rgb = mod.getGlowRgb();
            shader.uniform3f("GlowColor", rgb[0], rgb[1], rgb[2]);
            shader.uniform1f("GlowIntensity", mod.getGlowIntensity());
            shader.uniform1f("OutlineWidth", mod.getOutlineWidth());
            shader.uniform1f("EdgeDirections", 16f);
        }

        drawQuad(vw, vh);
        shader.unbind();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        captured = false;
    }

    private static void blitTexture(int textureId, int w, int h) {
        bindLinear(textureId);
        blitShader.bind();
        Matrix4f model = new Matrix4f().identity();
        Matrix4f proj = new Matrix4f().ortho(0f, w, h, 0f, -1f, 1f);
        blitShader.uniformMatrix4f("ModelViewMat", model);
        blitShader.uniformMatrix4f("ProjMat", proj);
        blitShader.uniform1i("Sampler0", 0);
        drawQuad(w, h);
        blitShader.unbind();
    }

    private static void bindTex(GlslProgram shader, String uniform, int unit, int textureId) {
        if (textureId == 0) {
            return;
        }
        RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
        RenderSystem.bindTexture(textureId);
        if (uniform.contains("Depth")) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        } else {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        }
        shader.uniform1i(uniform, unit);
    }

    private static void bindLinear(int textureId) {
        RenderSystem.bindTexture(textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    private static void drawQuad(int w, int h) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder builder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        builder.vertex(0, h, 0).texture(0f, 0f);
        builder.vertex(w, h, 0).texture(1f, 0f);
        builder.vertex(w, 0, 0).texture(1f, 1f);
        builder.vertex(0, 0, 0).texture(0f, 1f);
        BufferRenderer.draw(builder.end());
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
    }

    private static void ensureBuffers(int w, int h) {
        if (colorBefore == null || lastW != w || lastH != h) {
            if (colorBefore != null) {
                colorBefore.delete();
            }
            if (colorAfter != null) {
                colorAfter.delete();
            }
            if (depthBefore != null) {
                depthBefore.delete();
            }
            colorBefore = new SimpleFramebuffer(w, h, false, false);
            colorAfter = new SimpleFramebuffer(w, h, false, false);
            depthBefore = new SimpleFramebuffer(w, h, true, false);
            colorBefore.setTexFilter(GL11.GL_LINEAR);
            colorAfter.setTexFilter(GL11.GL_LINEAR);
            lastW = w;
            lastH = h;
        }
    }

    private static void ensureShaders() {
        if (blitShader == null) {
            blitShader = new GlslProgram(QUAD_VERT, BLIT_FRAG);
        }
        if (glassShader == null) {
            glassShader = new GlslProgram(QUAD_VERT, GLASS_FRAG);
        }
        if (glowShader == null) {
            glowShader = new GlslProgram(QUAD_VERT, GLOW_FRAG);
        }
    }
}
