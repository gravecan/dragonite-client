package me.shedaniel.clothconfig2.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.ColorFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Config_Tracers extends ConfigCategoryImpl {

    public static Config_Tracers INSTANCE;

    private final BooleanToggleBuilder traceMobs;
    private final EnumSelectorBuilder aimAt;
    private final BooleanToggleBuilder throughWalls;
    private final ColorFieldBuilder colorPlayers;
    private final ColorFieldBuilder colorMobs;
    private final ColorFieldBuilder friendColor;
    private final DoubleFieldBuilder lineWidth;
    private final DoubleFieldBuilder alpha;
    private final DoubleFieldBuilder maxRange;

    private final float[] rainbowScratch = new float[3];

    public Config_Tracers() {
        super("Tracers", "ESP lines to nearby entities", Cat.VISUALS);
        INSTANCE = this;
        setEnabled(false);

        traceMobs = new BooleanToggleBuilder("Trace Mobs", "", false);
        aimAt = new EnumSelectorBuilder("Aim At", "Body part the line targets", "Chest", "Head", "Chest", "Legs", "Feet");
        throughWalls = new BooleanToggleBuilder("Through Walls", "", true);
        colorPlayers = new ColorFieldBuilder("Player Color", "", 255, 255, 255).withRainbowOption();
        colorMobs = new ColorFieldBuilder("Mob Color", "", 255, 140, 100).withRainbowOption();
        friendColor = new ColorFieldBuilder("Friend Color", "", 0, 255, 100).withRainbowOption();
        lineWidth = new DoubleFieldBuilder("Line Width", "", 2.0, 0.5, 5.0, 0.5);
        alpha = new DoubleFieldBuilder("Alpha", "", 1.0, 0.1, 1.0, 0.05);
        maxRange = new DoubleFieldBuilder("Max Range", "", 80, 10, 200, 10);

        addSetting(traceMobs);
        addSetting(aimAt);
        addSetting(throughWalls);
        addSetting(colorPlayers);
        addSetting(colorMobs);
        addSetting(friendColor);
        addSetting(alpha);
        addSetting(maxRange);
    }

    public float queryRange() {
        return (float) maxRange.get();
    }

    public void render(WorldRenderContext ctx) {
        if (!isEnabled()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            return;
        }

        float tickDelta = ctx.tickCounter().getTickDelta(true);

        float maxR = (float) maxRange.get();
        float maxRSq = maxR * maxR;
        List<LivingEntity> targets = collectTargets(mc, maxRSq);
        if (targets.isEmpty()) {
            return;
        }

        RainbowManager rainbowMgr = RainbowManager.getInstance();
        if (ColorFieldBuilder.anyRainbow(colorPlayers, colorMobs, friendColor)) {
            rainbowMgr.update(1.0f);
        }

        EspProject.capture(
                ctx.projectionMatrix(),
                ctx.positionMatrix(),
                mc.getWindow().getScaledWidth(),
                mc.getWindow().getScaledHeight()
        );

        ScreenOrtho.run(mc, mat -> render2DTracers(mat, mc, tickDelta, rainbowMgr, targets, maxRSq));
    }

    private void render2DTracers(Matrix4f mat, MinecraftClient mc, float tickDelta, RainbowManager rainbowMgr, List<LivingEntity> targets, float maxRSq) {
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        float cx = sw / 2f;
        float cy = sh / 2f;

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        float fa = (float) alpha.get();
        float lw = 2.0f;

        
        BufferBuilder glowBuf = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        boolean drewGlow = false;

        for (LivingEntity e : targets) {
            if (shouldSkipTarget(e, mc, maxRSq)) continue;

            float[] p = getTargetPos(e, mc, tickDelta);
            if (p == null) continue;
            float tx = p[0];
            float tY = p[1];

            float[] rgb = resolveColor(e, mc, rainbowMgr, maxRSq);

            glowBuf.vertex(mat, cx, cy, 0f).color(rgb[0], rgb[1], rgb[2], fa * 0.3f);
            glowBuf.vertex(mat, tx, tY, 0f).color(rgb[0], rgb[1], rgb[2], fa * 0.4f);
            drewGlow = true;
        }

        if (drewGlow) {
            RenderSystem.lineWidth(lw + 3f);
            BufferRenderer.drawWithGlobalProgram(glowBuf.end());
        } else {
            glowBuf.end();
        }

        
        BufferBuilder mainBuf = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        boolean drewMain = false;

        for (LivingEntity e : targets) {
            if (shouldSkipTarget(e, mc, maxRSq)) continue;

            float[] p = getTargetPos(e, mc, tickDelta);
            if (p == null) continue;
            float tx = p[0];
            float tY = p[1];

            float[] rgb = resolveColor(e, mc, rainbowMgr, maxRSq);

            mainBuf.vertex(mat, cx, cy, 0f).color(rgb[0], rgb[1], rgb[2], fa * 0.5f);
            mainBuf.vertex(mat, tx, tY, 0f).color(rgb[0], rgb[1], rgb[2], fa);
            drewMain = true;
        }

        if (drewMain) {
            RenderSystem.lineWidth(lw);
            BufferRenderer.drawWithGlobalProgram(mainBuf.end());
        } else {
            mainBuf.end();
        }

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.lineWidth(1f);
    }

    private boolean shouldSkipTarget(LivingEntity e, MinecraftClient mc, float maxRSq) {
        if (!e.isAlive() || e.isRemoved() || e.getWorld() != mc.world) return true;
        boolean isPlayer = e instanceof PlayerEntity;
        boolean isMob = e instanceof MobEntity;
        if (!isPlayer && !(isMob && traceMobs.get())) return true;
        if (isPlayer && ((PlayerEntity) e).isSpectator()) return true;
        if (mc.player.squaredDistanceTo(e) > maxRSq) return true;
        if (!throughWalls.get() && !mc.player.canSee(e)) return true;
        return false;
    }

    private float[] getTargetPos(LivingEntity e, MinecraftClient mc, float tickDelta) {
        Vec3d lerped = e.getLerpedPos(tickDelta);
        double ty = EntityBodyAim.worldY(e, aimAt.get(), tickDelta);
        float[] screenPos = EspProject.worldToScreenDir(new Vec3d(lerped.x, ty, lerped.z), mc);
        if (screenPos == null) return null;

        float tx = screenPos[0];
        float tY = screenPos[1];
        boolean behind = screenPos.length > 2 && screenPos[2] > 0.5f;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        float cx = sw / 2f;
        float cy = sh / 2f;

        // Behind the camera: push the line toward the screen edge in that
        // direction so tracers keep tracking instead of vanishing (FOV lock).
        if (behind) {
            float dx = tx - cx;
            float dy = tY - cy;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 0.001f) {
                dx = 0f;
                dy = 1f;
                len = 1f;
            }
            dx /= len;
            dy /= len;
            float[] edge = clipRayToScreen(cx, cy, dx, dy, sw, sh);
            return new float[]{edge[0], edge[1]};
        }

        // Off-screen but in front: clip to the viewport edge so the line still
        // points at the real player position instead of guessing.
        if (tx < 0 || tx > sw || tY < 0 || tY > sh) {
            float dx = tx - cx;
            float dy = tY - cy;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > 0.001f) {
                float[] edge = clipRayToScreen(cx, cy, dx / len, dy / len, sw, sh);
                return new float[]{edge[0], edge[1]};
            }
        }

        return new float[]{tx, tY};
    }

    /** Intersect center→direction with the screen rectangle. */
    private static float[] clipRayToScreen(float cx, float cy, float dx, float dy, int sw, int sh) {
        float margin = 2f;
        float tMax = Float.POSITIVE_INFINITY;
        if (dx > 0.0001f) {
            tMax = Math.min(tMax, (sw - margin - cx) / dx);
        } else if (dx < -0.0001f) {
            tMax = Math.min(tMax, (margin - cx) / dx);
        }
        if (dy > 0.0001f) {
            tMax = Math.min(tMax, (sh - margin - cy) / dy);
        } else if (dy < -0.0001f) {
            tMax = Math.min(tMax, (margin - cy) / dy);
        }
        if (!Float.isFinite(tMax) || tMax < 0f) {
            tMax = Math.max(sw, sh);
        }
        return new float[]{cx + dx * tMax, cy + dy * tMax};
    }

    private float[] resolveColor(LivingEntity e, MinecraftClient mc, RainbowManager rainbowMgr, float maxRSq) {
        boolean isPlayer = e instanceof PlayerEntity;
        double distSq = mc.player.squaredDistanceTo(e);
        float r, g, b;
        ColorFieldBuilder field;
        if (isPlayer && FriendManager.getInstance() != null && FriendManager.getInstance().isFriend((PlayerEntity) e)) {
            field = friendColor;
        } else if (isPlayer) {
            field = colorPlayers;
        } else {
            field = colorMobs;
        }
        field.resolveRgb(rainbowMgr, (float) (Math.sqrt(distSq) * 2.5f), Math.sqrt(distSq), rainbowScratch);
        r = rainbowScratch[0];
        g = rainbowScratch[1];
        b = rainbowScratch[2];
        return new float[]{r, g, b};
    }

    
    private static List<LivingEntity> collectTargets(MinecraftClient mc, double maxDistSq) {
        List<LivingEntity> out = new ArrayList<>(32);
        Set<LivingEntity> seen = new HashSet<>();

        if (mc.world == null) {
            return out;
        }

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof LivingEntity living) {
                if (living == mc.player || living.isRemoved() || !living.isAlive() || living.isSpectator()) {
                    continue;
                }
                if (living.squaredDistanceTo(mc.player) <= maxDistSq && seen.add(living)) {
                    out.add(living);
                }
            }
        }

        return out;
    }
}
