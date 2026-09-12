package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.ColorFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.math.impl.HitboxReachMath;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

import static net.minecraft.util.math.MathHelper.lerp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Config_Selector extends ConfigCategoryImpl {

    private static final Map<Integer, Box> expandedBoxes = new ConcurrentHashMap<>();
    private static Config_Selector INSTANCE;

    private final DoubleFieldBuilder widthScale;
    private final DoubleFieldBuilder heightScale;
    private final BooleanToggleBuilder renderHitboxes;
    private final ColorFieldBuilder outlineColor;

    public Config_Selector() {
        super(SecString.OBF("Hitbox"), SecString.OBF("Expands enemy hitboxes so your crosshair and attacks connect more easily."), Cat.COMBAT);
        setTooltip(SecString.OBF("Makes enemy hitboxes larger for easier targeting"));
        INSTANCE = this;

        widthScale = new DoubleFieldBuilder(SecString.OBF("Width"), SecString.OBF("% extra width (0=normal, 100=2x, 2000=max)"), 0.0, 0.0, 2000.0, 1.0);
        heightScale = new DoubleFieldBuilder(SecString.OBF("Height"), SecString.OBF("% extra height (0=normal, 100=2x, 1000=max)"), 0.0, 0.0, 1000.0, 1.0);
        renderHitboxes = new BooleanToggleBuilder(SecString.OBF("Show Outline"), SecString.OBF("Draw expanded hitbox outlines on screen"), true);
        outlineColor = new ColorFieldBuilder(SecString.OBF("Outline Color"), SecString.OBF("Custom outline color for standard expanded hitboxes"), 255, 255, 255);

        addSetting(widthScale);
        addSetting(heightScale);
        addSetting(renderHitboxes);
        
        outlineColor.setVisibleWhen(renderHitboxes::get);
        addSetting(outlineColor);
    }

    public static Config_Selector getInstance() {
        return INSTANCE;
    }

    @Override
    public void onDisable() {
        expandedBoxes.clear();
        me.shedaniel.math.impl.HitboxHelper.resetAllBoundingBoxes();
    }

    public double getWidthScale() {
        return widthScale.get();
    }

    public double getHeightScale() {
        return heightScale.get();
    }

    public static Box getTargetBox(int entityId) {
        Config_Selector inst = INSTANCE;
        if (inst == null || !inst.isEnabled()) {
            return expandedBoxes.get(entityId);
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            return expandedBoxes.get(entityId);
        }
        Entity entity = mc.world.getEntityById(entityId);
        if (entity instanceof LivingEntity living) {
            return inst.computeExpandedBox(living, mc.getRenderTickCounter().getTickDelta(true));
        }
        return expandedBoxes.get(entityId);
    }

    public Box computeExpandedBox(LivingEntity entity, float tickDelta) {
        if (!isEnabled() || entity == null) {
            return null;
        }
        double x = lerp(tickDelta, entity.prevX, entity.getX());
        double y = lerp(tickDelta, entity.prevY, entity.getY());
        double z = lerp(tickDelta, entity.prevZ, entity.getZ());
        return expandBox(null, entity.getWidth(), entity.getHeight(), x, y, z);
    }

    public void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            expandedBoxes.clear();
            return;
        }

        if (!isEnabled()) {
            expandedBoxes.clear();
            return;
        }

        ConfigBuilderImpl mgr = HudConfigInit.getManager();
        ListEntryImpl ab = mgr != null ? mgr.getModuleByClass(ListEntryImpl.class) : null;

        expandedBoxes.clear();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !entity.isAlive()) {
                continue;
            }
            if (!(entity instanceof LivingEntity)) {
                continue;
            }

            if (entity instanceof PlayerEntity player
                    && ab != null && ab.isEnabled() && ab.isBot(player)) {
                continue;
            }

            Box expanded = computeExpandedBox((LivingEntity) entity, 1.0f);
            if (expanded != null) {
                expandedBoxes.put(entity.getId(), expanded);
            }
        }
    }

    public void applyHitbox(PlayerEntity player) {
        if (!isEnabled()) {
            return;
        }
        Box expanded = computeExpandedBox(player, 1.0f);
        if (expanded != null) {
            expandedBoxes.put(player.getId(), expanded);
        }
    }

    public Box createModifiedBoxFrom(Box base) {
        if (!isEnabled() || base == null) {
            return null;
        }
        return expandBox(base, base.maxX - base.minX, base.maxY - base.minY);
    }

    public Box createModifiedBoxFrom(Box base, double entityWidth, double entityHeight) {
        if (!isEnabled() || base == null) {
            return null;
        }
        return expandBox(base, entityWidth, entityHeight);
    }

    public Box createModifiedBox(PlayerEntity player) {
        if (!isEnabled()) {
            return null;
        }
        return expandBox(null, player.getWidth(), player.getHeight(),
                player.getX(), player.getY(), player.getZ());
    }

    private Box expandBox(Box base, double baseW, double baseH) {
        double cx = (base.minX + base.maxX) / 2.0;
        double cy = base.minY;
        double cz = (base.minZ + base.maxZ) / 2.0;
        return expandBox(null, baseW, baseH, cx, cy, cz);
    }

    private Box expandBox(Box ignored, double baseW, double baseH,
                          double cx, double cy, double cz) {
        double wMul = 1.0 + (widthScale.get() / 100.0);
        double hMul = 1.0 + (heightScale.get() / 100.0);

        double halfW = (baseW * wMul) / 2.0;
        double newH = baseH * hMul;

        return new Box(
                cx - halfW, cy, cz - halfW,
                cx + halfW, cy + newH, cz + halfW
        );
    }

    public void render(WorldRenderContext ctx) {
        if (!isEnabled() || !renderHitboxes.get()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            return;
        }
        float r = 1.0f;
        float g = 1.0f;
        float b = 1.0f;
        float a = 0.95f;
        if (outlineColor != null) {
            r = outlineColor.getRed() / 255f;
            g = outlineColor.getGreen() / 255f;
            b = outlineColor.getBlue() / 255f;
            a = outlineColor.getAlpha() / 255f;
        }
        float tickDelta = ctx.tickCounter().getTickDelta(true);
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !entity.isAlive() || !(entity instanceof LivingEntity living)) {
                continue;
            }
            Box box = computeExpandedBox(living, tickDelta);
            if (box != null) {
                WorldBoxRender.draw(ctx, box, r, g, b, a);
            }
        }
    }
}
