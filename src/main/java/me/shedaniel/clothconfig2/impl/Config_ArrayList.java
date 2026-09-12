package me.shedaniel.clothconfig2.impl;



import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;

import me.shedaniel.clothconfig2.impl.builders.ColorFieldBuilder;

import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;

import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;

import net.minecraft.client.MinecraftClient;

import net.minecraft.client.font.TextRenderer;

import net.minecraft.client.gui.DrawContext;



import java.util.ArrayList;

import java.util.List;





public class Config_ArrayList extends ConfigCategoryImpl {



    public static Config_ArrayList INSTANCE;



    private static List<ConfigCategoryImpl> cachedActiveModules = List.of();

    private static boolean moduleListDirty = true;



    private final BooleanToggleBuilder showKeybinds;

    private final BooleanToggleBuilder showMode;

    private final BooleanToggleBuilder showValue;

    private final DoubleFieldBuilder scale;

    private final DoubleFieldBuilder listY;

    private final ColorFieldBuilder accent;

    private final EnumSelectorBuilder listCorner;



    public Config_ArrayList() {

        super(SecString.OBF("Array List"), "Enabled modules waterfall", Cat.VISUALS);

        INSTANCE = this;

        setEnabled(false);



        showKeybinds = new BooleanToggleBuilder("Show Keybinds", "Compact [key] after name", true);

        showMode = new BooleanToggleBuilder("Show Mode", "Style / mode when module has one", true);

        showValue = new BooleanToggleBuilder("Show Value", "Hero number when no mode (Reach 3.1, ESP 64m)", true);

        scale = new DoubleFieldBuilder("Scale", "List size", 1.0, 0.7, 1.6, 0.05);

        listY = new DoubleFieldBuilder("Y Offset", "Vertical position", 22, 0, 2000, 2);

        accent = new ColorFieldBuilder("Accent", "Name + spine tint", 120, 179, 255).withRainbowOption();

        listCorner = new EnumSelectorBuilder("Corner", "", "Top Left", "Top Left", "Top Right");



        addSetting(showKeybinds);

        addSetting(showMode);

        addSetting(showValue);

        addSetting(scale);

        addSetting(listCorner);

        addSetting(listY);

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



        HudLogoTexture.ensure(mc);

        TextRenderer tr = mc.textRenderer;
        float s = (float) scale.get();
        boolean right = "Top Right".equals(listCorner.get());
        int cornerInset = Math.max(0, Math.round(1 * s));
        int margin = cornerInset;
        int sw = ctx.getScaledWindowWidth();
        int[] logoSize = HudBubbleRenderer.logoSize(s, 148);
        int logoW = logoSize[0];
        int logoH = logoSize[1];

        int logoY = cornerInset;
        int logoX = right ? sw - cornerInset - logoW : cornerInset;

        // Render logo watermark first so it is always shown when ArrayList is enabled
        HudBubbleRenderer.drawLogo(ctx, tr, logoX, logoY, s, logoW, logoH, false);

        List<ConfigCategoryImpl> active = collectActiveModules();
        if (active.isEmpty()) {
            return;
        }

        int rowPad = Math.max(4, Math.round(6 * s));
        int rowGap = Math.max(3, Math.round(4 * s));
        int accentW = 2;
        int rowH = tr.fontHeight + Math.round(8 * s);

        RainbowManager rainbowMgr = RainbowManager.getInstance();
        if (accent.isRainbow()) {
            rainbowMgr.update(1.0f);
        }
        int nameColor = accent.resolveDisplayArgb(rainbowMgr, 0f);
        int detailColor = 0xFFB0B8D0;
        int bindColor = 0xFF8A93AA;
        int accentArgb = nameColor;

        int moduleY = Math.max(logoY + logoH, Math.max(Math.round(18 * s), (int) listY.get()));
        int spineTop = moduleY;

        int maxContentW = Math.max(40, sw - margin * 2 - rowPad * 2 - accentW - 8);



        boolean showKeys = showKeybinds.get();

        boolean modes = showMode.get();

        boolean values = showValue.get();



        List<ModuleRow> rows = new ArrayList<>();

        for (ConfigCategoryImpl mod : active) {

            String name = mod.getName();

            String detail = HudArrayListDetails.resolveDetail(mod, modes, values);

            String bind = "";

            if (showKeys) {

                String key = HudKeybindLabels.compact(mod);

                if (!key.isEmpty()) {

                    bind = "[" + key + "]";

                }

            }

            ModuleRow row = fitRow(tr, name, detail, bind, rowGap, maxContentW, rowPad, accentW);

            rows.add(row);

        }



        rows.sort((a, b) -> {

            int byWidth = Integer.compare(b.rowW, a.rowW);

            if (byWidth != 0) {

                return byWidth;

            }

            return a.name.compareToIgnoreCase(b.name);

        });



        int spineX = right ? sw - margin - accentW : margin;
        int totalH = Math.max(1, rows.size() * rowH);

        HudBubbleRenderer.drawAccentSpine(ctx, spineX, spineTop, totalH, accentArgb);

        for (ModuleRow row : rows) {
            int rowX = right ? sw - margin - row.rowW : margin;
            long time = System.currentTimeMillis();
            // Calculate a beautiful flowing gradient factor based on Y position and time
            double wave = (double) moduleY * 0.008 - (double) time * 0.003;
            float factor = (float) (Math.sin(wave) * 0.5 + 0.5);
            
            // Clean Blue -> Light Blue -> White color changing theme
            java.awt.Color gradientColor;
            if (factor < 0.5f) {
                gradientColor = HudBubbleRenderer.blend(new java.awt.Color(0, 85, 255), new java.awt.Color(0, 195, 255), factor * 2.0f);
            } else {
                gradientColor = HudBubbleRenderer.blend(new java.awt.Color(0, 195, 255), new java.awt.Color(255, 255, 255), (factor - 0.5f) * 2.0f);
            }
            int rowAccentArgb = gradientColor.getRGB();

            HudBubbleRenderer.drawArrayListRow(
                    ctx, tr, rowX, moduleY, row.rowW, rowH,
                    row.name, row.detail, row.bind,
                    rowAccentArgb, rowAccentArgb, 0xFFB0B8D0, 0xFF8A93AA, right, rowPad, rowGap
            );
            
            moduleY += rowH + rowGap; // Increment vertical position for the next module
        }
    }



    public static void invalidateModuleListCache() {

        moduleListDirty = true;

    }



    private List<ConfigCategoryImpl> collectActiveModules() {

        if (!moduleListDirty) {

            return cachedActiveModules;

        }

        List<ConfigCategoryImpl> out = new ArrayList<>();

        ConfigBuilderImpl mgr = HudConfigInit.getManager();

        if (mgr != null) {

            for (ConfigCategoryImpl mod : mgr.getModules()) {
                if (!mod.isEnabled()) {
                    continue;
                }
                out.add(mod);
            }

        }

        cachedActiveModules = out;

        moduleListDirty = false;

        return out;

    }



    private static ModuleRow fitRow(TextRenderer tr, String name, String detail, String bind,

                                    int gap, int maxContentW, int rowPad, int accentW) {

        String d = detail == null ? "" : detail;

        String b = bind == null ? "" : bind;



        while (true) {

            int contentW = measureContent(tr, name, d, b, gap);

            if (contentW <= maxContentW) {

                return new ModuleRow(name, d, b, rowPad * 2 + accentW + contentW);

            }

            if (!b.isEmpty()) {

                b = "";

                continue;

            }

            if (!d.isEmpty()) {

                d = shorten(tr, d, Math.max(12, maxContentW - measureContent(tr, name, "", "", gap)));

                if (d.isEmpty()) {

                    continue;

                }

                continue;

            }

            name = shorten(tr, name, maxContentW);

            return new ModuleRow(name, "", "", rowPad * 2 + accentW + tr.getWidth(name));

        }

    }



    private static int measureContent(TextRenderer tr, String name, String detail, String bind, int gap) {

        int w = tr.getWidth(name);

        if (!detail.isEmpty()) {

            w += gap + tr.getWidth(detail);

        }

        if (!bind.isEmpty()) {

            w += gap + tr.getWidth(bind);

        }

        return w;

    }



    private static String shorten(TextRenderer tr, String text, int maxW) {

        if (text.isEmpty() || maxW <= 4) {

            return "";

        }

        if (tr.getWidth(text) <= maxW) {

            return text;

        }

        String ell = "…";

        while (text.length() > 2 && tr.getWidth(text + ell) > maxW) {

            text = text.substring(0, text.length() - 1);

        }

        return text + ell;

    }



    private static int pack(int r, int g, int b, int a) {

        return (a << 24) | (r << 16) | (g << 8) | b;

    }



    private record ModuleRow(String name, String detail, String bind, int rowW) {}

}

