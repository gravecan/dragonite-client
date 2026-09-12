package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.internal.IntegrityProbe;
import com.mojang.blaze3d.systems.RenderSystem;
import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.ColorFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public class OverlayRenderer extends ConfigCategoryImpl {
    private final DoubleFieldBuilder xPos = new DoubleFieldBuilder("X", "X Position", 300, 0, 1000, 1);
    private final DoubleFieldBuilder yPos = new DoubleFieldBuilder("Y", "Y Position", 200, 0, 1000, 1);
    private final DoubleFieldBuilder scaleSet = new DoubleFieldBuilder("Scale", "HUD Scale", 1.0, 0.5, 2.0, 0.1);
    private final BooleanToggleBuilder editPosition = new BooleanToggleBuilder("Edit Position", "Drag the HUD while ClickGUI is open", false);
    private final DoubleFieldBuilder fadeTime = new DoubleFieldBuilder("Display Time", "Seconds to show after hit (1-10s)", 3.0, 1.0, 10.0, 0.1);
    private final BooleanToggleBuilder hitShakeEnabled = new BooleanToggleBuilder("Hit Shake", "Shake on hit", true);
    private final DoubleFieldBuilder hitShakeIntensity = new DoubleFieldBuilder("Shake Intensity", "How much it shakes", 2.0, 0.5, 5.0, 0.5);
    private final DoubleFieldBuilder bgOpacity = new DoubleFieldBuilder("Opacity", "Background transparency", 160, 50, 255, 5);
    private final DoubleFieldBuilder cornerRadius = new DoubleFieldBuilder("Rounding", "Curvature", 8, 0, 15, 1);
    private final BooleanToggleBuilder hudGhosts = new BooleanToggleBuilder("Glow Effect", "Animated glow", false);
    private final ColorFieldBuilder glowColor = new ColorFieldBuilder("Glow Color", "Accent color", 0, 200, 255).withRainbowOption();


    private float healthAnim = 0f;
    private float showAnim = 0f;
    private float appearAnim = 0f; 
    private long appearStartTime = 0;
    private LivingEntity target;
    private LivingEntity lastTarget; 
    private long lastTargetTime = 0;
    
    
    private LivingEntity callbackTarget = null;
    private long callbackTargetTime = 0;

    private float hurtShakeImpulse = 0f;
    private long hurtShakeStartMs = 0L;
    
    
    public void triggerHitShake() {
        if (!hitShakeEnabled.get()) return;
        hurtShakeImpulse = 1f;
        hurtShakeStartMs = System.currentTimeMillis();
    }
    
    
    public void setCallbackTarget(LivingEntity target, long time) {
        this.callbackTarget = target;
        this.callbackTargetTime = time;
    }

    
    private static final class GhostTrailRing {
        static final int CAPACITY = 35;
        final float[] x = new float[CAPACITY];
        final float[] y = new float[CAPACITY];
        int head;
        int count;

        void push(float px, float py) {
            head = (head - 1 + CAPACITY) % CAPACITY;
            x[head] = px;
            y[head] = py;
            if (count < CAPACITY) {
                count++;
            }
        }
    }

    private final GhostTrailRing[] ghostTrails = new GhostTrailRing[]{new GhostTrailRing(), new GhostTrailRing(), new GhostTrailRing()};
    private long lastGhostTime = 0;
    private static final long GHOST_INTERVAL_MS = 16; 

    
    private static class HUDParticle {
        float x, y, vx, vy, size, maxLife, life;
        Color color;
        HUDParticle(float x, float y, Color color) {
            this.x = x; this.y = y;
            this.vx = (float)(Math.random() - 0.5) * 120f;  
            this.vy = (float)(Math.random() - 1.0) * 120f;  
            this.size = (float)(Math.random() * 3f + 1.5f);
            this.maxLife = 15f + (float)(Math.random() * 15f);
            this.life = this.maxLife;
            this.color = color;
        }
        boolean update(float dt) {
            life -= dt * 60f;  
            if (life <= 0) return false;
            x += vx * 0.02f;
            y += vy * 0.02f;
            vy += 7f;  
            return true;
        }
        float getProgress() { return life / maxLife; }
    }
    private final List<HUDParticle> particles = new ArrayList<>();
    private int lastHurtTime = 0;

    
    private java.util.Map<java.util.UUID, Identifier> skinCache = new java.util.HashMap<>();
    private java.util.Map<java.util.UUID, Long> skinCacheTime = new java.util.HashMap<>();
    private long now = 0; 


    public OverlayRenderer() {
        super("TargetHUD", "Premium Target Info", Cat.VISUALS);
        setEnabled(false); 
        addSetting(xPos); addSetting(yPos); addSetting(scaleSet);
        addSetting(editPosition);
        addSetting(fadeTime);
        fadeTime.setSuffix("s");
        addSetting(bgOpacity); addSetting(cornerRadius);
        addSetting(hitShakeEnabled); addSetting(hitShakeIntensity);
        addSetting(hudGhosts); addSetting(glowColor);
        
        
        hitShakeIntensity.setVisibleWhen(() -> hitShakeEnabled.get());
        glowColor.setVisibleWhen(() -> hudGhosts.get());
        
        
        double[] saved = PersistenceHelper.loadTargetHudPosition();
        if (saved != null && saved.length >= 2) {
            xPos.set(saved[0]);
            yPos.set(saved[1]);
        }
    }

    public boolean isEditPositionEnabled() {
        return editPosition.get();
    }

    public double getHudX() { return xPos.get(); }

    public double getDisplayTimeSeconds() {
        return fadeTime.get();
    }
    public double getHudY() { return yPos.get(); }
    public float getHudScale() { return (float) scaleSet.get(); }

    public float getHudWidth() {
        
        float paddingX = 6f;
        float headSize = 30f;
        float gapHeadText = 6f;
        float textZoneWidth = 80f;
        return paddingX + headSize + gapHeadText + textZoneWidth + paddingX;
    }

    public float getHudHeight() {
        float paddingY = 3f;
        float headSize = 30f;
        float nicknameHeight = 11f;
        float hpTextHeight = 9f;
        float distTextHeight = 9f;
        float gapNickHp = 1f;
        float gapHpDist = 1f;
        float textZoneHeight = nicknameHeight + gapNickHp + hpTextHeight + gapHpDist + distTextHeight;
        return paddingY * 2f + Math.max(headSize, textZoneHeight);
    }

    public void setHudPosition(double x, double y) {
        xPos.set(x);
        yPos.set(y);
    }

    public void onFrame(MinecraftClient mc) {
        now = System.currentTimeMillis();

        particles.removeIf(p -> !p.update(0.016f));

        if (!isEnabled()) {
            showAnim = MathHelper.lerp(0.1f, showAnim, 0f);
            return;
        }

        LivingEntity current = findTarget(mc);
        
        if (current != null) {
            
            if (target != current) {
                lastHurtTime = 0;
                
                if (appearAnim < 0.5f) {
                    appearAnim = 0f;
                    appearStartTime = now;
                }
            }
            target = current;
            lastTarget = current;
            lastTargetTime = now;
            showAnim = MathHelper.lerp(0.15f, showAnim, 1f);
            
            
            if (appearAnim < 1f) {
                float progress = Math.min(1f, (now - appearStartTime) / 200f);
                appearAnim = 1f - (1f - progress) * (1f - progress); 
            }
        } else {
            
            long keepMs = (long)(fadeTime.get() * 1000);
            boolean shouldHold = keepMs > 0 && (now - lastTargetTime) <= keepMs;

            if (shouldHold && lastTarget != null && lastTarget.isAlive()) {
                
                target = lastTarget;
                showAnim = MathHelper.lerp(0.12f, showAnim, 1f);
            } else {
                
                target = null;
                showAnim = MathHelper.lerp(0.08f, showAnim, 0f);
                appearAnim = 0f; 
            }
        }

        if (target == null) return;

        boolean wasHurt = target.hurtTime > 0 && lastHurtTime == 0;
        boolean justHit = target.hurtTime == 10 && lastHurtTime != 10;
        if (wasHurt || justHit) {
            if (hitShakeEnabled.get()) {
                hurtShakeImpulse = 1f;
                hurtShakeStartMs = now;
            }
        }
        lastHurtTime = target.hurtTime;

        if (showAnim < 0.01f) return;

        float hp = HealthEstimate.resolveHealth(target);
        float maxHp = HealthEstimate.resolveMaxHealth(target);
        int bleed = me.shedaniel.clothconfig2.internal.SecurityVault.bleedTag();
        healthAnim = MathHelper.lerp(0.1f, healthAnim,
                MathHelper.clamp(hp / maxHp + (bleed & 0xF) * 0.00001f, 0f, 1f));
    }
    
    
    private LivingEntity findTarget(MinecraftClient mc) {
        if (mc.player == null) return null;

        if (mc.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult ehr) {
            if (ehr.getEntity() instanceof LivingEntity looked && looked.isAlive() && looked != mc.player) {
                return looked;
            }
        }

        if (callbackTarget != null && callbackTarget.isAlive() && callbackTarget != mc.player) {
            
            long keepMs = (long)(fadeTime.get() * 1000);
            if (System.currentTimeMillis() - callbackTargetTime < keepMs) {
                return callbackTarget;
            }
        }
        
        
        var hrTarget = HitRegistration.getLastTarget();
        if (hrTarget instanceof LivingEntity le && le.isAlive() && le != mc.player) {
            
            long keepMs = (long)(fadeTime.get() * 1000);
            long timeSinceHit = System.currentTimeMillis() - HitRegistration.getLastAttackTime();
            if (timeSinceHit < keepMs) {
                return le;
            }
        }
        
        
        return null;
    }

    public void render(DrawContext ctx) {
        if (!isEnabled()) return;

        boolean preview = editPosition.get();
        LivingEntity renderTarget = target;
        float renderShowAnim = showAnim;
        if (preview) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.player != null) renderTarget = mc.player;
            renderShowAnim = 1f;
        }

        if (renderShowAnim < 0.01f || renderTarget == null) return;

        LivingEntity prevTarget = this.target;
        float prevShowAnim = this.showAnim;
        try {
            if (preview) {
                this.target = renderTarget;
                this.showAnim = renderShowAnim;
            }

            MinecraftClient mc = MinecraftClient.getInstance();
            float scale = (float)scaleSet.get();
            float x = (float)xPos.get();
            float y = (float)yPos.get();

            if (!preview && hurtShakeImpulse > 0f && hitShakeEnabled.get()) {
                float t = (now - hurtShakeStartMs) / 1000f;
                float decay = (float)Math.exp(-t * 12.0);
                float amp = (float)hitShakeIntensity.get() * hurtShakeImpulse * decay;
                float phase = (now - hurtShakeStartMs) * 0.05f;
                float shakeX = (float)(Math.sin(phase) * 0.6);
                float shakeY = (float)(Math.cos(phase * 1.5) * 0.4);
                x += shakeX * amp;
                y += shakeY * amp;
                if (decay < 0.02f) hurtShakeImpulse = 0f;
            }

            MatrixStack ms = ctx.getMatrices();
            ms.push();
            ms.translate(0f, 0f, 400f);

            float localAppear = preview ? 1f : appearAnim;
            float appearScale = 0.7f + 0.3f * localAppear;
            float appearY = 20f * (1f - localAppear);
            ms.translate(x, y + appearY, 0);
            ms.scale(scale * appearScale, scale * appearScale, 1f);

            float paddingX = 6f, paddingY = 3f;
            float headSize = 30f;
            float gapHeadText = 6f;
            float nicknameHeight = 11f;
            float hpTextHeight = 9f;
            float distTextHeight = 9f;
            float gapNickHp = 1f;
            float gapHpDist = 1f;
            float textZoneHeight = nicknameHeight + gapNickHp + hpTextHeight + gapHpDist + distTextHeight;
            float textZoneWidth = 80f;

            float totalWidth = paddingX + headSize + gapHeadText + textZoneWidth + paddingX;
            float totalHeight = paddingY * 2f + Math.max(headSize, textZoneHeight);
            float rR = (float)cornerRadius.get();


            Matrix4f m = ms.peek().getPositionMatrix();
            int fullAlpha = (int)(renderShowAnim * 255f);

            drawRoundedRect(m, 0, 0, totalWidth, totalHeight, rR, new Color(25, 25, 30, (int)(bgOpacity.get() * renderShowAnim)));

            if (hudGhosts.get() && renderShowAnim > 0.05f) {
                renderHudGhosts(ctx, m, totalWidth, totalHeight, renderShowAnim);
            }

            float headX = paddingX;
            float headY = (totalHeight - headSize) / 2f;

            if (renderTarget instanceof AbstractClientPlayerEntity player) {
                java.util.UUID uuid = player.getUuid();
                Identifier skinTexture = null;

                try {
                    skinTexture = player.getSkinTextures().texture();
                } catch (Exception ignored) {
                }

                if (skinTexture != null) {
                    skinCache.put(uuid, skinTexture);
                    skinCacheTime.put(uuid, now);
                } else {
                    Long cacheTime = skinCacheTime.get(uuid);
                    if (cacheTime != null && (now - cacheTime) < 5000) {
                        skinTexture = skinCache.get(uuid);
                    }
                }

                if (skinTexture != null) {
                    RenderSystem.setShaderTexture(0, skinTexture);
                    RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();

                    float hurtP = renderTarget.hurtTime > 0 ? (renderTarget.hurtTime / 10f) : 0f;
                    int r = 255;
                    int g = (int)(255f * (1f - 0.75f * hurtP));
                    int b = (int)(255f * (1f - 0.75f * hurtP));

                    Color headColor = new Color(r, g, b, fullAlpha);
                    float u1 = 8f / 64f, v1 = 8f / 64f, u2 = 16f / 64f, v2 = 16f / 64f;
                    drawRoundedTexture(m, headX, headY, headSize, headSize, rR, u1, v1, u2, v2, headColor);
                    float u1o = 40f / 64f, v1o = 8f / 64f, u2o = 48f / 64f, v2o = 16f / 64f;
                    drawRoundedTexture(m, headX, headY, headSize, headSize, rR, u1o, v1o, u2o, v2o, headColor);
                    RenderSystem.disableBlend();
                } else {
                    drawRoundedRect(m, headX, headY, headSize, headSize, rR, new Color(80, 80, 120, fullAlpha));
                }
            } else {
                int r = 255, g = 255, b = 255;
                if (target.hurtTime > 0) {
                    g = b = (int)(255f * (1f - (target.hurtTime / 10f)));
                }
                drawRoundedRect(m, headX, headY, headSize, headSize, rR, new Color(r, g, b, fullAlpha));
            }

            float contentX = headX + headSize + gapHeadText;
            float contentY = (totalHeight - textZoneHeight) / 2f;

            String name = target.getName().getString();
            ctx.drawText(mc.textRenderer, name, (int)contentX, (int)contentY, new Color(255, 255, 255, fullAlpha).getRGB(), true);

            float hp = HealthEstimate.resolveHealth(target);
            float maxHp = HealthEstimate.resolveMaxHealth(target);
            String hpText = String.format("%.0f / %.0f", hp, maxHp);
            float hpTextY = contentY + nicknameHeight + gapNickHp;
            ctx.drawText(mc.textRenderer, hpText, (int) contentX, (int) hpTextY,
                    new Color(190, 190, 200, (int) (fullAlpha * 0.85f)).getRGB(), true);

            float dist = mc.player != null ? mc.player.distanceTo(target) : 0f;
            String distText = (int) dist + "m";
            float distTextY = hpTextY + hpTextHeight + gapHpDist;
            ctx.drawText(mc.textRenderer, distText, (int) contentX, (int) distTextY,
                    new Color(170, 170, 190, (int) (fullAlpha * 0.75f)).getRGB(), true);



            renderParticles(ctx, m);
            ms.pop();
        } finally {
            if (preview) {
                this.target = prevTarget;
                this.showAnim = prevShowAnim;
            }
        }
    }

    private void renderParticles(DrawContext ctx, Matrix4f m) {
        if (particles.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        for (HUDParticle p : particles) {
            int r = p.color.getRed(), g = p.color.getGreen(), b = p.color.getBlue();
            float progress = p.getProgress();
            int alpha = (int)(progress * progress * 255);  
            float size = p.size;
            if (alpha > 0) {
                drawColorQuad(bb, m, p.x - size/2f, p.y - size/2f, size, size, r, g, b, alpha);
            }
        }

        BufferRenderer.drawWithGlobalProgram(bb.end());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void drawColorQuad(BufferBuilder bb, Matrix4f m, float x, float y, float w, float h, int r, int g, int b, int a) {
        bb.vertex(m, x, y + h, 0).color(r, g, b, a);
        bb.vertex(m, x + w, y + h, 0).color(r, g, b, a);
        bb.vertex(m, x + w, y, 0).color(r, g, b, a);
        bb.vertex(m, x, y, 0).color(r, g, b, a);
    }

    private void renderHudGhosts(DrawContext ctx, Matrix4f m, float w, float h, float anim) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(770, 1);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.disableCull();

        long time = System.currentTimeMillis();
        RainbowManager mgr = RainbowManager.getInstance();
        if (glowColor.isRainbow()) {
            mgr.update(1.0f);
        }
        int gc = glowColor.resolveDisplayArgb(mgr, 0f);
        int r = (gc >> 16) & 0xFF, g = (gc >> 8) & 0xFF, b = gc & 0xFF;

        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        boolean drew = false;

        for (int i = 0; i < 3; i++) {
            double t = (time % 1000000) / 1000.0;
            float offsetX = (float)(Math.cos(t * 1.1 + i) * (w / 2.2f) + Math.sin(t * 0.7 - i) * 12f);
            float offsetY = (float)(Math.sin(t * 0.82 + i * 2) * (h / 2.2f) + Math.cos(t * 1.3 + i) * 10f);

            float ghostX = w / 2f + offsetX;
            float ghostY = h / 2f + offsetY;

            GhostTrailRing trail = ghostTrails[i];
            if (time - lastGhostTime >= GHOST_INTERVAL_MS) {
                trail.push(ghostX, ghostY);
            }

            for (int pIdx = 0; pIdx < trail.count; pIdx++) {
                int idx = (trail.head - pIdx + GhostTrailRing.CAPACITY) % GhostTrailRing.CAPACITY;
                float progress = (float) pIdx / trail.count;
                float pSize = 5f - progress * 3f;
                int pAlpha = (int) (anim * 60 * (1.0f - progress * progress * progress));
                if (pAlpha > 1) {
                    appendGlowCircle(bb, m, trail.x[idx], trail.y[idx], pSize, r, g, b, pAlpha);
                    drew = true;
                }
            }

            float headSize = 7f + (float)Math.sin(t * 2 + i) * 1f;
            int headAlpha = (int)(anim * 80);
            appendGlowCircle(bb, m, ghostX, ghostY, headSize, r, g, b, headAlpha);
            drew = true;
        }

        if (time - lastGhostTime >= GHOST_INTERVAL_MS) {
            lastGhostTime = time;
        }

        if (drew) {
            BufferRenderer.drawWithGlobalProgram(bb.end());
        } else {
            bb.end();
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void appendGlowCircle(BufferBuilder bb, Matrix4f m, float cx, float cy, float radius,
                                         int r, int g, int b, int alpha) {
        int segments = 12;
        int edgeAlpha = (int)(alpha * 0.15f);
        for (int seg = 0; seg < segments; seg++) {
            float a0 = (float)(seg * 2 * Math.PI / segments);
            float a1 = (float)((seg + 1) * 2 * Math.PI / segments);
            float vx0 = cx + (float)Math.cos(a0) * radius;
            float vy0 = cy + (float)Math.sin(a0) * radius;
            float vx1 = cx + (float)Math.cos(a1) * radius;
            float vy1 = cy + (float)Math.sin(a1) * radius;
            bb.vertex(m, cx, cy, 0).color(r, g, b, alpha);
            bb.vertex(m, vx0, vy0, 0).color(r, g, b, edgeAlpha);
            bb.vertex(m, vx1, vy1, 0).color(r, g, b, edgeAlpha);
        }
    }

    
    public static void drawRoundedRect(Matrix4f m, double x, double y, double width, double height, double radius, Color color) {
        float fx = (float)x, fy = (float)y, fw = (float)width, fh = (float)height, fr = (float)radius;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        int a = color.getAlpha(), r = color.getRed(), g = color.getGreen(), b = color.getBlue();

        float[][] corners = {
            {fx + fw - fr, fy + fr},
            {fx + fr, fy + fr},
            {fx + fr, fy + fh - fr},
            {fx + fw - fr, fy + fh - fr}
        };

        bb.vertex(m, fx + fw / 2f, fy + fh / 2f, 0).color(r, g, b, a);

        for (int i = 0; i < 4; i++) {
            float cx = corners[i][0], cy = corners[i][1];
            for (float angle = i * 90; angle <= (i + 1) * 90; angle += 3) {
                float rad = (float)Math.toRadians(angle);
                float vx = (float)(cx + Math.cos(rad) * fr);
                float vy = (float)(cy - Math.sin(rad) * fr);
                bb.vertex(m, vx, vy, 0).color(r, g, b, a);
            }
        }
        bb.vertex(m, (float)(corners[0][0] + Math.cos(0) * fr), (float)(corners[0][1] - Math.sin(0) * fr), 0).color(r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(bb.end());
        RenderSystem.enableCull();
    }

    public static void drawRoundedGradientRect(Matrix4f m, double x, double y, double width, double height, double radius, Color leftC, Color rightC) {
        float fx = (float)x, fy = (float)y, fw = (float)width, fh = (float)height, fr = (float)radius;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);

        float[][] corners = {
            {fx + fw - fr, fy + fr},
            {fx + fr, fy + fr},
            {fx + fr, fy + fh - fr},
            {fx + fw - fr, fy + fh - fr}
        };

        bb.vertex(m, fx + fw / 2f, fy + fh / 2f, 0).color(getGradientCol(leftC, rightC, 0.5f));

        for (int i = 0; i < 4; i++) {
            float cx = corners[i][0], cy = corners[i][1];
            for (float angle = i * 90; angle <= (i + 1) * 90; angle += 3) {
                float rad = (float)Math.toRadians(angle);
                float vx = (float)(cx + Math.cos(rad) * fr);
                float vy = (float)(cy - Math.sin(rad) * fr);
                float percent = (vx - fx) / fw;
                bb.vertex(m, vx, vy, 0).color(getGradientCol(leftC, rightC, percent));
            }
        }
        float vx = (float)(corners[0][0] + Math.cos(0) * fr);
        float vy = (float)(corners[0][1] - Math.sin(0) * fr);
        bb.vertex(m, vx, vy, 0).color(getGradientCol(leftC, rightC, (vx - fx) / fw));

        BufferRenderer.drawWithGlobalProgram(bb.end());
        RenderSystem.enableCull();
    }

    private static int getGradientCol(Color left, Color right, float percent) {
        percent = Math.max(0, Math.min(1, percent));
        int r = (int)(left.getRed() + (right.getRed() - left.getRed()) * percent);
        int g = (int)(left.getGreen() + (right.getGreen() - left.getGreen()) * percent);
        int b = (int)(left.getBlue() + (right.getBlue() - left.getBlue()) * percent);
        int a = (int)(left.getAlpha() + (right.getAlpha() - left.getAlpha()) * percent);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void drawRoundedTexture(Matrix4f m, double x, double y, double width, double height, double radius, float u1, float v1, float u2, float v2, Color color) {
        float fx = (float)x, fy = (float)y, fw = (float)width, fh = (float)height, fr = (float)radius;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);

        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_TEXTURE_COLOR);
        int a = color.getAlpha(), r = color.getRed(), g = color.getGreen(), b = color.getBlue();

        float[][] corners = {
            {fx + fw - fr, fy + fr},
            {fx + fr, fy + fr},
            {fx + fr, fy + fh - fr},
            {fx + fw - fr, fy + fh - fr}
        };

        bb.vertex(m, fx + fw / 2f, fy + fh / 2f, 0).texture(u1 + (u2 - u1) * 0.5f, v1 + (v2 - v1) * 0.5f).color(r, g, b, a);

        for (int i = 0; i < 4; i++) {
            float cx = corners[i][0], cy = corners[i][1];
            for (float angle = i * 90; angle <= (i + 1) * 90; angle += 3) {
                float rad = (float)Math.toRadians(angle);
                float vx = (float)(cx + Math.cos(rad) * fr);
                float vy = (float)(cy - Math.sin(rad) * fr);
                float u = u1 + (u2 - u1) * ((vx - fx) / fw);
                float v = v1 + (v2 - v1) * ((vy - fy) / fh);
                bb.vertex(m, vx, vy, 0).texture(u, v).color(r, g, b, a);
            }
        }
        float vx = (float)(corners[0][0] + Math.cos(0) * fr);
        float vy = (float)(corners[0][1] - Math.sin(0) * fr);
        float u = u1 + (u2 - u1) * ((vx - fx) / fw);
        float v = v1 + (v2 - v1) * ((vy - fy) / fh);
        bb.vertex(m, vx, vy, 0).texture(u, v).color(r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(bb.end());
        RenderSystem.enableCull();
    }

}
