package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.ColorFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.math.MathHelper;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class Config_Bubbles extends ConfigCategoryImpl {

    public static Config_Bubbles INSTANCE;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int TOP_SAFE_ZONE = 18;

    private final BooleanToggleBuilder showPlayer;
    private final BooleanToggleBuilder showFps;
    private final BooleanToggleBuilder showTime;
    private final BooleanToggleBuilder showCoords;
    private final BooleanToggleBuilder showPing;
    private final BooleanToggleBuilder showTps;
    private final BooleanToggleBuilder showSpeed;
    private final DoubleFieldBuilder scale;
    private final DoubleFieldBuilder bubbleX;
    private final DoubleFieldBuilder bubbleY;
    private final ColorFieldBuilder accent;
    private final EnumSelectorBuilder anchor;

    public Config_Bubbles() {
        super(SecString.OBF("Bubbles"), "Info pills at the top of the screen", Cat.VISUALS);
        INSTANCE = this;
        setEnabled(false);

        showPlayer = new BooleanToggleBuilder("Player", "Your name", true);
        showFps = new BooleanToggleBuilder("FPS", "", true);
        showTime = new BooleanToggleBuilder("Time", "Local clock", true);
        showCoords = new BooleanToggleBuilder("Coords", "XYZ position", true);
        showPing = new BooleanToggleBuilder("Ping", "Latency ms", true);
        showTps = new BooleanToggleBuilder("TPS", "Server tick rate", true);
        showSpeed = new BooleanToggleBuilder("Speed", "Blocks per second", false);

        scale = new DoubleFieldBuilder("Scale", "Bubble size", 1.0, 0.7, 1.6, 0.05);
        bubbleX = new DoubleFieldBuilder("X Offset", "Horizontal position", 0, -400, 400, 5);
        bubbleY = new DoubleFieldBuilder("Y Offset", "Vertical position", 35, 0, 200, 2);
        accent = new ColorFieldBuilder("Accent", "Icon tint", 120, 179, 255).withRainbowOption();
        anchor = new EnumSelectorBuilder("Anchor", "", "Right", "Center", "Left", "Right");

        addSetting(showPlayer);
        addSetting(showFps);
        addSetting(showTime);
        addSetting(showCoords);
        addSetting(showPing);
        addSetting(showTps);
        addSetting(showSpeed);
        addSetting(scale);
        addSetting(anchor);
        addSetting(bubbleX);
        addSetting(bubbleY);
        addSetting(accent);
    }

    public void renderHud(DrawContext ctx, float tickDelta) {
        if (!isEnabled()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || !VisualPreview.allowHudVisuals(mc)) {
            return;
        }

        TextRenderer tr = mc.textRenderer;
        float s = (float) scale.get();
        int bg = pack(12, 14, 22, 200);
        int text = 0xFFE8ECFF;
        RainbowManager rainbowMgr = RainbowManager.getInstance();
        if (accent.isRainbow()) {
            rainbowMgr.update(1.0f);
        }
        int icon = accent.resolveDisplayArgb(rainbowMgr, 0f);
        renderBubbleCluster(ctx, tr, mc, s, bg, text, icon);
    }

    private void renderBubbleCluster(DrawContext ctx, TextRenderer tr, MinecraftClient mc,
                                     float s, int bg, int text, int icon) {
        List<Chip> row1 = new ArrayList<>();
        List<Chip> row2 = new ArrayList<>();

        if (showPlayer.get()) {
            row1.add(new Chip(HudBubbleRenderer.ICON_PLAYER, mc.player.getName().getString()));
        }
        if (showFps.get()) {
            row1.add(new Chip(HudBubbleRenderer.ICON_FPS, mc.getCurrentFps() + " Fps"));
        }
        if (showTime.get()) {
            row1.add(new Chip(HudBubbleRenderer.ICON_TIME, LocalTime.now().format(CLOCK)));
        }
        if (showCoords.get()) {
            row2.add(new Chip(HudBubbleRenderer.ICON_COORDS, formatCoords(mc)));
        }
        if (showPing.get()) {
            row2.add(new Chip(HudBubbleRenderer.ICON_PING, pingLabel(mc) + " Ping"));
        }
        if (showTps.get()) {
            row2.add(new Chip(HudBubbleRenderer.ICON_TPS, String.format(Locale.US, "%.1f Ticks", 20.0)));
        }
        if (showSpeed.get()) {
            row2.add(new Chip(HudBubbleRenderer.ICON_SPEED, String.format(Locale.US, "%.1f Bps", horizontalBps(mc))));
        }

        if (row1.isEmpty() && row2.isEmpty()) {
            return;
        }

        int gap = Math.round(4 * s);
        int rowH = Math.round(20 * s);
        int sw = ctx.getScaledWindowWidth();
        int y = Math.max(Math.round(TOP_SAFE_ZONE * s), (int) bubbleY.get());

        int row1W = rowWidth(tr, row1, s, gap);
        int row2W = rowWidth(tr, row2, s, gap);
        int row1X = anchorX(sw, row1W, (int) bubbleX.get(), s);
        int row2X = anchorX(sw, row2W, (int) bubbleX.get(), s);

        int x = row1X;
        for (Chip chip : row1) {
            HudBubbleRenderer.drawChip(ctx, tr, x, y, chip.icon, chip.text, s, bg, text, icon);
            x += HudBubbleRenderer.chipWidth(tr, chip.text, s) + gap;
        }
        if (!row2.isEmpty()) {
            y += row1.isEmpty() ? 0 : rowH + gap;
            x = row2X;
            for (Chip chip : row2) {
                HudBubbleRenderer.drawChip(ctx, tr, x, y, chip.icon, chip.text, s, bg, text, icon);
                x += HudBubbleRenderer.chipWidth(tr, chip.text, s) + gap;
            }
        }
    }

    private static int rowWidth(TextRenderer tr, List<Chip> chips, float s, int gap) {
        int w = 0;
        for (int i = 0; i < chips.size(); i++) {
            w += HudBubbleRenderer.chipWidth(tr, chips.get(i).text, s);
            if (i < chips.size() - 1) {
                w += gap;
            }
        }
        return w;
    }

    private int anchorX(int screenW, int rowW, int offset, float s) {
        int base = switch (anchor.get()) {
            case "Left" -> Math.round(8 * s);
            case "Right" -> screenW - rowW - Math.round(8 * s);
            default -> (screenW - rowW) / 2;
        };
        return base + offset;
    }

    private static String formatCoords(MinecraftClient mc) {
        return String.format(Locale.US, "%d %d %d",
                MathHelper.floor(mc.player.getX()),
                MathHelper.floor(mc.player.getY()),
                MathHelper.floor(mc.player.getZ()));
    }

    private static int pingLabel(MinecraftClient mc) {
        if (mc.getNetworkHandler() == null || mc.player == null) {
            return 0;
        }
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return entry != null ? entry.getLatency() : 0;
    }

    private static double horizontalBps(MinecraftClient mc) {
        double vx = mc.player.getX() - mc.player.prevX;
        double vz = mc.player.getZ() - mc.player.prevZ;
        return Math.sqrt(vx * vx + vz * vz) * 20.0;
    }

    private static int pack(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private record Chip(int icon, String text) {}
}
