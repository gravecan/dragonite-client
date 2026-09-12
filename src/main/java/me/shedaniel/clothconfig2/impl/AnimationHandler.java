package me.shedaniel.clothconfig2.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import me.shedaniel.clothconfig2.impl.builders.ColorFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;


public class AnimationHandler extends ConfigCategoryImpl {

    private static final int MAX_PARTICLES = 80;
    private static final double BLOCK_INSET = 0.0015;

    private static final double DEFAULT_COUNT = 24.0;

    private final DoubleFieldBuilder lifetime;
    private final DoubleFieldBuilder range;
    private final ColorFieldBuilder color;

    private final List<JumpOutline> outlines = new ArrayList<>();
    private boolean wasOnGround = true;
    private boolean jumpQueued = false;

    public AnimationHandler() {
        super("Jump Particles", "Block outlines when you jump", Cat.VISUALS);
        setTooltip("Outlines real blocks around your feet — stays inside each block, fades with distance.");

        lifetime = new DoubleFieldBuilder("Lifetime", "Seconds", 1.1, 0.4, 2.5, 0.1);
        lifetime.setSuffix("s");
        range = new DoubleFieldBuilder("Range", "Max block distance", 3.5, 1.0, 12.0, 0.5);
        color = new ColorFieldBuilder("Color", "Outline color", 255, 255, 255).withRainbowOption();

        addSetting(lifetime);
        addSetting(range);
        addSetting(color);
    }

    @Override
    public void onDisable() {
        outlines.clear();
        wasOnGround = true;
        jumpQueued = false;
    }

    public void onTick() {
        if (!isEnabled()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            return;
        }

        boolean onGround = mc.player.isOnGround();
        if (wasOnGround && mc.options.jumpKey.isPressed()) {
            jumpQueued = true;
        }

        if (wasOnGround && !onGround && mc.player.getVelocity().y > 0 && jumpQueued) {
            spawnBlockOutlines(mc);
            jumpQueued = false;
        }

        if (onGround) {
            jumpQueued = false;
        }
        wasOnGround = onGround;

        trimOutlines();
    }

    private void spawnBlockOutlines(MinecraftClient mc) {
        Vec3d pos = mc.player.getPos();
        double r = range.get();
        int max = (int) DEFAULT_COUNT;
        BlockPos center = BlockPos.ofFloored(pos);
        List<BlockCandidate> candidates = new ArrayList<>();

        int ir = (int) Math.ceil(r);
        for (int dx = -ir; dx <= ir; dx++) {
            for (int dz = -ir; dz <= ir; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > r + 0.15) {
                    continue;
                }
                BlockPos column = center.add(dx, 0, dz);
                BlockPos block = findOutlineBlock(mc, column, center.getY());
                if (block != null) {
                    double cx = block.getX() + 0.5 - pos.x;
                    double cy = block.getY() + 0.5 - pos.y;
                    double cz = block.getZ() + 0.5 - pos.z;
                    candidates.add(new BlockCandidate(block, cx * cx + cy * cy + cz * cz, (float) dist));
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(c -> c.distSq));
        long now = System.currentTimeMillis();
        int n = Math.min(max, candidates.size());
        for (int i = 0; i < n; i++) {
            BlockCandidate c = candidates.get(i);
            float hue = (float) i / Math.max(1, n);
            outlines.add(new JumpOutline(c.pos, c.ringDist, hue, now));
        }
    }

    private static BlockPos findOutlineBlock(MinecraftClient mc, BlockPos column, int feetY) {
        for (int dy = 0; dy >= -2; dy--) {
            BlockPos pos = column.withY(feetY + dy);
            if (isSolidOutlineBlock(mc, pos)) {
                return pos.toImmutable();
            }
        }
        for (int dy = 1; dy <= 1; dy++) {
            BlockPos pos = column.withY(feetY + dy);
            if (isSolidOutlineBlock(mc, pos)) {
                return pos.toImmutable();
            }
        }
        return null;
    }

    private static boolean isSolidOutlineBlock(MinecraftClient mc, BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        return !state.getCollisionShape(mc.world, pos).isEmpty();
    }

    private void trimOutlines() {
        if (outlines.size() <= MAX_PARTICLES) {
            return;
        }
        outlines.subList(0, outlines.size() - MAX_PARTICLES).clear();
    }

    public void render(WorldRenderContext ctx) {
        if (!isEnabled() || outlines.isEmpty()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        long lifeMs = (long) (lifetime.get() * 1000.0);
        float maxRadius = (float) range.get();

        Iterator<JumpOutline> it = outlines.iterator();
        while (it.hasNext()) {
            JumpOutline o = it.next();
            if (nowMs - o.spawnMs > lifeMs) {
                it.remove();
            }
        }
        if (outlines.isEmpty()) {
            return;
        }

        Vec3d cam = ctx.camera().getPos();
        MatrixStack matrices = ctx.matrixStack();
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.lineWidth(2.0f);

        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        boolean drew = false;

        for (JumpOutline o : outlines) {
            float life = (nowMs - o.spawnMs) / (float) lifeMs;
            if (life >= 1f) {
                continue;
            }

            // Wave front propagation check (expanding outwards from center)
            float startLife = (o.ringDist / maxRadius) * 0.4f;
            if (life < startLife) {
                continue; // Wave front hasn't reached this block yet
            }

            float blockProgress = (life - startLife) / (1f - startLife);
            float blockAlpha = 1f;
            if (blockProgress < 0.15f) {
                blockAlpha = blockProgress / 0.15f;
            } else {
                blockAlpha = 1f - (blockProgress - 0.15f) / 0.85f;
            }

            float alpha = blockAlpha * 0.85f;
            if (alpha < 0.035f) {
                continue;
            }

            float[] rgb = outlineColor(o.hue, alpha);
            
            // Render full scale box outline with fade
            Box box = new Box(o.pos).contract(BLOCK_INSET);
            appendBoxOutline(buf, mat, box, rgb[0], rgb[1], rgb[2], rgb[3]);
            drew = true;
        }

        if (drew) {
            BufferRenderer.drawWithGlobalProgram(buf.end());
        } else {
            buf.end();
        }

        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        matrices.pop();
    }

    private float[] outlineColor(float hue, float alphaMul) {
        RainbowManager mgr = RainbowManager.getInstance();
        if (color.isRainbow()) {
            mgr.update(1.0f);
        }
        float[] rgb = new float[3];
        color.resolveRgb(mgr, hue * 64f, hue * 64f, rgb);
        return new float[]{rgb[0], rgb[1], rgb[2], alphaMul};
    }

    private static float fadeAlpha(float t) {
        t = Math.min(1f, Math.max(0f, t));
        if (t < 0.08f) {
            return t / 0.08f;
        }
        return 1f - (t - 0.08f) / 0.92f;
    }

    private static void appendBoxOutline(BufferBuilder buf, Matrix4f mat, Box box,
                                         float r, float g, float b, float a) {
        float x1 = (float) box.minX;
        float y1 = (float) box.minY;
        float z1 = (float) box.minZ;
        float x2 = (float) box.maxX;
        float y2 = (float) box.maxY;
        float z2 = (float) box.maxZ;

        line(buf, mat, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(buf, mat, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(buf, mat, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(buf, mat, x1, y1, z2, x1, y1, z1, r, g, b, a);

        line(buf, mat, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(buf, mat, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(buf, mat, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(buf, mat, x1, y2, z2, x1, y2, z1, r, g, b, a);

        line(buf, mat, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(buf, mat, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(buf, mat, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(buf, mat, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private static void line(BufferBuilder buf, Matrix4f mat,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float r, float g, float b, float a) {
        buf.vertex(mat, x1, y1, z1).color(r, g, b, a);
        buf.vertex(mat, x2, y2, z2).color(r, g, b, a);
    }

    private record BlockCandidate(BlockPos pos, double distSq, float ringDist) {}

    private static final class JumpOutline {
        final BlockPos pos;
        final float ringDist;
        final float hue;
        final long spawnMs;

        JumpOutline(BlockPos pos, float ringDist, float hue, long spawnMs) {
            this.pos = pos;
            this.ringDist = ringDist;
            this.hue = hue;
            this.spawnMs = spawnMs;
        }
    }
}
