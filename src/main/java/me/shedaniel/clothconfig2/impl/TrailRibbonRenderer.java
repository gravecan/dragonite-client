package me.shedaniel.clothconfig2.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;


public final class TrailRibbonRenderer {

    private static final int SPLINE_STEPS = 8;

    private TrailRibbonRenderer() {}

    public static void render(
            Matrix4f matrix,
            Vec3d cameraPos,
            MinecraftClient mc,
            List<TrailSample> samples,
            float heightMul,
            float fillStrength,
            float r,
            float g,
            float b,
            boolean rainbow,
            RainbowManager rainbowMgr
    ) {
        if (samples.size() < 2 || mc.player == null) {
            return;
        }

        List<Vec3d> curve = buildCatmullRomCurve(samples);
        if (curve.size() < 2) {
            return;
        }

        float wallHeight = mc.player.getHeight() * heightMul;
        long now = System.currentTimeMillis();
        long oldest = samples.get(0).time();
        long newest = samples.get(samples.size() - 1).time();
        long span = Math.max(1L, newest - oldest);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        renderVerticalStrip(matrix, cameraPos, curve, samples, wallHeight, oldest, span, now,
                r, g, b, rainbow, rainbowMgr, fillStrength);

        RenderSystem.lineWidth(1.0f);
        renderEdgeLine(matrix, cameraPos, curve, samples, wallHeight, oldest, span, now,
                r, g, b, rainbow, rainbowMgr, true);
        renderEdgeLine(matrix, cameraPos, curve, samples, wallHeight, oldest, span, now,
                r, g, b, rainbow, rainbowMgr, false);

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void renderVerticalStrip(
            Matrix4f matrix,
            Vec3d cameraPos,
            List<Vec3d> curve,
            List<TrailSample> samples,
            float wallHeight,
            long oldest,
            long span,
            long now,
            float r,
            float g,
            float b,
            boolean rainbow,
            RainbowManager rainbowMgr,
            float fillStrength
    ) {
        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.TRIANGLE_STRIP,
                VertexFormats.POSITION_COLOR
        );

        int last = curve.size() - 1;
        for (int i = 0; i < curve.size(); i++) {
            Vec3d world = curve.get(i);
            Vec3d rel = world.subtract(cameraPos);
            float progress = i / (float) last;

            float cr = r;
            float cg = g;
            float cb = b;
            if (rainbow) {
                float[] rgb = rainbowMgr.getRainbowColor(progress * 300f);
                cr = rgb[0];
                cg = rgb[1];
                cb = rgb[2];
            }

            float alpha = trailAlpha(samples, progress, oldest, span, now, progress, 0.72f) * fillStrength;
            int a = (int) (MathHelper.clamp(alpha, 0f, 1f) * 255);
            if (a < 2) {
                continue;
            }

            float x = (float) rel.x;
            float y = (float) rel.y;
            float z = (float) rel.z;

            buffer.vertex(matrix, x, y, z).color(cr, cg, cb, a);
            buffer.vertex(matrix, x, y + wallHeight, z).color(cr, cg, cb, a);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void renderEdgeLine(
            Matrix4f matrix,
            Vec3d cameraPos,
            List<Vec3d> curve,
            List<TrailSample> samples,
            float wallHeight,
            long oldest,
            long span,
            long now,
            float r,
            float g,
            float b,
            boolean rainbow,
            RainbowManager rainbowMgr,
            boolean top
    ) {
        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.DEBUG_LINE_STRIP,
                VertexFormats.POSITION_COLOR
        );

        int last = curve.size() - 1;
        for (int i = 0; i < curve.size(); i++) {
            Vec3d world = curve.get(i);
            Vec3d rel = world.subtract(cameraPos);
            float progress = i / (float) last;

            float cr = r;
            float cg = g;
            float cb = b;
            if (rainbow) {
                float[] rgb = rainbowMgr.getRainbowColor(progress * 300f);
                cr = rgb[0];
                cg = rgb[1];
                cb = rgb[2];
            }

            float alpha = trailAlpha(samples, progress, oldest, span, now, progress, 1.0f);
            int a = (int) (MathHelper.clamp(alpha, 0f, 1f) * 255);
            if (a < 2) {
                continue;
            }

            float yOff = top ? wallHeight : 0f;
            buffer.vertex(matrix, (float) rel.x, (float) rel.y + yOff, (float) rel.z).color(cr, cg, cb, a);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static float trailAlpha(
            List<TrailSample> samples,
            float progress,
            long oldest,
            long span,
            long now,
            float indexProgress,
            float cap
    ) {
        long sampleTime = sampleTimeAt(samples, progress, oldest, span);
        float age = (now - sampleTime) / (float) span;
        float ageFade = MathHelper.clamp(1f - age * 0.35f, 0.15f, 1f);
        return Math.min(indexProgress * cap, 1f) * ageFade;
    }

    private static long sampleTimeAt(List<TrailSample> samples, float progress, long oldest, long span) {
        long target = oldest + (long) (span * progress);
        TrailSample best = samples.get(0);
        long bestDelta = Math.abs(best.time() - target);
        for (TrailSample sample : samples) {
            long delta = Math.abs(sample.time() - target);
            if (delta < bestDelta) {
                bestDelta = delta;
                best = sample;
            }
        }
        return best.time();
    }

    private static List<Vec3d> buildCatmullRomCurve(List<TrailSample> samples) {
        int n = samples.size();
        if (n < 2) {
            return List.of();
        }
        if (n < 4) {
            List<Vec3d> linear = new ArrayList<>();
            for (int i = 0; i < n - 1; i++) {
                Vec3d a = samples.get(i).pos();
                Vec3d b = samples.get(i + 1).pos();
                for (int s = 0; s < SPLINE_STEPS; s++) {
                    linear.add(a.lerp(b, s / (double) SPLINE_STEPS));
                }
            }
            linear.add(samples.get(samples.size() - 1).pos());
            return linear;
        }

        List<Vec3d> curve = new ArrayList<>();
        for (int i = 0; i < n - 1; i++) {
            Vec3d p0 = samples.get(Math.max(0, i - 1)).pos();
            Vec3d p1 = samples.get(i).pos();
            Vec3d p2 = samples.get(i + 1).pos();
            Vec3d p3 = samples.get(Math.min(n - 1, i + 2)).pos();
            for (int s = 0; s < SPLINE_STEPS; s++) {
                curve.add(catmullRom(p0, p1, p2, p3, s / (double) SPLINE_STEPS));
            }
        }
        curve.add(samples.get(samples.size() - 1).pos());
        return curve;
    }

    private static Vec3d catmullRom(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        double x = 0.5 * ((2.0 * p1.x) + (-p0.x + p2.x) * t
                + (2.0 * p0.x - 5.0 * p1.x + 4.0 * p2.x - p3.x) * t2
                + (-p0.x + 3.0 * p1.x - 3.0 * p2.x + p3.x) * t3);
        double y = 0.5 * ((2.0 * p1.y) + (-p0.y + p2.y) * t
                + (2.0 * p0.y - 5.0 * p1.y + 4.0 * p2.y - p3.y) * t2
                + (-p0.y + 3.0 * p1.y - 3.0 * p2.y + p3.y) * t3);
        double z = 0.5 * ((2.0 * p1.z) + (-p0.z + p2.z) * t
                + (2.0 * p0.z - 5.0 * p1.z + 4.0 * p2.z - p3.z) * t2
                + (-p0.z + 3.0 * p1.z - 3.0 * p2.z + p3.z) * t3);
        return new Vec3d(x, y, z);
    }

    public static Vec3d playerFeet(MinecraftClient mc, float tickDelta) {
        double x = MathHelper.lerp(tickDelta, mc.player.prevX, mc.player.getX());
        double y = MathHelper.lerp(tickDelta, mc.player.prevY, mc.player.getY());
        double z = MathHelper.lerp(tickDelta, mc.player.prevZ, mc.player.getZ());
        return new Vec3d(x, y, z);
    }

    public record TrailSample(Vec3d pos, long time) {}
}
