package me.shedaniel.clothconfig2.gui;



import com.mojang.blaze3d.systems.RenderSystem;

import me.shedaniel.clothconfig2.gui.prestige.PrestigeRenderHelper;

import me.shedaniel.clothconfig2.gui.prestige.PrestigeRenderUtil;

import me.shedaniel.clothconfig2.impl.RainbowManager;

import me.shedaniel.clothconfig2.impl.builders.ColorFieldBuilder;

import net.minecraft.client.MinecraftClient;

import net.minecraft.client.gui.DrawContext;

import net.minecraft.util.math.MathHelper;

import org.lwjgl.glfw.GLFW;



import java.awt.Color;

import java.util.HashMap;

import java.util.Map;



/** Floating color popup rendered outside the main ClickGUI panel. */

public final class PrestigeColorPicker {



    public static final int ROW_H = 15;

    public static final int BODY_H_BASE = 125;

    public static final int BODY_H = 137;

    public static final int POPUP_W = 100;

    public static final int POPUP_TITLE_H = 0;

    /** @deprecated use {@link #popupHeight(ColorFieldBuilder)} */

    @Deprecated

    public static final int POPUP_TOTAL_H = POPUP_TITLE_H + BODY_H;

    public static final int SWATCH_SIZE = 10;



    private static final int SLIDER_PAD = 4;

    private static final int COPY_Y = 122;

    private static final int RAINBOW_Y = 108;



    private static final Map<ColorFieldBuilder, State> STATES = new HashMap<>();



    private PrestigeColorPicker() {}



    public static State stateOf(ColorFieldBuilder field) {

        return STATES.computeIfAbsent(field, f -> new State(f));

    }



    public static int bodyHeight(ColorFieldBuilder field) {

        return field != null && field.hasRainbowOption() ? BODY_H : BODY_H_BASE;

    }



    public static int popupHeight(ColorFieldBuilder field) {

        return POPUP_TITLE_H + bodyHeight(field);

    }



    public static final class State {

        public final ColorFieldBuilder field;

        public boolean over;

        public boolean over2;

        public boolean over3;

        float centrals;

        float centrals2;

        float centrals3;

        float centrals4;



        State(ColorFieldBuilder field) {

            this.field = field;

            syncFromField();

        }



        Color color() {

            return new Color(field.getRed(), field.getGreen(), field.getBlue(), field.getAlpha());

        }



        public void syncFromField() {

            centrals = hue(color());

            centrals4 = alpha01(color());

            centrals2 = hue2(color());

            centrals3 = 1f - hue3(color());

        }



        void tick(float dtMs) {

            if (over || over2 || over3) {

                return;

            }

            float smooth = Math.min(1f, dtMs * 0.005f * 2f);

            centrals2 = lerp(centrals2, hue2(color()), smooth);

            centrals3 = lerp(centrals3, 1f - hue3(color()), smooth);

            centrals = lerp(centrals, hue(color()), Math.min(1f, dtMs * 0.005f));

            centrals4 = lerp(centrals4, 1f - alpha01(color()), Math.min(1f, dtMs * 0.005f));

        }

    }



    /** Row preview: flat chip, animated strip when rainbow mode is on. */

    public static void renderSquareSwatch(DrawContext ctx, int x, int y, ColorFieldBuilder field, float alpha) {

        float a = MathHelper.clamp(alpha, 0f, 1f);

        int sz = SWATCH_SIZE;

        if (field.getAlpha() < 255) {

            drawChecker(ctx, x, y, sz, sz, a);

        }

        if (field.isRainbow()) {

            RainbowManager.getInstance().update(1f);

            for (int px = 0; px < sz; px++) {

                float[] rgb = RainbowManager.getInstance().getRainbowColor(px * 14f);

                int fa = Math.round(field.getAlpha() * a);

                int argb = (fa << 24)

                        | ((int) (rgb[0] * 255) << 16)

                        | ((int) (rgb[1] * 255) << 8)

                        | (int) (rgb[2] * 255);

                ctx.fill(x + px, y, x + px + 1, y + sz, argb);

            }

            return;

        }

        int fa = Math.round(field.getAlpha() * a);

        int argb = (fa << 24) | (field.getRed() << 16) | (field.getGreen() << 8) | field.getBlue();

        ctx.fill(x, y, x + sz, y + sz, argb);

    }



    public static void renderFloatingPopup(DrawContext ctx, int px, int py, float alpha, State st,

                                           int mx, int my, float dtSec) {

        PrestigeRenderHelper.bind(ctx);

        float f = MathHelper.clamp(alpha, 0f, 1f);

        st.tick(dtSec * 1000f);



        int bodyH = bodyHeight(st.field);

        int x1 = px + POPUP_W;

        int y1 = py + POPUP_TITLE_H + bodyH;



        for (int i = 4; i >= 1; i--) {

            int sa = (int) (14 * f / i);

            ctx.fill(px - i, py + i, x1 + i, y1 + i, sa << 24);

        }



        PrestigeRenderUtil.renderColoredQuad(px, py, x1, y1,

                PrestigeRenderUtil.getColor(0, f), PrestigeRenderUtil.getColor(0, f),

                PrestigeRenderUtil.getColor(0, f), PrestigeRenderUtil.getColor(0, f));

        PrestigeRenderUtil.renderRoundedRectOutline(px, py, x1, y1, PrestigeRenderUtil.getColor(-3, f), 0);



        updateDragFromMouse(st, px, py, mx, my);

        renderPopupBody(ctx, st, px, py, POPUP_W, f, mx, my);

    }



    public static int clampPopupX(int x, int screenW) {

        int margin = 8;

        return Math.max(margin, Math.min(x, screenW - POPUP_W - margin));

    }



    public static int clampPopupY(int y, int screenH, ColorFieldBuilder field) {

        int margin = 8;

        int h = popupHeight(field);

        return Math.max(margin, Math.min(y, screenH - h - margin));

    }



    public static boolean hitPopup(int px, int py, int mx, int my, ColorFieldBuilder field) {

        int h = popupHeight(field);

        return mx >= px && mx < px + POPUP_W && my >= py && my < py + h;

    }



    public static boolean mouseClickedPopup(State st, int px, int py, int mx, int my, int button) {

        if (button != 0) {

            return hitPopup(px, py, mx, my, st.field);

        }

        if (!hitPopup(px, py, mx, my, st.field)) {

            return false;

        }

        float x = px;

        float y = py + POPUP_TITLE_H;

        float w = POPUP_W;

        if (isOverSv(st, mx, my, x, y, w)) {

            st.over = true;

        }

        if (!st.field.isRainbow() && isOverHue(mx, my, x, y, w)) {

            st.over2 = true;

        }

        if (isOverAlpha(mx, my, x, y, w)) {

            st.over3 = true;

        }

        if (st.field.hasRainbowOption() && isOverRainbow(mx, my, x, y, w)) {

            st.field.toggleRainbow();

            return true;

        }

        if (isOverCopy(mx, my, x, y, w, st.field)) {

            copy(st.field);

            return true;

        }

        if (isOverPaste(mx, my, x, y, w, st.field)) {

            paste(st.field);

            st.syncFromField();

            return true;

        }

        return true;

    }



    public static void mouseReleased(State st) {

        st.over = false;

        st.over2 = false;

        st.over3 = false;

    }



    public static void mouseReleasedAll() {

        for (State s : STATES.values()) {

            mouseReleased(s);

        }

    }



    private static void renderPopupBody(DrawContext ctx, State st, float x, float y, float w,

                                        float f, int mx, int my) {

        float innerW = w - 10f;

        boolean rainbowOn = st.field.isRainbow();



        PrestigeRenderUtil.renderRoundedRectOutline(x + 5, y + 5, x + w - 5, y + 75,

                PrestigeRenderUtil.getColor(-3, f), 0);



        if (st.over && !rainbowOn) {

            float sat = clamp((mx - (x + 5)) / innerW);

            float bri = 1f - clamp((my - (y + 5)) / 70f);

            applyHsb(st, Color.getHSBColor(hue(st.color()), sat, bri));

            st.centrals2 = sat;

            st.centrals3 = 1f - bri;

        }



        PrestigeRenderUtil.renderGradient(x + 5, y + 5, x + w - 5, y + 75,

                Color.getHSBColor(hue(st.color()), 1f, 1f), f);

        if (rainbowOn) {

            PrestigeRenderUtil.renderColoredQuad(x + 5, y + 5, x + w - 5, y + 75,

                    PrestigeRenderUtil.getColor(0, f * 0.55f), PrestigeRenderUtil.getColor(0, f * 0.55f),

                    PrestigeRenderUtil.getColor(0, f * 0.55f), PrestigeRenderUtil.getColor(0, f * 0.55f));

        }



        drawSliderHandle(x + 5 + innerW * st.centrals2, y + 5 + 70 * st.centrals3, f);



        PrestigeRenderUtil.renderRoundedRectOutline(x + 5, y + 80, x + w - 5, y + 90,

                PrestigeRenderUtil.getColor(-3, f), 0);



        float seg = innerW / 6f;

        for (int i = 0; i < 6; i++) {

            Color c0 = PrestigeRenderUtil.getColor(new Color(Color.HSBtoRGB(i / 6f, 1f, 1f)), f);

            Color c1 = PrestigeRenderUtil.getColor(new Color(Color.HSBtoRGB((i + 1) / 6f, 1f, 1f)), f);

            float sx0 = x + 5 + i * seg;

            float sx1 = x + 5 + (i + 1) * seg;

            PrestigeRenderUtil.renderColoredQuad(sx0, y + 80, sx1, y + 90, c0, c1, c0, c1);

        }



        renderCentrals(ctx, st, x, y, w, f, mx, my);



        if (st.field.hasRainbowOption()) {

            renderRainbowRow(ctx, st, x, y, w, f, mx, my);

        }

    }



    private static void drawSliderHandle(float cx, float cy, float f) {

        PrestigeRenderUtil.renderFilledCircle(cx, cy, 3f, PrestigeRenderUtil.getColor(-3, f));

        PrestigeRenderUtil.renderFilledCircle(cx, cy, 2.5f, PrestigeRenderUtil.getColor(5, f));

    }



    private static void renderCentrals(DrawContext ctx, State st, float x, float y, float w,

                                       float f, int mx, int my) {

        float innerW = w - 10f;

        float centrals = st.centrals;

        float centrals4 = st.centrals4;

        boolean rainbowOn = st.field.isRainbow();



        float hueX = x + 5 + innerW * centrals;

        drawBarHandle(hueX, y + 79, y + 91, f);



        PrestigeRenderUtil.renderRoundedRectOutline(x + 5, y + 95, x + w - 5, y + 105,

                PrestigeRenderUtil.getColor(-3, f), 0);

        Color obj = st.color();

        PrestigeRenderUtil.renderColoredQuad(x + 5, y + 95, x + w - 2, y + 105,

                PrestigeRenderUtil.getColor(obj, f), new Color(0, 0, 0, 0),

                PrestigeRenderUtil.getColor(obj, f), new Color(0, 0, 0, 0));



        float alphaX = x + 5 + innerW * centrals4;

        drawBarHandle(alphaX, y + 94, y + 106, f);



        int copyY = st.field.hasRainbowOption() ? COPY_Y : 110;

        PrestigeRenderUtil.renderColoredQuad(x + 5, y + copyY, x + w / 2 - 2.5f, y + copyY + 10,

                PrestigeRenderUtil.getColor(-1, f), PrestigeRenderUtil.getColor(-1, f),

                PrestigeRenderUtil.getColor(-1, f), PrestigeRenderUtil.getColor(-1, f));

        PrestigeRenderUtil.renderRoundedRectOutline(x + 5, y + copyY, x + w / 2 - 2.5f, y + copyY + 10,

                PrestigeRenderUtil.getColor(-3, f), 0);

        PrestigeRenderUtil.renderColoredQuad(x + w / 2 + 2.5f, y + copyY, x + w - 5, y + copyY + 10,

                PrestigeRenderUtil.getColor(-1, f), PrestigeRenderUtil.getColor(-1, f),

                PrestigeRenderUtil.getColor(-1, f), PrestigeRenderUtil.getColor(-1, f));

        PrestigeRenderUtil.renderRoundedRectOutline(x + w / 2 + 2.5f, y + copyY, x + w - 5, y + copyY + 10,

                PrestigeRenderUtil.getColor(-3, f), 0);



        MinecraftClient mc = MinecraftClient.getInstance();

        float labelA = 0.8f;

        boolean hovCopy = isOverCopy(mx, my, x, y, w, st.field);

        boolean hovPaste = isOverPaste(mx, my, x, y, w, st.field);

        ctx.drawText(mc.textRenderer, "Copy", (int) (x + 12), (int) (y + copyY + 1),

                argb(PrestigeRenderUtil.getColor(hovCopy ? 0.5f : labelA, f)), false);

        ctx.drawText(mc.textRenderer, "Paste", (int) (x + w / 2 + 10), (int) (y + copyY + 1),

                argb(PrestigeRenderUtil.getColor(hovPaste ? 0.5f : labelA, f)), false);



        if (!rainbowOn && st.over2) {

            float h = clamp((mx - (x + 5)) / innerW);

            applyHsb(st, Color.getHSBColor(h, hue2(st.color()), hue3(st.color())));

            preserveAlpha(st);

            st.centrals = h;

        }

        if (st.over3) {

            Color c = st.color();

            float a = clamp(1f - (mx - (x + 5)) / innerW);

            st.field.commitArgb(argb(new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(a * 255f))));

            st.centrals4 = 1f - a;

        }

    }



    private static void renderRainbowRow(DrawContext ctx, State st, float x, float y, float w,

                                         float f, int mx, int my) {

        float rowY = y + RAINBOW_Y;

        boolean on = st.field.isRainbow();

        boolean hov = isOverRainbow(mx, my, x, y, w);



        int bg = on ? 0xFF5865F2 : (hov ? 0xFF2A3040 : 0xFF1A1E28);

        ctx.fill((int) (x + 5), (int) rowY, (int) (x + w - 5), (int) (rowY + 10), mulA(bg, f));



        if (on) {

            RainbowManager.getInstance().update(1f);

            int ix = (int) (x + 5);

            int iw = (int) (w - 10);

            for (int i = 0; i < iw; i++) {

                float[] rgb = RainbowManager.getInstance().getRainbowColor(i * 3f);

                int col = 0xFF000000

                        | ((int) (rgb[0] * 255) << 16)

                        | ((int) (rgb[1] * 255) << 8)

                        | (int) (rgb[2] * 255);

                ctx.fill(ix + i, (int) rowY + 1, ix + i + 1, (int) rowY + 9, mulA(col, f * 0.85f));

            }

        }



        PrestigeRenderUtil.renderRoundedRectOutline(x + 5, rowY, x + w - 5, rowY + 10,

                PrestigeRenderUtil.getColor(-3, f), 0);



        MinecraftClient mc = MinecraftClient.getInstance();

        String label = on ? "Rainbow ON" : "Rainbow";

        int textCol = argb(PrestigeRenderUtil.getColor(on ? 1f : 0.75f, f));

        ctx.drawText(mc.textRenderer, label, (int) (x + 8), (int) (rowY + 1), textCol, false);

    }



    private static void drawBarHandle(float cx, float y0, float y1, float f) {

        PrestigeRenderUtil.renderColoredQuad(

                cx - 1.5f, y0,

                cx + 1.5f, y1,

                PrestigeRenderUtil.getColor(10, f), PrestigeRenderUtil.getColor(10, f),

                PrestigeRenderUtil.getColor(10, f), PrestigeRenderUtil.getColor(10, f));

        PrestigeRenderUtil.renderRoundedRectOutline(

                cx - 1.5f, y0,

                cx + 1.5f, y1,

                PrestigeRenderUtil.getColor(-5, f), 0);

    }



    private static void updateDragFromMouse(State st, float x, float y, int mx, int my) {

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null) {

            return;

        }

        boolean down = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT)

                == GLFW.GLFW_PRESS;

        if (!down) {

            return;

        }

        float w = POPUP_W;

        if (isOverSv(st, mx, my, x, y, w)) {

            st.over = true;

        } else if (!st.field.isRainbow() && isOverHue(mx, my, x, y, w)) {

            st.over2 = true;

        } else if (isOverAlpha(mx, my, x, y, w)) {

            st.over3 = true;

        }

    }



    private static boolean inBar(int mx, int my, float x, float y, float w, float h) {

        return mx >= x + 5 - SLIDER_PAD && mx <= x + w - 5 + SLIDER_PAD

                && my >= y - SLIDER_PAD && my <= y + h + SLIDER_PAD;

    }



    private static boolean isOverSv(State st, int mx, int my, float x, float y, float w) {

        if (st.field.isRainbow()) {

            return false;

        }

        return inBar(mx, my, x, y + 5, w, 70f);

    }



    private static boolean isOverHue(int mx, int my, float x, float y, float w) {

        return inBar(mx, my, x, y + 80, w, 10f);

    }



    private static boolean isOverAlpha(int mx, int my, float x, float y, float w) {

        return inBar(mx, my, x, y + 95, w, 10f);

    }



    private static boolean isOverRainbow(int mx, int my, float x, float y, float w) {

        return inBar(mx, my, x, y + RAINBOW_Y, w, 10f);

    }



    private static int copyRowY(float y, ColorFieldBuilder field) {

        return (int) y + (field != null && field.hasRainbowOption() ? COPY_Y : 110);

    }



    private static boolean isOverCopy(int mx, int my, float x, float y, float w, ColorFieldBuilder field) {

        int baseY = copyRowY(y, field);

        return mx > x + 5 && mx < x + w / 2 - 2.5f && my >= baseY - 2 && my <= baseY + 12;

    }



    private static boolean isOverPaste(int mx, int my, float x, float y, float w, ColorFieldBuilder field) {

        int baseY = copyRowY(y, field);

        return mx > x + w / 2 + 2.5f && mx < x + w - 5 && my >= baseY - 2 && my <= baseY + 12;

    }



    private static void applyHsb(State st, Color hsb) {

        int a = st.field.getAlpha();

        st.field.commitArgb((a << 24) | (hsb.getRGB() & 0xFFFFFF));

    }



    private static void preserveAlpha(State st) {

        st.field.commitArgb(argb(st.color()));

    }



    private static void copy(ColorFieldBuilder field) {

        String hex = String.format("#%06x:%d", field.getRGB() & 0xFFFFFF, field.getAlpha());

        if (field.isRainbow()) {

            hex = "rainbow:" + hex;

        }

        MinecraftClient.getInstance().keyboard.setClipboard(hex);

    }



    private static void paste(ColorFieldBuilder field) {

        try {

            String clip = MinecraftClient.getInstance().keyboard.getClipboard().trim();

            if (clip.startsWith("rainbow:")) {

                field.setRainbow(true);

                clip = clip.substring("rainbow:".length());

            } else {

                field.setRainbow(false);

            }

            if (clip.contains(":")) {

                String[] p = clip.split(":", 2);

                field.commitArgb(Color.decode(p[0]).getRGB() & 0xFFFFFF

                        | (Integer.parseInt(p[1].trim()) << 24));

            } else {

                int rgb = Color.decode(clip).getRGB() & 0xFFFFFF;

                field.commitArgb(0xFF000000 | rgb);

            }

        } catch (Exception ignored) {

        }

    }



    private static void drawChecker(DrawContext ctx, int x, int y, int w, int h, float a) {

        int cs = 2;

        int c1 = mulA(0xFF909098, a);

        int c2 = mulA(0xFF606068, a);

        for (int py = 0; py < h; py += cs) {

            for (int px = 0; px < w; px += cs) {

                boolean light = ((px / cs) + (py / cs)) % 2 == 0;

                ctx.fill(x + px, y + py, x + px + cs, y + py + cs, light ? c1 : c2);

            }

        }

    }



    private static int mulA(int argb, float a) {

        int al = (argb >> 24) & 0xFF;

        return ((int) (al * MathHelper.clamp(a, 0f, 1f)) << 24) | (argb & 0xFFFFFF);

    }



    private static float hue(Color c) {

        return Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null)[0];

    }



    private static float hue2(Color c) {

        return Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null)[1];

    }



    private static float hue3(Color c) {

        return Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null)[2];

    }



    private static float alpha01(Color c) {

        return c.getAlpha() / 255f;

    }



    private static int argb(Color c) {

        return (c.getAlpha() << 24) | (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();

    }



    private static float lerp(float a, float b, float t) {

        return a + (b - a) * MathHelper.clamp(t, 0f, 1f);

    }



    private static float clamp(float v) {

        return MathHelper.clamp(v, 0f, 1f);

    }

}


