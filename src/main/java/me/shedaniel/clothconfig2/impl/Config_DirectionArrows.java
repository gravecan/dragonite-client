package me.shedaniel.clothconfig2.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.ColorFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Config_DirectionArrows extends ConfigCategoryImpl {

    private static final Identifier ARROW_TEXTURE = Identifier.of("cloth-config2", "textures/world/vertical_arrow_separator.png");

    private final BooleanToggleBuilder showPlayers;
    private final BooleanToggleBuilder showMobs;
    private final ColorFieldBuilder playerColor;
    private final ColorFieldBuilder mobColor;
    private final DoubleFieldBuilder ringRadius;
    private final DoubleFieldBuilder arrowSize;
    private final DoubleFieldBuilder maxRange;
    private final DoubleFieldBuilder opacity;

    public Config_DirectionArrows() {
        super("DirectionArrows", "Arrows next to your crosshair that point where entities are", Cat.VISUALS);

        showPlayers = new BooleanToggleBuilder("Show Players", "Other players on the ring", true);
        showMobs = new BooleanToggleBuilder("Show Mobs", "Hostile/passive mobs on the ring", false);
        playerColor = new ColorFieldBuilder("Player Color", "", 0x5b, 0x8f, 0xff).withRainbowOption();
        mobColor = new ColorFieldBuilder("Mob Color", "", 255, 100, 100).withRainbowOption();
        ringRadius = new DoubleFieldBuilder("Ring Radius", "", 46, 40, 200, 5);
        arrowSize = new DoubleFieldBuilder("Arrow Size", "", 8, 4, 28, 1);
        maxRange = new DoubleFieldBuilder("Max Range", "", 100, 10, 500, 10);
        opacity = new DoubleFieldBuilder("Opacity", "", 200, 50, 255, 5);

        addSetting(showPlayers);
        addSetting(showMobs);
        addSetting(playerColor);
        addSetting(mobColor);
        addSetting(ringRadius);
        addSetting(arrowSize);
        addSetting(maxRange);
        addSetting(opacity);

        playerColor.setVisibleWhen(showPlayers::get);
        mobColor.setVisibleWhen(showMobs::get);
    }

    public void render(DrawContext ctx, float delta) {
        if (!isEnabled()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || !VisualPreview.allowHudVisuals(mc)) {
            return;
        }
        InjectedClientAssets.ensureClientTextures(mc);

        int cx = ctx.getScaledWindowWidth() / 2;
        int cy = ctx.getScaledWindowHeight() / 2;
        int radius = (int) ringRadius.get();

        MatrixStack ms = ctx.getMatrices();
        ms.push();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        float yaw = MathHelper.lerp(delta, mc.player.prevYaw, mc.player.getYaw());
        float maxR = (float) maxRange.get();

        double playerX = MathHelper.lerp(delta, mc.player.prevX, mc.player.getX());
        double playerZ = MathHelper.lerp(delta, mc.player.prevZ, mc.player.getZ());

        List<LivingEntity> targets = collectTargets(mc, maxR);
        targets.sort(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)));

        int size = (int) arrowSize.get();
        RainbowManager rainbowMgr = RainbowManager.getInstance();
        if (ColorFieldBuilder.anyRainbow(playerColor, mobColor)) {
            rainbowMgr.update(1.0f);
        }

        for (LivingEntity entity : targets) {
            if (!isValidTarget(mc, entity)) {
                continue;
            }

            double entityX = MathHelper.lerp(delta, entity.prevX, entity.getX());
            double entityZ = MathHelper.lerp(delta, entity.prevZ, entity.getZ());

            double dx = entityX - playerX;
            double dz = entityZ - playerZ;
            if (dx * dx + dz * dz < 0.09) {
                continue;
            }

            float relativeYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0) - yaw;
            relativeYaw = MathHelper.wrapDegrees(relativeYaw);
            float screenAngleRad = (float) Math.toRadians(relativeYaw);

            int ax = (int) (cx + radius * Math.sin(screenAngleRad));
            int ay = (int) (cy - radius * Math.cos(screenAngleRad));

            drawArrow(ctx, ax, ay, relativeYaw, size, colorFor(entity, rainbowMgr));
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        ms.pop();
    }

    private Color colorFor(LivingEntity entity, RainbowManager rainbowMgr) {
        ColorFieldBuilder field = entity instanceof PlayerEntity ? playerColor : mobColor;
        if (field.isRainbow()) {
            float[] rgb = rainbowMgr.getRainbowColor(entity.getId() * 13f);
            return new Color(rgb[0], rgb[1], rgb[2], (int) opacity.get());
        }
        return new Color(
                field.getRed(),
                field.getGreen(),
                field.getBlue(),
                (int) opacity.get());
    }

    private List<LivingEntity> collectTargets(MinecraftClient mc, float maxR) {
        List<LivingEntity> out = new ArrayList<>();
        if (showPlayers.get()) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player != mc.player && player.squaredDistanceTo(mc.player) <= (double) maxR * maxR) {
                    out.add(player);
                }
            }
        }
        if (showMobs.get()) {
            for (LivingEntity entity : mc.world.getEntitiesByClass(
                    LivingEntity.class,
                    mc.player.getBoundingBox().expand(maxR),
                    e -> e != mc.player && e instanceof MobEntity)) {
                out.add(entity);
            }
        }
        return out;
    }

    private static boolean isValidTarget(MinecraftClient mc, LivingEntity entity) {
        if (!entity.isAlive() || entity.isRemoved() || entity == mc.player) {
            return false;
        }
        return !(entity instanceof PlayerEntity player && player.isSpectator());
    }

    private void drawArrow(DrawContext ctx, int cx, int cy, float angle, int size, Color col) {
        // Keep arrows slim: 1:1 with the size setting, slight vertical squash
        // so they don't read as big puffy wedges next to the crosshair.
        float textureW = size;
        float textureH = size * 0.72f;
        float halfW = textureW / 2.0f;
        float halfH = textureH / 2.0f;

        MatrixStack matrices = ctx.getMatrices();
        matrices.push();
        matrices.translate(cx, cy, 0);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));

        int drawW = Math.max(1, Math.round(textureW));
        int drawH = Math.max(1, Math.round(textureH));
        int color = col.getRGB();

        RenderSystem.setShaderTexture(0, ARROW_TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderColor(
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f,
                ((color >> 24) & 0xFF) / 255f
        );

        ctx.drawTexture(ARROW_TEXTURE,
                Math.round(-halfW), Math.round(-halfH - 1.5f),
                0, 0, drawW, drawH, drawW, drawH);

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        matrices.pop();
    }
}
