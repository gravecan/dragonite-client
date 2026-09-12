package me.shedaniel.clothconfig2.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.ColorFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

public class ConfigEntryImpl extends ConfigCategoryImpl {

    public static final String MODE_2D = "2D";
    public static final String MODE_3D = "3D";

    public static ConfigEntryImpl INSTANCE;

    private final float[] colorScratch = new float[4];
    private final float[] rainbowScratch = new float[3];
    private static final EspBoxScratch BOX_SCRATCH = new EspBoxScratch();

    private final EnumSelectorBuilder hitboxMode;
    private final EnumSelectorBuilder target;
    private final BooleanToggleBuilder glow;
    private final ColorFieldBuilder glowColor;
    private final DoubleFieldBuilder glowOpacity;
    private final BooleanToggleBuilder cornersOnly;
    private final BooleanToggleBuilder showHealth;
    private final DoubleFieldBuilder lineWidth;
    private final DoubleFieldBuilder maxRange;
    private final ColorFieldBuilder playerColor;
    private final ColorFieldBuilder mobColor;
    private final ColorFieldBuilder friendColor;
    private final BooleanToggleBuilder throughWalls;

    public ConfigEntryImpl() {
        super("ESP", "Player & mob hitbox ESP", Cat.VISUALS);
        INSTANCE = this;

        hitboxMode = new EnumSelectorBuilder(
                "Hitbox Mode",
                "2D = flat player box | 3D = world box",
                MODE_2D,
                MODE_2D,
                MODE_3D
        );
        target = new EnumSelectorBuilder("Target", "", "All", "All", "Players", "Mobs");
        glow = new BooleanToggleBuilder("Glow", "Filled glow inside ESP (chams-style)", false);
        glowColor = new ColorFieldBuilder("Glow Color", "Inner glow tint", 255, 255, 255).withRainbowOption();
        glowOpacity = new DoubleFieldBuilder("Glow Opacity", "Brightness/Opacity of glow", 0.42, 0.1, 1.0, 0.01);
        cornersOnly = new BooleanToggleBuilder("Corners Only", "2D corner brackets", false);
        showHealth = new BooleanToggleBuilder("Health Bar", "2D vertical HP strip on the right of the ESP box", true);
        lineWidth = new DoubleFieldBuilder("Line Width", "", 1.0, 0.5, 4.0, 0.25);
        maxRange = new DoubleFieldBuilder("Max Range", "", 64, 8, 256, 8);
        playerColor = new ColorFieldBuilder("Player Color", "", 255, 255, 255).withRainbowOption();
        mobColor = new ColorFieldBuilder("Mob Color", "", 255, 100, 100).withRainbowOption();
        friendColor = new ColorFieldBuilder("Friend Color", "", 0, 255, 100).withRainbowOption();
        throughWalls = new BooleanToggleBuilder("Through Walls", "", true);

        addSetting(hitboxMode);
        addSetting(target);
        addSetting(glow);
        addSetting(glowColor);
        addSetting(glowOpacity);
        addSetting(cornersOnly);
        addSetting(showHealth);
        addSetting(throughWalls);
        addSetting(lineWidth);
        addSetting(maxRange);
        addSetting(playerColor);
        addSetting(mobColor);
        addSetting(friendColor);

        glowColor.setVisibleWhen(glow::get);
        glowOpacity.setVisibleWhen(glow::get);
        cornersOnly.setVisibleWhen(() -> MODE_2D.equals(hitboxMode.get()));
        showHealth.setVisibleWhen(this::is2DMode);
    }

    public void render(WorldRenderContext ctx) {
        if (!isEnabled()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            return;
        }

        WorldRenderEntityCache.rebuild(mc, HudConfigInit.getManager());
        
        RainbowManager rainbowMgr = RainbowManager.getInstance();
        if (ColorFieldBuilder.anyRainbow(playerColor, mobColor, friendColor, glowColor)) {
            rainbowMgr.update(1.0f);
        }

        if (MODE_2D.equals(hitboxMode.get())) {
            float tickDelta = ctx.tickCounter().getTickDelta(true);
            EspProject.capture(
                    ctx.projectionMatrix(),
                    ctx.positionMatrix(),
                    mc.getWindow().getScaledWidth(),
                    mc.getWindow().getScaledHeight()
            );
            
            ScreenOrtho.run(mc, mat -> render2DScreen(mat, mc, tickDelta, rainbowMgr));
            return;
        }
        render3D(ctx, mc, rainbowMgr);
    }

    public boolean is2DMode() {
        return MODE_2D.equals(hitboxMode.get());
    }

    public float queryRange() {
        return (float) maxRange.get();
    }

    public void renderHud(DrawContext drawContext, float tickDelta) {
        
    }

    
    private void render2DScreen(Matrix4f mat, MinecraftClient mc, float tickDelta, RainbowManager rainbowMgr) {
        String tgt = target.get();
        float maxR = (float) maxRange.get();
        float maxRSq = maxR * maxR;
        boolean corners = cornersOnly.get();
        boolean useGlow = glow.get();
        boolean healthBar = showHealth.get();
        float lw = Math.max(1f, (float) lineWidth.get());

        java.util.List<ProjectedEntity> projected = new java.util.ArrayList<>();
        for (LivingEntity living : WorldRenderEntityCache.livingEntities()) {
            Entity e = living;
            if (!e.isAlive() || e.isRemoved() || e.getWorld() != mc.world) {
                continue;
            }
            if (e instanceof PlayerEntity player && player.isSpectator()) {
                continue;
            }
            if (!matchesTarget(e, tgt) || mc.player.squaredDistanceTo(e) > maxRSq) {
                continue;
            }

            float[] bounds = projectEntityBounds(living, tickDelta, mc);
            if (bounds == null) {
                continue;
            }

            resolveColorInto(living, e, rainbowMgr, mc.player.distanceTo(e));
            float r = colorScratch[0];
            float g = colorScratch[1];
            float b = colorScratch[2];
            float a = colorScratch[3];

            float left = bounds[0];
            float top = bounds[1];
            float right = bounds[2];
            float bottom = bounds[3];

            projected.add(new ProjectedEntity(living, left, top, right, bottom, r, g, b, a));
        }

        if (projected.isEmpty()) {
            return;
        }

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        GL11.glLineWidth(1f);

        try {
            if (useGlow) {
                BufferBuilder glowBuf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                boolean drewGlow = false;
                for (ProjectedEntity p : projected) {
                    glowColor.resolveRgb(rainbowMgr, p.living.getId() * 7f, -1, colorScratch);
                    float alpha = (float) glowOpacity.get();
                    appendScreenRect(glowBuf, mat, p.left, p.top, p.right, p.bottom, colorScratch[0], colorScratch[1], colorScratch[2], alpha);
                    drewGlow = true;
                }
                if (drewGlow) {
                    RenderSystem.setShader(GameRenderer::getPositionColorProgram);
                    RenderSystem.blendFunc(770, 1);
                    BufferRenderer.drawWithGlobalProgram(glowBuf.end());
                    RenderSystem.defaultBlendFunc();
                } else {
                    glowBuf.end();
                }
            }

            {
                BufferBuilder lineBuf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                boolean drewLines = false;
                for (ProjectedEntity p : projected) {
                    float boxW = p.right - p.left;
                    float boxH = p.bottom - p.top;
                    float thick = Math.max(1.25f, screenThickness(lw, boxW, boxH));
                    if (corners) {
                        drewLines |= appendScreenCornerBrackets(lineBuf, mat, p.left, p.top, p.right, p.bottom, thick, p.r, p.g, p.b, p.a);
                    } else {
                        appendScreenRectEdges(lineBuf, mat, p.left, p.top, p.right, p.bottom, thick, p.r, p.g, p.b, p.a);
                        drewLines = true;
                    }
                }
                if (drewLines) {
                    RenderSystem.setShader(GameRenderer::getPositionColorProgram);
                    BufferRenderer.drawWithGlobalProgram(lineBuf.end());
                } else {
                    lineBuf.end();
                }
            }

            if (healthBar) {
                BufferBuilder healthBgBuf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                boolean drewHealthBg = false;
                for (ProjectedEntity p : projected) {
                    drewHealthBg |= appendScreenHealthBarBg(healthBgBuf, mat, p.left, p.top, p.right, p.bottom);
                }
                if (drewHealthBg) {
                    RenderSystem.setShader(GameRenderer::getPositionColorProgram);
                    BufferRenderer.drawWithGlobalProgram(healthBgBuf.end());
                } else {
                    healthBgBuf.end();
                }
            }

            if (healthBar) {
                BufferBuilder healthFillBuf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                boolean drewHealthFill = false;
                for (ProjectedEntity p : projected) {
                    float maxHp = Math.max(1f, p.living.getMaxHealth());
                    float healthPct = MathHelper.clamp(p.living.getHealth() / maxHp, 0f, 1f);
                    drewHealthFill |= appendScreenHealthBarFill(healthFillBuf, mat, p.left, p.top, p.right, p.bottom, healthPct);
                }
                if (drewHealthFill) {
                    RenderSystem.setShader(GameRenderer::getPositionColorProgram);
                    BufferRenderer.drawWithGlobalProgram(healthFillBuf.end());
                } else {
                    healthFillBuf.end();
                }
            }
        } finally {
            GL11.glLineWidth(1f);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    private float[] projectEntityBounds(LivingEntity e, float tickDelta, MinecraftClient mc) {
        BOX_SCRATCH.setLerpedWorld(e, tickDelta);

        float[] accum = new float[4];
        int count = 0;

        double[] xs = {BOX_SCRATCH.minX, BOX_SCRATCH.maxX};
        double[] ys = {BOX_SCRATCH.minY, BOX_SCRATCH.maxY};
        double[] zs = {BOX_SCRATCH.minZ, BOX_SCRATCH.maxZ};

        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    boolean isFirst = (count == 0);
                    if (accumulateScreenPoint(mc, x, y, z, isFirst, accum)) {
                        count++;
                    }
                }
            }
        }

        if (count == 0) {
            return null;
        }

        float minX = accum[0];
        float minY = accum[1];
        float maxX = accum[2];
        float maxY = accum[3];

        float cx = (minX + maxX) * 0.5f;
        float cy = (minY + maxY) * 0.5f;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        if (minX > sw + 400 || maxX < -400 || minY > sh + 400 || maxY < -400) {
            return null;
        }

        if ((maxX - minX) < 8f) {
            minX = cx - 4f;
            maxX = cx + 4f;
        }
        if ((maxY - minY) < 8f) {
            minY = cy - 4f;
            maxY = cy + 4f;
        }
        return new float[]{minX, minY, maxX, maxY};
    }

    private static boolean accumulateScreenPoint(MinecraftClient mc, double wx, double wy, double wz,
                                                 boolean isFirst, float[] accum) {
        float[] p = EspProject.worldToScreen(new Vec3d(wx, wy, wz), mc, false);
        if (p == null) {
            return false;
        }
        if (isFirst) {
            accum[0] = p[0];
            accum[1] = p[1];
            accum[2] = p[0];
            accum[3] = p[1];
        } else {
            accum[0] = Math.min(accum[0], p[0]);
            accum[1] = Math.min(accum[1], p[1]);
            accum[2] = Math.max(accum[2], p[0]);
            accum[3] = Math.max(accum[3], p[1]);
        }
        return true;
    }

    private static float screenThickness(float lineWidth, float boxW, float boxH) {
        float base = Math.max(0.5f, lineWidth * 0.5f);
        float cap = Math.min(boxW, boxH) * 0.13f;
        return Math.max(0.5f, Math.min(base, cap));
    }

    private static float cornerArmLength(float boxW, float boxH, float thickness) {
        float minDim = Math.min(boxW, boxH);
        float len = minDim * 0.30f;
        len = Math.max(len, thickness * 3f);
        return Math.max(2f, Math.min(len, minDim * 0.40f));
    }

    private static void appendScreenRect(BufferBuilder buf, Matrix4f mat,
                                         float left, float top, float right, float bottom,
                                         float r, float g, float b, float a) {
        buf.vertex(mat, left, top, 0f).color(r, g, b, a);
        buf.vertex(mat, right, top, 0f).color(r, g, b, a);
        buf.vertex(mat, right, bottom, 0f).color(r, g, b, a);
        buf.vertex(mat, left, bottom, 0f).color(r, g, b, a);
    }

    private static void appendScreenRectEdges(BufferBuilder buf, Matrix4f mat,
                                              float left, float top, float right, float bottom,
                                              float thick, float r, float g, float b, float a) {
        appendScreenEdgeQuad(buf, mat, left, top, right, top + thick, r, g, b, a);
        appendScreenEdgeQuad(buf, mat, left, bottom - thick, right, bottom, r, g, b, a);
        appendScreenEdgeQuad(buf, mat, left, top, left + thick, bottom, r, g, b, a);
        appendScreenEdgeQuad(buf, mat, right - thick, top, right, bottom, r, g, b, a);
    }

    private static void appendScreenEdgeQuad(BufferBuilder buf, Matrix4f mat,
                                           float x1, float y1, float x2, float y2,
                                           float r, float g, float b, float a) {
        buf.vertex(mat, x1, y1, 0f).color(r, g, b, a);
        buf.vertex(mat, x2, y1, 0f).color(r, g, b, a);
        buf.vertex(mat, x2, y2, 0f).color(r, g, b, a);
        buf.vertex(mat, x1, y2, 0f).color(r, g, b, a);
    }

    private static boolean appendScreenCornerBrackets(BufferBuilder buf, Matrix4f mat,
                                                      float left, float top, float right, float bottom,
                                                      float thick, float r, float g, float b, float a) {
        float arm = cornerArmLength(right - left, bottom - top, thick);

        appendScreenEdgeQuad(buf, mat, left, top, left + arm, top + thick, r, g, b, a);
        appendScreenEdgeQuad(buf, mat, left, top, left + thick, top + arm, r, g, b, a);

        appendScreenEdgeQuad(buf, mat, right - arm, top, right, top + thick, r, g, b, a);
        appendScreenEdgeQuad(buf, mat, right - thick, top, right, top + arm, r, g, b, a);

        appendScreenEdgeQuad(buf, mat, left, bottom - thick, left + arm, bottom, r, g, b, a);
        appendScreenEdgeQuad(buf, mat, left, bottom - arm, left + thick, bottom, r, g, b, a);

        appendScreenEdgeQuad(buf, mat, right - arm, bottom - thick, right, bottom, r, g, b, a);
        appendScreenEdgeQuad(buf, mat, right - thick, bottom - arm, right, bottom, r, g, b, a);
        return true;
    }

    private static boolean appendScreenHealthBarBg(BufferBuilder buf, Matrix4f mat,
                                                   float left, float top, float right, float bottom) {
        float gap = 3f;
        float barW = 2.5f;
        float barLeft = right + gap;
        float barRight = barLeft + barW;
        appendScreenRect(buf, mat, barLeft, top, barRight, bottom, 0f, 0f, 0f, 0.85f);
        return true;
    }

    private static boolean appendScreenHealthBarFill(BufferBuilder buf, Matrix4f mat,
                                                     float left, float top, float right, float bottom,
                                                     float health) {
        health = MathHelper.clamp(health, 0f, 1f);
        if (health <= 0.01f) {
            return false;
        }
        float gap = 3f;
        float barW = 2.5f;
        float barLeft = right + gap;
        float barRight = barLeft + barW;
        float boxH = bottom - top;
        float fillTop = bottom - boxH * health;
        float hr = Math.min(1f, 2f * (1f - health));
        float hg = Math.min(1f, 2f * health);
        appendScreenRect(buf, mat, barLeft, fillTop, barRight, bottom, hr, hg, 0.08f, 1f);
        return true;
    }

    private static void screenLine(BufferBuilder buf, Matrix4f mat,
                                   float x1, float y1, float x2, float y2,
                                   float r, float g, float b, float a) {
        buf.vertex(mat, x1, y1, 0f).color(r, g, b, a);
        buf.vertex(mat, x2, y2, 0f).color(r, g, b, a);
    }

    private void render3D(WorldRenderContext ctx, MinecraftClient mc, RainbowManager rainbowMgr) {
        float tickDelta = ctx.tickCounter().getTickDelta(true);
        MatrixStack matrices = ctx.matrixStack();
        Vec3d cam = ctx.camera().getPos();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        String tgt = target.get();
        float maxR = (float) maxRange.get();
        float maxRSq = maxR * maxR;
        boolean corners = cornersOnly.get();
        boolean useGlow = glow.get();
        boolean xray = throughWalls.get();

        BufferBuilder glowBuf = useGlow
                ? Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
                : null;
        BufferBuilder outlineBuf = !useGlow
                ? Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR)
                : null;

        boolean drewGlow = false;
        boolean drewOutline = false;

        for (LivingEntity living : WorldRenderEntityCache.livingEntities()) {
            Entity e = living;
            if (!matchesTarget(e, tgt) || mc.player.squaredDistanceTo(e) > maxRSq) {
                continue;
            }

            resolveColorInto(living, e, rainbowMgr, mc.player.distanceTo(e));
            float r = colorScratch[0];
            float g = colorScratch[1];
            float b = colorScratch[2];
            float a = colorScratch[3];

            double ex = MathHelper.lerp(tickDelta, e.prevX, e.getX()) - cam.x;
            double ey = MathHelper.lerp(tickDelta, e.prevY, e.getY()) - cam.y;
            double ez = MathHelper.lerp(tickDelta, e.prevZ, e.getZ()) - cam.z;
            float hw = e.getWidth() * 0.5f;
            float height = e.getHeight();

            matrices.push();
            matrices.translate(ex, ey, ez);
            Matrix4f mat = matrices.peek().getPositionMatrix();

            if (useGlow) {
                glowColor.resolveRgb(rainbowMgr, living.getId() * 7f, -1, colorScratch);
                float alpha = (float) glowOpacity.get();
                appendLocalGlowBox(glowBuf, mat, hw, height, colorScratch[0], colorScratch[1], colorScratch[2], alpha);
                drewGlow = true;
            }
            if (outlineBuf != null) {
                appendLocalBoxOutline(outlineBuf, mat, hw, height, r, g, b, a);
                drewOutline = true;
            }
            matrices.pop();
        }

        if (drewGlow && glowBuf != null) {
            if (xray) {
                RenderSystem.disableDepthTest();
            } else {
                RenderSystem.enableDepthTest();
            }
            RenderSystem.depthMask(false);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            BufferRenderer.drawWithGlobalProgram(glowBuf.end());
            RenderSystem.depthMask(true);
        } else if (glowBuf != null) {
            glowBuf.end();
        }

        if (drewOutline && outlineBuf != null) {
            if (xray) {
                RenderSystem.disableDepthTest();
            } else {
                RenderSystem.enableDepthTest();
            }
            RenderSystem.disableCull();
            BufferRenderer.drawWithGlobalProgram(outlineBuf.end());
        } else if (outlineBuf != null) {
            outlineBuf.end();
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void appendLocalGlowBox(BufferBuilder buf, Matrix4f mat, float hw, float height,
                                           float r, float g, float b, float a) {
        float pad = Math.min(0.06f, hw * 0.08f);
        float minX = -hw + pad;
        float maxX = hw - pad;
        float minY = pad;
        float maxY = height - pad;
        float minZ = -hw + pad;
        float maxZ = hw - pad;
        if (maxX <= minX || maxY <= minY || maxZ <= minZ) {
            return;
        }
        quad(buf, mat, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        quad(buf, mat, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
        quad(buf, mat, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a);
        quad(buf, mat, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        quad(buf, mat, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
        quad(buf, mat, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);
    }

    private static void appendLocalBoxOutline(BufferBuilder buf, Matrix4f mat, float hw, float height,
                                              float r, float g, float b, float a) {
        float e = 0.002f;
        line(buf, mat, -hw - e, 0, -hw - e, hw + e, 0, -hw - e, r, g, b, a);
        line(buf, mat, hw + e, 0, -hw - e, hw + e, 0, hw + e, r, g, b, a);
        line(buf, mat, hw + e, 0, hw + e, -hw - e, 0, hw + e, r, g, b, a);
        line(buf, mat, -hw - e, 0, hw + e, -hw - e, 0, -hw - e, r, g, b, a);
        line(buf, mat, -hw - e, height, -hw - e, hw + e, height, -hw - e, r, g, b, a);
        line(buf, mat, hw + e, height, -hw - e, hw + e, height, hw + e, r, g, b, a);
        line(buf, mat, hw + e, height, hw + e, -hw - e, height, hw + e, r, g, b, a);
        line(buf, mat, -hw - e, height, hw + e, -hw - e, height, -hw - e, r, g, b, a);
        line(buf, mat, -hw - e, 0, -hw - e, -hw - e, height, -hw - e, r, g, b, a);
        line(buf, mat, hw + e, 0, -hw - e, hw + e, height, -hw - e, r, g, b, a);
        line(buf, mat, hw + e, 0, hw + e, hw + e, height, hw + e, r, g, b, a);
        line(buf, mat, -hw - e, 0, hw + e, -hw - e, height, hw + e, r, g, b, a);
    }

    private static void quad(BufferBuilder buf, Matrix4f mat,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float r, float g, float b, float a) {
        buf.vertex(mat, x1, y1, z1).color(r, g, b, a);
        buf.vertex(mat, x2, y2, z2).color(r, g, b, a);
        buf.vertex(mat, x3, y3, z3).color(r, g, b, a);
        buf.vertex(mat, x4, y4, z4).color(r, g, b, a);
    }

    private static void line(BufferBuilder buf, Matrix4f mat,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float r, float g, float b, float a) {
        buf.vertex(mat, x1, y1, z1).color(r, g, b, a);
        buf.vertex(mat, x2, y2, z2).color(r, g, b, a);
    }

    private static boolean matchesTarget(Entity e, String tgt) {
        boolean isPlayer = e instanceof PlayerEntity;
        boolean isMob = e instanceof MobEntity;
        if ("Players".equals(tgt)) {
            return isPlayer;
        }
        if ("Mobs".equals(tgt)) {
            return isMob;
        }
        return isPlayer || isMob;
    }

    private void resolveColorInto(LivingEntity living, Entity e, RainbowManager rainbowMgr, double dist) {
        ColorFieldBuilder field;
        if (e instanceof PlayerEntity player
                && FriendManager.getInstance() != null
                && FriendManager.getInstance().isFriend(player)) {
            field = friendColor;
        } else if (e instanceof PlayerEntity) {
            field = playerColor;
        } else {
            field = mobColor;
        }

        field.resolveRgb(rainbowMgr, (float) (dist * 2.5f), dist, rainbowScratch);
        colorScratch[0] = rainbowScratch[0];
        colorScratch[1] = rainbowScratch[1];
        colorScratch[2] = rainbowScratch[2];
        colorScratch[3] = 1f;
    }

    private static class ProjectedEntity {
        final LivingEntity living;
        final float left, top, right, bottom;
        final float r, g, b, a;
        ProjectedEntity(LivingEntity living, float left, float top, float right, float bottom, float r, float g, float b, float a) {
            this.living = living;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }
    }
}
