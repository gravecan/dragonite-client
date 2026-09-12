package me.shedaniel.clothconfig2.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.ColorFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class RenderContext extends ConfigCategoryImpl {

    private final BooleanToggleBuilder showOnSelf;
    private final BooleanToggleBuilder showOnPlayers;
    private final DoubleFieldBuilder   scale;
    private final ColorFieldBuilder    chinaColor;
    private final ColorFieldBuilder    chinaColor2;
    private final BooleanToggleBuilder disableOnElytra;

    public RenderContext() {
        super("China hat", "China hat on players", Cat.VISUALS);

        showOnSelf     = new BooleanToggleBuilder("Show On Self",    "", true);
        showOnPlayers  = new BooleanToggleBuilder("Show On Players", "", true);
        scale          = new DoubleFieldBuilder("Scale",             "", 1.0, 0.5, 2.0, 0.1);
        chinaColor     = new ColorFieldBuilder("China Color 1",      "", 255, 50, 100).withRainbowOption();
        chinaColor2    = new ColorFieldBuilder("China Color 2",      "", 100, 50, 255).withRainbowOption();
        disableOnElytra = new BooleanToggleBuilder("Disable On Elytra", "Disable hat when gliding", true);

        addSetting(showOnSelf);
        addSetting(showOnPlayers);
        addSetting(scale);
        addSetting(chinaColor);
        addSetting(chinaColor2);
        addSetting(disableOnElytra);
    }

    public void render(WorldRenderContext ctx) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        boolean firstPerson = mc.options.getPerspective().isFirstPerson();
        float pt = ctx.tickCounter().getTickDelta(true);

        RainbowManager rainbowMgr = RainbowManager.getInstance();
        if (ColorFieldBuilder.anyRainbow(chinaColor, chinaColor2)) {
            rainbowMgr.update(1.0f);
        }

        List<PlayerEntity> players = new ArrayList<>();
        if (showOnSelf.get() && mc.player != null && !firstPerson
                && mc.player.isAlive()) {
            if (!(disableOnElytra.get() && mc.player.isFallFlying())) {
                players.add(mc.player);
            }
        }
        for (net.minecraft.entity.LivingEntity living : WorldRenderEntityCache.livingEntities()) {
            if (!(living instanceof PlayerEntity player)) {
                continue;
            }
            if (player == mc.player) {
                continue;
            }
            if (!showOnPlayers.get() || !player.isAlive()) {
                continue;
            }
            if (disableOnElytra.get() && player.isFallFlying()) {
                continue;
            }
            players.add(player);
        }

        if (players.isEmpty()) {
            return;
        }

        MatrixStack matrices = ctx.matrixStack();
        Vec3d cam = ctx.camera().getPos();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1.5f, -1.0f);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        for (PlayerEntity player : players) {
            double px = MathHelper.lerp(pt, player.prevX, player.getX()) - cam.x;
            double py = MathHelper.lerp(pt, player.prevY, player.getY()) - cam.y;
            double pz = MathHelper.lerp(pt, player.prevZ, player.getZ()) - cam.z;

            matrices.push();
            matrices.translate(px, py, pz);
            applyHatTransform(matrices, player, pt);

            float sc = (float) scale.get();
            matrices.scale(sc, sc, sc);
            drawChinaHat(matrices, rainbowMgr);
            matrices.pop();
        }

        RenderSystem.disablePolygonOffset();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    public static void renderOnPlayerHead(PlayerEntity player, float tickDelta, MatrixStack matrices) {
    }

    
    private static void applyHatTransform(MatrixStack matrices, PlayerEntity player, float pt) {
        float headY = player.getHeight() + 0.04f;
        matrices.translate(0, headY, 0);
        float bodyYaw = MathHelper.lerpAngleDegrees(pt, player.prevBodyYaw, player.bodyYaw);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - bodyYaw));
    }

    private void drawChinaHat(MatrixStack matrices, RainbowManager rainbowMgr) {
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        matrices.push();
        matrices.translate(0f, 0.06f, 0f);

        Matrix4f mat = matrices.peek().getPositionMatrix();
        int segs = 48;
        float width = 0.55f;
        float coneHeight = 0.31f;
        float anim = (System.currentTimeMillis() % 8000L) / 8000f;
        float alpha = 1.0f;

        float r1, g1, b1, r2, g2, b2;
        float[] scratch = new float[3];
        chinaColor.resolveRgb(rainbowMgr, 0f, -1, scratch);
        r1 = scratch[0];
        g1 = scratch[1];
        b1 = scratch[2];
        chinaColor2.resolveRgb(rainbowMgr, 180f, -1, scratch);
        r2 = scratch[0];
        g2 = scratch[1];
        b2 = scratch[2];

        float centerT = (MathHelper.sin(anim * MathHelper.TAU) + 1f) * 0.5f;
        float cr = lerp(r1, r2, centerT);
        float cg = lerp(g1, g2, centerT);
        float cb = lerp(b1, b2, centerT);

        float[] ringX = new float[segs + 1];
        float[] ringZ = new float[segs + 1];
        float[] ringR = new float[segs + 1];
        float[] ringG = new float[segs + 1];
        float[] ringB = new float[segs + 1];

        for (int i = 0; i <= segs; i++) {
            float angle = (float) (2 * Math.PI * i / segs);
            ringX[i] = -MathHelper.sin(angle) * width;
            ringZ[i] = MathHelper.cos(angle) * width;
            float t = ringBlend(angle, anim);
            ringR[i] = lerp(r1, r2, t);
            ringG[i] = lerp(g1, g2, t);
            ringB[i] = lerp(b1, b2, t);
        }

        
        BufferBuilder shell = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < segs; i++) {
            addConeTri(shell, mat, 0f, coneHeight, 0f, cr, cg, cb, alpha,
                    ringX[i], 0f, ringZ[i], ringR[i], ringG[i], ringB[i],
                    ringX[i + 1], 0f, ringZ[i + 1], ringR[i + 1], ringG[i + 1], ringB[i + 1]);
            addConeTri(shell, mat, 0f, coneHeight, 0f, cr, cg, cb, alpha,
                    ringX[i + 1], 0f, ringZ[i + 1], ringR[i + 1], ringG[i + 1], ringB[i + 1],
                    ringX[i], 0f, ringZ[i], ringR[i], ringG[i], ringB[i]);
        }
        BufferRenderer.drawWithGlobalProgram(shell.end());

        
        BufferBuilder brim = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        float avgR = 0f, avgG = 0f, avgB = 0f;
        for (int i = 0; i < segs; i++) {
            avgR += ringR[i];
            avgG += ringG[i];
            avgB += ringB[i];
        }
        float inv = 1f / segs;
        brim.vertex(mat, 0f, -0.002f, 0f).color(avgR * inv, avgG * inv, avgB * inv, alpha);
        for (int i = segs; i >= 0; i--) {
            brim.vertex(mat, ringX[i], 0f, ringZ[i]).color(ringR[i], ringG[i], ringB[i], alpha);
        }
        BufferRenderer.drawWithGlobalProgram(brim.end());

        matrices.pop();
    }

    private static void addConeTri(
            BufferBuilder buf, Matrix4f mat,
            float ax, float ay, float az, float ar, float ag, float ab, float aa,
            float bx, float by, float bz, float br, float bg, float bb,
            float cx, float cy, float cz, float cr, float cg, float cb
    ) {
        buf.vertex(mat, ax, ay, az).color(ar, ag, ab, aa);
        buf.vertex(mat, bx, by, bz).color(br, bg, bb, aa);
        buf.vertex(mat, cx, cy, cz).color(cr, cg, cb, aa);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * MathHelper.clamp(t, 0f, 1f);
    }

    private static float ringBlend(float angle, float anim) {
        return (MathHelper.sin(angle + anim * MathHelper.TAU) + 1f) * 0.5f;
    }
}
