package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.ColorFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

public class Config_SubCategoryList extends ConfigCategoryImpl {

    private static Config_SubCategoryList INSTANCE;

    private final EnumSelectorBuilder method = new EnumSelectorBuilder(
            SecString.OBF("Method"), "", SecString.OBF("Method 1"), SecString.OBF("Method 1"), SecString.OBF("Method 2"));
    
    private final BooleanToggleBuilder showOutline = new BooleanToggleBuilder(
            SecString.OBF("Show Outline"), SecString.OBF("Show the visual boundary of the static hitbox"), true);

    private final ColorFieldBuilder outlineColor = new ColorFieldBuilder(
            SecString.OBF("Outline Color"), SecString.OBF("Custom outline color for the static hitbox"), 255, 255, 255);

    public Config_SubCategoryList() {
        super(SecString.OBF("StaticHitboxes"), SecString.OBF("Standing hitbox for elytra players"), Cat.COMBAT);
        setTooltip(SecString.OBF("Method 1: standing box at feet. Method 2: standing box centered on elytra hitbox."));
        INSTANCE = this;
        addSetting(method);
        addSetting(showOutline);
        
        outlineColor.setVisibleWhen(showOutline::get);
        addSetting(outlineColor);
    }

    public static Config_SubCategoryList getInstance() { return INSTANCE; }

    public Box getStaticBox(PlayerEntity player) {
        return getStaticBox(player, 1.0f);
    }

    private double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    public Box getStaticBox(PlayerEntity player, float tickDelta) {
        if (!isEnabled()) return null;
        if (!player.isFallFlying()) return null;

        double x = lerp(tickDelta, player.prevX, player.getX());
        double y = lerp(tickDelta, player.prevY, player.getY());
        double z = lerp(tickDelta, player.prevZ, player.getZ());
        
        double widthExpand = ((double) ((15.0f * 2.0f) / 100.0f)); 
        double heightExpand = ((double) ((90.0f * 2.0f) / 100.0f)); 
        
        Config_Selector hb = Config_Selector.getInstance();
        if (hb != null && hb.isEnabled()) {
            double wMul = 1.0 + (hb.getWidthScale() / 100.0);
            double hMul = 1.0 + (hb.getHeightScale() / 100.0);
            widthExpand = ((double) ((15.0f * 2.0f) / 100.0f)) * wMul;  
            heightExpand = ((double) ((90.0f * 2.0f) / 100.0f)) * hMul; 
        }

        if (method.get().equals(SecString.OBF("Method 1"))) {
            return new Box(x - widthExpand, y, z - widthExpand, x + widthExpand, y + heightExpand, z + widthExpand);
        } else {
            double cy = y + player.getHeight() * 0.5;
            double halfH = heightExpand / 2.0;
            return new Box(x - widthExpand, cy - halfH, z - widthExpand, x + widthExpand, cy + halfH, z + widthExpand);
        }
    }

    @Override public void onEnable() {}
    @Override public void onDisable() {}

    public void render(WorldRenderContext ctx) {
        if (!isEnabled() || !showOutline.get()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        
        float tickDelta = mc.getRenderTickCounter().getTickDelta(true);

        float r = 1.0f;
        float g = 1.0f;
        float b = 1.0f;
        float a = 1.0f;
        if (outlineColor != null) {
            r = outlineColor.getRed() / 255f;
            g = outlineColor.getGreen() / 255f;
            b = outlineColor.getBlue() / 255f;
            a = outlineColor.getAlpha() / 255f;
        }

        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity player)) continue;
            if (player == mc.player || !player.isAlive()) continue;
            Box box = getStaticBox(player, tickDelta);
            if (box != null) {
                WorldBoxRender.draw(ctx, box, r, g, b, a);
            }
        }
    }
}
