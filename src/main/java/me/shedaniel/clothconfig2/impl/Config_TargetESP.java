package me.shedaniel.clothconfig2.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.ColorFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;


public class Config_TargetESP extends ConfigCategoryImpl {

    private final EnumSelectorBuilder style = new EnumSelectorBuilder("Style", "Target ESP style", "Orbits",
        "Orbits", "Square", "Helix", "Smooth Circle");
    private final DoubleFieldBuilder intensitySetting = new DoubleFieldBuilder("Intensity", "Opacity", 1.0, 0.1, 1.0, 0.01);
    private final DoubleFieldBuilder scaleSetting = new DoubleFieldBuilder("Scale", "Scale", 1.0, 0.5, 2.0, 0.01);
    private final DoubleFieldBuilder speedSetting = new DoubleFieldBuilder("Speed", "Speed", 1.0, 0.5, 2.0, 0.01);
    private final DoubleFieldBuilder radiusSetting = new DoubleFieldBuilder("Radius", "Orbit Distance", 1.0, 0.5, 3.0, 0.01);
    private final DoubleFieldBuilder countSetting = new DoubleFieldBuilder("Count", "Number of particles", 8.0, 4.0, 16.0, 1.0);
    private final ColorFieldBuilder colorSetting = new ColorFieldBuilder("Color", "Base Color", 255, 101, 57).withRainbowOption();


    private double kolcoStep = 0.0;
    private static final double RING_SPEED = 0.035;

    
    private LivingEntity lastTarget = null;
    private float fadeAnim = 0f;
    private long lastRenderTime = 0;
    private long lastAcquireTime = 0;
    
    
    private LivingEntity hitTarget = null;
    private long hitTargetTime = 0;
    private final DoubleFieldBuilder persistMs = new DoubleFieldBuilder("Disappear Time", "Time after hit before ESP fades", 3.0, 0.0, 10.0, 0.1);

    
    private static final int TRAIL_SAMPLES = 72;

    private float markerRotProgress = 0f;
    private float markerRotFrom = -280f;
    private float markerRotTo = 280f;
    private long markerRotLastMs = 0L;

    private String cachedStyle = "Ghosts";
    private float ghostAnimationTime = 0f;
    private long lastGhostUpdateTimestamp = 0L;

    private static final Identifier MARKER_TEX = Identifier.of("cloth-config2", "textures/target_marker.png");
    private float bloomCamYaw, bloomCamPitch;
    private int bloomR, bloomG, bloomB;
    private float billRx, billRy, billRz;
    private float billUx, billUy, billUz;


    public Config_TargetESP() {
        super("TargetESP", "Ghost-style target ESP with persistence", Cat.VISUALS);
        addSetting(style);
        addSetting(intensitySetting);
        addSetting(scaleSetting);
        addSetting(speedSetting);
        addSetting(radiusSetting);
        addSetting(countSetting);
        addSetting(colorSetting);
        addSetting(persistMs);
        persistMs.setSuffix("s");
        countSetting.setVisibleWhen(() -> style.get().equals("Orbits"));
    }

    public void render(WorldRenderContext context) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        LivingEntity currentTarget = getTarget(mc);
        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - lastRenderTime) / 1000f);
        lastRenderTime = now;

        if (currentTarget != null && currentTarget.isAlive()) {
            lastTarget = currentTarget;
            fadeAnim = Math.min(1f, fadeAnim + dt * 4f); 
        } else {
            fadeAnim -= dt * 3f; 
            if (fadeAnim <= 0f || (lastTarget != null && !lastTarget.isAlive())) {
                fadeAnim = 0f;
                lastTarget = null;
            }
        }

        if (lastTarget == null || fadeAnim <= 0.01f) return;

        float tickDelta = context.tickCounter().getTickDelta(true);
        kolcoStep += RING_SPEED * speedSetting.get();

        long currentTime = System.currentTimeMillis();
        if (lastGhostUpdateTimestamp > 0) {
            float dtUpdate = (currentTime - lastGhostUpdateTimestamp) / 1000f;
            ghostAnimationTime += dtUpdate * 4f * (float) speedSetting.get();
        }
        lastGhostUpdateTimestamp = currentTime;

        String s = normalizeStyle(style.get());
        if (!s.equals(cachedStyle)) {
            cachedStyle = s;
            resetStyleState();
        }

        try {
            if ("Square".equals(s)) {
                renderMarker(context, lastTarget, fadeAnim, tickDelta);
            } else if ("Helix".equals(s)) {
                renderHelix(context, lastTarget, fadeAnim, tickDelta);
            } else if ("Smooth Circle".equals(s)) {
                renderSmoothRing(context, lastTarget, fadeAnim, tickDelta);
            } else {
                renderGhosts(context, lastTarget, fadeAnim, tickDelta);
            }
        } catch (Exception ignored) {
            safeCleanupRender(context);
        }
    }

    private void resetStyleState() {
        ghostAnimationTime = 0f;
        lastGhostUpdateTimestamp = 0L;
        markerRotProgress = 0f;
        markerRotFrom = -280f;
        markerRotTo = 280f;
        markerRotLastMs = System.currentTimeMillis();
        kolcoStep = 0.0;
    }

    private void safeCleanupRender(WorldRenderContext ctx) {
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.lineWidth(1f);
    }

    private static String normalizeStyle(String raw) {
        if (raw == null) {
            return "Orbits";
        }
        String lower = raw.toLowerCase(java.util.Locale.ROOT).trim();
        if (lower.contains("orbit") || lower.contains("ghost")) {
            return "Orbits";
        }
        if (lower.contains("square") || lower.contains("marker")) {
            return "Square";
        }
        if (lower.contains("helix")) {
            return "Helix";
        }
        if (lower.contains("circle") || lower.contains("ring")) {
            return "Smooth Circle";
        }
        return "Orbits";
    }

    private float opacity(float anim) {
        double v = intensitySetting.get();
        if (v < 0.0) v = 0.0;
        if (v > 1.0) v = 1.0;
        return (float) (anim * v);
    }

    private LivingEntity getTarget(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        long persistDuration = (long) (persistMs.get() * 1000.0);

        if (hitTarget != null && (!hitTarget.isAlive() || hitTarget == mc.player)) {
            hitTarget = null;
            hitTargetTime = 0;
        }

        LivingEntity live = resolveLiveTarget(mc);
        if (live != null) {
            hitTarget = live;
            hitTargetTime = now;
            return live;
        }

        Entity hitRegTarget = HitRegistration.getLastTarget();
        if (hitRegTarget instanceof LivingEntity le && le.isAlive() && le != mc.player) {
            long timeSinceHit = now - HitRegistration.getLastAttackTime();
            if (timeSinceHit < persistDuration) {
                if (hitTarget != le) {
                    hitTarget = le;
                    hitTargetTime = now;
                }
                return le;
            }
        }

        if (hitTarget != null && hitTarget.isAlive() && hitTarget != mc.player) {
            if (now - hitTargetTime < persistDuration) {
                return hitTarget;
            }
            hitTarget = null;
        }

        return null;
    }

    private static LivingEntity resolveLiveTarget(MinecraftClient mc) {
        if (mc.crosshairTarget instanceof EntityHitResult ehr) {
            if (ehr.getEntity() instanceof LivingEntity looked
                    && looked.isAlive()
                    && looked != mc.player) {
                return looked;
            }
        }



        Config_FloatList aim = Config_FloatList.INSTANCE;
        if (aim != null && aim.isEnabled()) {
            int id = aim.getAimLockedTargetId();
            if (id >= 0 && mc.world != null) {
                Entity entity = mc.world.getEntityById(id);
                if (entity instanceof LivingEntity le && le.isAlive() && le != mc.player) {
                    return le;
                }
            }
        }

        return null;
    }
    
    
    public void onHitEntity(LivingEntity target) {
        if (target == null || !target.isAlive()) return;
        hitTarget = target;
        hitTargetTime = System.currentTimeMillis();
        lastTarget = target;
        fadeAnim = 1f; 
    }

    private Vec3d targetPos(LivingEntity target, float tickDelta) {
        return new Vec3d(
                MathHelper.lerp(tickDelta, target.prevX, target.getX()),
                MathHelper.lerp(tickDelta, target.prevY, target.getY()),
                MathHelper.lerp(tickDelta, target.prevZ, target.getZ())
        );
    }

    
    private void renderSmoothRing(WorldRenderContext ctx, LivingEntity target, float anim, float tickDelta) {
        if (anim <= 0.001f) {
            return;
        }
        Camera camera = ctx.camera();
        Vec3d rPos = targetPos(target, tickDelta);
        float entityHeight = target.getHeight();
        Vec3d camPos = camera.getPos();

        double relX = rPos.x - camPos.x;
        double relY = rPos.y - camPos.y;
        double relZ = rPos.z - camPos.z;
        double radius = radiusSetting.get() * 0.55;

        double duration = 2000.0 / Math.max(0.05, speedSetting.get());
        double elapsed = System.currentTimeMillis() % (long) duration;
        boolean side = elapsed > duration / 2.0;
        double progress = elapsed / (duration / 2.0);
        if (side) {
            progress -= 1.0;
        } else {
            progress = 1.0 - progress;
        }
        progress = progress < 0.5
                ? 2.0 * progress * progress
                : 1.0 - Math.pow(-2.0 * progress + 2.0, 2.0) / 2.0;
        double eased = entityHeight / 1.2 * (progress > 0.5 ? 1.0 - progress : progress) * (side ? -1 : 1);

        int baseCol = displayArgb(0f);
        int r = (baseCol >> 16) & 0xFF;
        int g = (baseCol >> 8) & 0xFF;
        int b = baseCol & 0xFF;
        float intensity = opacity(anim);
        int colorWithAlpha = multAlpha(baseCol, intensity * (225f / 255f));
        int colorTransparent = multAlpha(baseCol, intensity * (1f / 255f));
        int colorFull = multAlpha(baseCol, intensity);

        Matrix4f matrix = ctx.matrixStack().peek().getPositionMatrix();

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder buffer = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= 360; i++) {
            double rad = Math.toRadians(i);
            float px = (float) (relX + Math.cos(rad) * radius);
            float pz = (float) (relZ + Math.sin(rad) * radius);
            float py1 = (float) (relY + entityHeight * progress);
            float py2 = (float) (relY + entityHeight * progress + eased);
            buffer.vertex(matrix, px, py1, pz).color(r, g, b, (colorWithAlpha >> 24) & 0xFF);
            buffer.vertex(matrix, px, py2, pz).color(r, g, b, (colorTransparent >> 24) & 0xFF);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.lineWidth(2f);
        BufferBuilder lineBuffer = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < 360; i++) {
            double rad1 = Math.toRadians(i);
            double rad2 = Math.toRadians(i + 1);
            float py = (float) (relY + entityHeight * progress);
            
            float px1 = (float) (relX + Math.cos(rad1) * radius);
            float pz1 = (float) (relZ + Math.sin(rad1) * radius);
            lineBuffer.vertex(matrix, px1, py, pz1).color(r, g, b, (colorFull >> 24) & 0xFF);
            
            float px2 = (float) (relX + Math.cos(rad2) * radius);
            float pz2 = (float) (relZ + Math.sin(rad2) * radius);
            lineBuffer.vertex(matrix, px2, py, pz2).color(r, g, b, (colorFull >> 24) & 0xFF);
        }
        BufferRenderer.drawWithGlobalProgram(lineBuffer.end());

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.lineWidth(1f);
    }

    private static float easeOutCubic(float t) {
        t = MathHelper.clamp(t, 0.0f, 1.0f);
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    
    private void renderMarker(WorldRenderContext ctx, LivingEntity target, float anim, float tickDelta) {
        if (anim <= 0.001f) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        InjectedClientAssets.ensureClientTextures(mc);
        boolean packPresent = false;
        try {
            packPresent = mc.getResourceManager().getResource(MARKER_TEX).isPresent();
        } catch (Throwable ignored) {
        }
        if (!packPresent && !InjectedClientAssets.texturesReady()) {
            renderCornerBrackets(ctx, target, anim, tickDelta);
            return;
        }

        Camera camera = ctx.camera();
        Vec3d rPos = targetPos(target, tickDelta);
        Vec3d camPos = camera.getPos();

        long now = System.currentTimeMillis();
        float dt = Math.max(0.001f, (now - markerRotLastMs) / 1000f);
        markerRotLastMs = now;
        float cycleDuration = Math.max(0.35f, 2.2f / (float) speedSetting.get());
        markerRotProgress += dt / cycleDuration;
        while (markerRotProgress >= 1f) {
            markerRotProgress -= 1f;
            markerRotFrom = markerRotTo;
            markerRotTo = markerRotTo > 0f ? -280f : 280f;
        }
        float easedSpin = easeOutCubic(markerRotProgress);
        float rotation = MathHelper.lerp(easedSpin, markerRotFrom, markerRotTo);

        int baseCol = displayArgb(0f);
        int r = (baseCol >> 16) & 0xFF;
        int g = (baseCol >> 8) & 0xFF;
        int b = baseCol & 0xFF;
        int a = MathHelper.clamp((int) (255 * opacity(anim)), 0, 255);

        float half = (float) (scaleSetting.get() * 0.35f * anim);

        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0, MARKER_TEX);

        ctx.matrixStack().push();
        ctx.matrixStack().translate(
                rPos.x - camPos.x,
                rPos.y - camPos.y + (target.getHeight() + 0.4f) * 0.5f,
                rPos.z - camPos.z);
        ctx.matrixStack().multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        ctx.matrixStack().multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        ctx.matrixStack().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));

        Matrix4f m = ctx.matrixStack().peek().getPositionMatrix();
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        bb.vertex(m, -half, -half, 0).texture(0, 1).color(r, g, b, a);
        bb.vertex(m, half, -half, 0).texture(1, 1).color(r, g, b, a);
        bb.vertex(m, half, half, 0).texture(1, 0).color(r, g, b, a);
        bb.vertex(m, -half, half, 0).texture(0, 0).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(bb.end());
        ctx.matrixStack().pop();

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    
    private void renderCornerBrackets(WorldRenderContext ctx, LivingEntity target, float anim, float tickDelta) {
        Camera camera = ctx.camera();
        Vec3d rPos = targetPos(target, tickDelta);
        Vec3d camPos = camera.getPos();

        float sp = (float) speedSetting.get();
        float spin = (System.currentTimeMillis() % 2000L) / (2000.0f / sp) * 360f;
        float w = target.getWidth() * 1.35f * (float) radiusSetting.get();
        float h = target.getHeight() * 1.05f;
        float arm = Math.min(w, h) * 0.28f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        ctx.matrixStack().push();
        ctx.matrixStack().translate(rPos.x - camPos.x, rPos.y - camPos.y, rPos.z - camPos.z);
        ctx.matrixStack().multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));

        int color = multAlpha(displayArgb(anim), opacity(anim));
        int a = Math.max(1, (color >> 24) & 0xFF);
        int cr = (color >> 16) & 0xFF;
        int cg = (color >> 8) & 0xFF;
        int cb = color & 0xFF;
        float halfW = w / 2f;

        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        Matrix4f m = ctx.matrixStack().peek().getPositionMatrix();
        drawCornerBracket(bb, m, -halfW, 0, -halfW, arm, cr, cg, cb, a);
        drawCornerBracket(bb, m, halfW, 0, -halfW, arm, cr, cg, cb, a);
        drawCornerBracket(bb, m, halfW, 0, halfW, arm, cr, cg, cb, a);
        drawCornerBracket(bb, m, -halfW, 0, halfW, arm, cr, cg, cb, a);
        drawCornerBracket(bb, m, -halfW, h, -halfW, arm, cr, cg, cb, a);
        drawCornerBracket(bb, m, halfW, h, -halfW, arm, cr, cg, cb, a);
        drawCornerBracket(bb, m, halfW, h, halfW, arm, cr, cg, cb, a);
        drawCornerBracket(bb, m, -halfW, h, halfW, arm, cr, cg, cb, a);
        BufferRenderer.drawWithGlobalProgram(bb.end());
        ctx.matrixStack().pop();

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void drawCornerBracket(BufferBuilder bb, Matrix4f m,
                                          float cx, float cy, float cz, float arm,
                                          int r, int g, int b, int a) {
        float sx = cx >= 0 ? -arm : arm;
        float sz = cz >= 0 ? -arm : arm;
        addLine(bb, m, cx, cy, cz, cx + sx, cy, cz, r, g, b, a);
        addLine(bb, m, cx, cy, cz, cx, cy, cz + sz, r, g, b, a);
    }

    private void renderHelix(WorldRenderContext ctx, LivingEntity target, float anim, float tickDelta) {
        if (anim <= 0.001f) {
            return;
        }
        if (!beginBloom(ctx, target, tickDelta)) {
            return;
        }
        float rad = Math.max(0.4f, target.getWidth()) * (float) radiusSetting.get();
        float h = target.getHeight();
        float time = (float) ((System.currentTimeMillis() % 1000000L) / 1000.0);
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        Matrix4f base = ctx.matrixStack().peek().getPositionMatrix();

        for (int i = 0; i < 72; i++) {
            float progress = i / 72.0f;
            float t = time * 3.5f - i * 0.02f;
            int alpha = (int) (opacity(anim) * (1.0f - progress * progress) * 180.0f);
            float size = 0.05f * (1.0f - progress) * (float) scaleSetting.get();
            float y = (float) Math.sin(t * 0.5f) * (h * 0.6f);
            float angle = t + (float) Math.sin(t * 0.8f) * 0.5f;
            drawBloomBatched(bb, base, (float) Math.cos(angle) * rad, y, (float) Math.sin(angle) * rad, size, alpha, t * 20.0f);
            drawBloomBatched(bb, base, (float) Math.cos(angle + Math.PI) * rad, -y, (float) Math.sin(angle + Math.PI) * rad, size, alpha, -t * 20.0f);
        }

        BufferRenderer.drawWithGlobalProgram(bb.end());
        endBloom(ctx);
    }

    
    private void renderGhosts(WorldRenderContext ctx, LivingEntity target, float anim, float tickDelta) {
        if (anim <= 0.001f) {
            return;
        }
        if (!BloomTextureHelper.bind(MinecraftClient.getInstance())) {
            return;
        }

        Camera camera = ctx.camera();
        Vec3d rPos = targetPos(target, tickDelta);
        Vec3d camPos = camera.getPos();
        double relX = rPos.x - camPos.x;
        double relY = rPos.y - camPos.y + target.getHeight() * 0.6f;
        double relZ = rPos.z - camPos.z;

        float radius = (float) radiusSetting.get() * 0.67f;
        float spriteSize = (float) (scaleSetting.get() * 0.18f);
        int length = MathHelper.clamp((int) countSetting.get() * 3, 15, 45);

        int baseCol = displayArgb(0f);
        int r = (baseCol >> 16) & 0xFF;
        int g = (baseCol >> 8) & 0xFF;
        int b = baseCol & 0xFF;
        float alphaMul = opacity(anim);

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        float yaw = camera.getYaw();
        float pitch = camera.getPitch();

        long timeMs = System.currentTimeMillis();
        double distance = 10.0 + (length * 0.2);
        int alphaFactor = 15;

        
        drawPath(bb, ctx.matrixStack(), camPos, relX, relY, relZ, yaw, pitch, radius, spriteSize, length, timeMs, distance, alphaFactor, r, g, b, alphaMul, 1);
        
        drawPath(bb, ctx.matrixStack(), camPos, relX, relY, relZ, yaw, pitch, radius, spriteSize, length, timeMs, distance, alphaFactor, r, g, b, alphaMul, 2);
        
        drawPath(bb, ctx.matrixStack(), camPos, relX, relY, relZ, yaw, pitch, radius, spriteSize, length, timeMs, distance, alphaFactor, r, g, b, alphaMul, 3);

        BufferRenderer.drawWithGlobalProgram(bb.end());
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void drawPath(BufferBuilder bb, net.minecraft.client.util.math.MatrixStack ms, Vec3d camPos,
                          double cx, double cy, double cz, float yaw, float pitch,
                          float radius, float spriteSize, int length, long timeMs, double distance, int alphaFactor,
                          int r, int g, int b, float alphaMul, int pathType) {
        for (int i = 0; i < length; i++) {
            double timeVal = timeMs - (i * distance);
            double angle = 0.15f * timeVal / 30.0;
            double sin = Math.sin(angle) * radius;
            double cos = Math.cos(angle) * radius;

            double ox, oy, oz;
            if (pathType == 1) {
                ox = sin; oy = cos; oz = -cos;
            } else if (pathType == 2) {
                ox = -sin; oy = sin; oz = -cos;
            } else {
                ox = -sin; oy = -sin; oz = cos;
            }

            int alphaVal = MathHelper.clamp((int) (255 * alphaMul) - (i * alphaFactor), 0, 255);
            if (alphaVal < 2) continue;

            float size = spriteSize * (1.0f - (i / (float) length) * 0.5f);

            drawGhostBillboard(bb, ms, camPos, cx + ox, cy + oy, cz + oz, size, yaw, pitch, r, g, b, alphaVal);
        }
    }

    private void drawGhostBillboard(BufferBuilder bb, net.minecraft.client.util.math.MatrixStack ms, Vec3d camPos,
                                    double wx, double wy, double wz, float half, float yaw, float pitch,
                                    int r, int g, int b, int a) {
        if (a < 2) {
            return;
        }
        ms.push();
        ms.translate(wx, wy, wz);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        Matrix4f m = ms.peek().getPositionMatrix();
        bb.vertex(m, -half, -half, 0).texture(0, 1).color(r, g, b, a);
        bb.vertex(m, half, -half, 0).texture(1, 1).color(r, g, b, a);
        bb.vertex(m, half, half, 0).texture(1, 0).color(r, g, b, a);
        bb.vertex(m, -half, half, 0).texture(0, 0).color(r, g, b, a);
        ms.pop();
    }

    private boolean beginBloom(WorldRenderContext ctx, LivingEntity entity, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d cam = ctx.camera().getPos();
        Vec3d pos = new Vec3d(
            MathHelper.lerp(tickDelta, entity.prevX, entity.getX()),
            MathHelper.lerp(tickDelta, entity.prevY, entity.getY()) + entity.getHeight() / 2f,
            MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ())
        );
        bloomCamYaw = ctx.camera().getYaw();
        bloomCamPitch = ctx.camera().getPitch();
        int c = displayArgb(0f);
        bloomR = (c >> 16) & 0xFF; bloomG = (c >> 8) & 0xFF; bloomB = c & 0xFF;
        computeBillboardBasis(bloomCamYaw, bloomCamPitch);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(770, 1);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        ctx.matrixStack().push();
        ctx.matrixStack().translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);

        if (!BloomTextureHelper.bind(mc)) {
            ctx.matrixStack().pop();
            return false;
        }
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        return true;
    }

    private void endBloom(WorldRenderContext ctx) {
        ctx.matrixStack().pop();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend(); 
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void computeBillboardBasis(float yaw, float pitch) {
        float yawRad = (float) Math.toRadians(-yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        float cosY = MathHelper.cos(yawRad);
        float sinY = MathHelper.sin(yawRad);
        float cosP = MathHelper.cos(pitchRad);
        float sinP = MathHelper.sin(pitchRad);
        billRx = cosY;
        billRy = 0f;
        billRz = sinY;
        billUx = sinY * sinP;
        billUy = cosP;
        billUz = -cosY * sinP;
    }

    
    private void drawBloomBatched(BufferBuilder bb, net.minecraft.client.util.math.MatrixStack ms, float px, float py, float pz, float size, int alpha, float rotation) {
        drawBloomBatched(bb, ms.peek().getPositionMatrix(), px, py, pz, size, alpha, rotation);
    }

    private void drawBloomBatched(BufferBuilder bb, net.minecraft.client.util.math.MatrixStack ms, float px, float py, float pz, float size, int r, int g, int b, int alpha, float rotation) {
        drawBloomBatched(bb, ms.peek().getPositionMatrix(), px, py, pz, size, r, g, b, alpha, rotation);
    }

    private void drawBloomBatched(BufferBuilder bb, Matrix4f base, float px, float py, float pz, float size, int alpha, float rotation) {
        drawBloomBatched(bb, base, px, py, pz, size, bloomR, bloomG, bloomB, alpha, rotation);
    }

    private void drawBloomBatched(BufferBuilder bb, Matrix4f base, float px, float py, float pz, float size, int r, int g, int b, int alpha, float rotation) {
        if (alpha < 2) {
            return;
        }
        float rx = billRx * size;
        float ry = billRy * size;
        float rz = billRz * size;
        float ux = billUx * size;
        float uy = billUy * size;
        float uz = billUz * size;
        if (rotation != 0f) {
            float rad = (float) Math.toRadians(rotation);
            float cr = MathHelper.cos(rad);
            float sr = MathHelper.sin(rad);
            float nrx = rx * cr + ux * sr;
            float nry = ry * cr + uy * sr;
            float nrz = rz * cr + uz * sr;
            ux = ux * cr - rx * sr;
            uy = uy * cr - ry * sr;
            uz = uz * cr - rz * sr;
            rx = nrx;
            ry = nry;
            rz = nrz;
        }
        bb.vertex(base, px - rx - ux, py - ry - uy, pz - rz - uz).texture(0, 0).color(r, g, b, alpha);
        bb.vertex(base, px - rx + ux, py - ry + uy, pz - rz + uz).texture(0, 1).color(r, g, b, alpha);
        bb.vertex(base, px + rx + ux, py + ry + uy, pz + rz + uz).texture(1, 1).color(r, g, b, alpha);
        bb.vertex(base, px + rx - ux, py + ry - uy, pz + rz - uz).texture(1, 0).color(r, g, b, alpha);
    }

    
    private void addGlow(BufferBuilder bb, Matrix4f m, float x, float y, float w, float h, int color) {
        int a = (color >> 24) & 0xFF, r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        bb.vertex(m, x, y+h, 0).texture(0, 1).color(r, g, b, a);
        bb.vertex(m, x+w, y+h, 0).texture(1, 1).color(r, g, b, a);
        bb.vertex(m, x+w, y, 0).texture(1, 0).color(r, g, b, a);
        bb.vertex(m, x, y, 0).texture(0, 0).color(r, g, b, a);
    }

    private void addVertex(BufferBuilder bb, Matrix4f m, float x, float y, float z, int color) {
        int a = (color >> 24) & 0xFF, r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        bb.vertex(m, x, y, z).color(r, g, b, a);
    }

    private void addLine(BufferBuilder bb, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, int r, int g, int b, int a) {
        bb.vertex(m, x1, y1, z1).color(r, g, b, a);
        bb.vertex(m, x2, y2, z2).color(r, g, b, a);
    }

    private int displayArgb(float hueOffset) {
        RainbowManager mgr = RainbowManager.getInstance();
        if (colorSetting.isRainbow()) {
            mgr.update(1f);
        }
        return colorSetting.resolveDisplayArgb(mgr, hueOffset);
    }

    private int multAlpha(int color, float alphaFactor) {
        int a = (color >> 24) & 0xFF;
        if (a == 0) a = 255;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int newA = Math.round(a * alphaFactor);
        newA = Math.max(0, Math.min(255, newA));
        return (newA << 24) | (r << 16) | (g << 8) | b;
    }
}
