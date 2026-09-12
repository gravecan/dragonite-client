package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.ColorFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;


public class Config_SkeletonEsp extends ConfigCategoryImpl {
    public static Config_SkeletonEsp INSTANCE;

    private static final float LINE_WIDTH = 1.0f;

    private final ColorFieldBuilder color;
    private final ColorFieldBuilder friendColor;
    private final DoubleFieldBuilder maxRange;
    private final BooleanToggleBuilder throughWalls;
    private final BooleanToggleBuilder disableOnElytra;

    public Config_SkeletonEsp() {
        super("Skeleton ESP", "Bone lines on players", Cat.VISUALS);
        INSTANCE = this;
        color = new ColorFieldBuilder("Color", "Skeleton color", 255, 255, 255).withRainbowOption();
        friendColor = new ColorFieldBuilder("Friend Color", "", 0, 255, 100).withRainbowOption();
        maxRange = new DoubleFieldBuilder("Max Range", "Blocks", 64, 8, 128, 1);
        throughWalls = new BooleanToggleBuilder("Through Walls", "", true);
        disableOnElytra = new BooleanToggleBuilder("Disable On Elytra", "Disable ESP when player is gliding", true);
        addSetting(color);
        addSetting(friendColor);
        addSetting(maxRange);
        addSetting(throughWalls);
        addSetting(disableOnElytra);
    }

    public float maxRangeQuery() {
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

        RainbowManager rainbowMgr = RainbowManager.getInstance();
        if (ColorFieldBuilder.anyRainbow(color, friendColor)) {
            rainbowMgr.update(1.0f);
        }

        float tickDelta = ctx.tickCounter().getTickDelta(true);
        Vec3d cam = ctx.camera().getPos();
        float maxR = (float) maxRange.get();
        MatrixStack matrices = ctx.matrixStack();
        matrices.push();

        WorldLineRender.begin(matrices, true, LINE_WIDTH);
        BufferBuilder buffer = WorldLineRender.createBuffer();
        boolean drew = false;

        for (LivingEntity living : WorldRenderEntityCache.livingEntities()) {
            if (!(living instanceof PlayerEntity player)) {
                continue;
            }
            if (!player.isAlive() || player == mc.player) {
                continue;
            }
            if (disableOnElytra.get() && player.isFallFlying()) {
                continue;
            }
            if (!throughWalls.get() && !mc.player.canSee(player)) {
                continue;
            }
            
            boolean friend = FriendManager.getInstance() != null && FriendManager.getInstance().isFriend(player);
            if (mc.player.distanceTo(player) > maxR) {
                continue;
            }

            ColorFieldBuilder field = friend ? friendColor : color;
            float[] rgb = new float[3];
            field.resolveRgb(rainbowMgr, player.getId() * 13f, -1, rgb);
            float r = rgb[0];
            float g = rgb[1];
            float b = rgb[2];
            float a = field.getAlpha() / 255f;

            Vec3d renderCam = cam;

            SkeletonEspRenderer.drawPlayer(matrices, buffer, player, renderCam, tickDelta, r, g, b, a);
            drew = true;
        }

        if (drew) {
            WorldLineRender.draw(buffer);
        }
        WorldLineRender.end(true);
        matrices.pop();
    }
}
