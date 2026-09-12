package me.shedaniel.clothconfig2.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.render.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

import java.util.Locale;
import java.util.function.Consumer;


public class ColorPickerWidget extends ClickableWidget {

    public static final int PREFERRED_WIDTH = 196;
    public static final int PREFERRED_HEIGHT = 158;

    private static final int BG      = 0xF00E1016;
    private static final int BORDER  = 0xFF323848;
    private static final int ACCENT  = 0xFF5865F2;
    private static final int TEXT    = 0xFFD8DFF5;
    private static final int DIM     = 0xFF5C6478;
    private static final int FIELD   = 0xFF161A22;
    private static final int PAD = 10;
    private static final int SV_H = 84;
    private static final int BAR_H = 8;
    private static final int GAP = 6;
    private static final int SLIDER_HIT_PAD = 4;

    private float h = 0.58f, s = 0.72f, v = 0.92f;
    private int alpha = 255;

    private boolean dragSv, dragHue, dragAlpha;
    private int svX, svY, svW, svH, hueY, alphaY, barW, footerY;

    private float openAnim;
    private long lastMs = System.currentTimeMillis();

    private Consumer<Integer> onChange;
    private final TextRenderer tr;

    public ColorPickerWidget(int x, int y, int w, int h) {
        super(x, y, w, h, Text.empty());
        tr = MinecraftClient.getInstance().textRenderer;
    }

    public int getColor() {
        return pack(alpha, hsvToRgb(h, s, v));
    }

    public void onColorChange(Consumer<Integer> cb) {
        onChange = cb;
    }

    public void setColor(int argb) {
        alpha = (argb >> 24) & 0xFF;
        if (alpha == 0 && (argb & 0x00FFFFFF) != 0) {
            alpha = 255;
        }
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        float[] hsv = rgbToHsv(r, g, b);
        boolean isGrayscale = (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b))) < 2;
        if (!isGrayscale) {
            h = hsv[0];
        }
        s = hsv[1];
        v = hsv[2];
        if (s < 0.001f) {
            s = 0.01f; // Avoid getting locked on plain white so hue slider can switch color variants
        }
    }

    @Override
    public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastMs) / 1000f, 0.05f);
        lastMs = now;
        openAnim = Math.min(1f, openAnim + dt * 14f);
        float a = 1f - (float) Math.pow(1f - openAnim, 2.5);

        layout();
        int x0 = getX();
        int y0 = getY();
        int x1 = x0 + width;
        int y1 = y0 + height;

        fillRound(ctx, x0, y0, x1, y1, 7, withA(BG, a));
        strokeRound(ctx, x0, y0, x1, y1, 7, withA(BORDER, a));
        ctx.fill(x0 + 8, y0, x1 - 8, y0 + 1, withA(ACCENT, a));

        int rgb = hsvToRgb(h, s, v);
        int preview = pack(alpha, rgb);
        int px = x1 - PAD - 20;
        int py = y0 + 7;
        fillRound(ctx, px, py, px + 20, py + 20, 4, withA(preview, a));
        strokeRound(ctx, px, py, px + 20, py + 20, 4, withA(0x60FFFFFF, a));

        ctx.drawText(tr, Text.literal("Colour"), x0 + PAD, y0 + 9, withA(TEXT, a), false);

        ctx.enableScissor(x0 + 1, y0 + 1, x1 - 1, y1 - 1);
        drawChecker(ctx, svX, svY, svW, svH, a);
        drawSv(ctx, a);
        drawHue(ctx, a);
        drawAlpha(ctx, a);
        ctx.disableScissor();

        String hex = String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
        int pct = Math.round(alpha / 255f * 100f);
        ctx.fill(svX, footerY, x1 - PAD, footerY + 14, withA(FIELD, a));
        ctx.drawText(tr, Text.literal(hex), svX + 5, footerY + 3, withA(TEXT, a), false);
        String pctStr = pct + "%";
        ctx.drawText(tr, Text.literal(pctStr), x1 - PAD - 5 - tr.getWidth(pctStr), footerY + 3, withA(DIM, a), false);
    }

    private void layout() {
        int x = getX() + PAD;
        int y = getY() + 28;
        barW = width - PAD * 2;
        svX = x;
        svY = y;
        svW = barW;
        svH = SV_H;
        hueY = svY + svH + GAP;
        alphaY = hueY + BAR_H + GAP;
        footerY = alphaY + BAR_H + GAP;
    }

    private void drawSv(DrawContext ctx, float a) {
        int x1 = svX + svW;
        int y1 = svY + svH;
        int hrgb = hsvToRgb(h, 1f, 1f);
        int fa = (int) (a * 255);
        int hr = (hrgb >> 16) & 0xFF, hg = (hrgb >> 8) & 0xFF, hb = hrgb & 0xFF;
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        quad(m, svX, svY, x1, y1,
                rgba(255, 255, 255, fa), rgba(hr, hg, hb, fa),
                rgba(hr, hg, hb, fa), rgba(255, 255, 255, fa));
        quad(m, svX, svY, x1, y1,
                rgba(0, 0, 0, 0), rgba(0, 0, 0, 0),
                rgba(0, 0, 0, fa), rgba(0, 0, 0, fa));
        strokeRound(ctx, svX, svY, x1, y1, 3, withA(BORDER, a));
        int kx = svX + (int) (s * svW);
        int ky = svY + (int) ((1f - v) * svH);
        ctx.fill(kx - 3, ky - 3, kx + 4, ky + 4, withA(0xFFFFFFFF, a));
        ctx.fill(kx - 1, ky - 1, kx + 2, ky + 2, withA(ACCENT, a));
    }

    private void drawHue(DrawContext ctx, float a) {
        int y1 = hueY + BAR_H;
        int fa = (int) (a * 255);
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        ctx.enableScissor(svX, hueY, svX + barW, y1);
        int segs = 32;
        for (int i = 0; i < segs; i++) {
            float t0 = (float) i / segs;
            float t1 = (float) (i + 1) / segs;
            int c0 = opaque(hsvToRgb(t0, 1f, 1f), fa);
            int c1 = opaque(hsvToRgb(t1, 1f, 1f), fa);
            int sx0 = svX + (int) (t0 * barW);
            int sx1 = svX + (int) (t1 * barW);
            quad(m, sx0, hueY, sx1, y1, c0, c0, c1, c1);
        }
        ctx.disableScissor();
        strokeRound(ctx, svX, hueY, svX + barW, y1, 3, withA(BORDER, a));
        int hx = svX + (int) (h * barW);
        ctx.fill(hx - 1, hueY - 1, hx + 2, y1 + 1, withA(0xE8EEFF, a));
    }

    private void drawAlpha(DrawContext ctx, float a) {
        int y1 = alphaY + BAR_H;
        int fa = (int) (a * 255);
        int rgb = hsvToRgb(h, s, v);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        drawChecker(ctx, svX, alphaY, barW, BAR_H, a);
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        quad(m, svX, alphaY, svX + barW, y1,
                rgba(r, g, b, 0), rgba(r, g, b, fa),
                rgba(r, g, b, fa), rgba(r, g, b, 0));
        strokeRound(ctx, svX, alphaY, svX + barW, y1, 3, withA(BORDER, a));
        int ax = svX + (int) (alpha / 255f * barW);
        ctx.fill(ax - 1, alphaY - 1, ax + 2, y1 + 1, withA(0xE8EEFF, a));
    }

    private void drawChecker(DrawContext ctx, int x, int y, int w, int h, float a) {
        int cell = 4;
        int fa = (int) (a * 255);
        for (int cy = y; cy < y + h; cy += cell) {
            for (int cx = x; cx < x + w; cx += cell) {
                boolean light = (((cx - x) / cell) + ((cy - y) / cell)) % 2 == 0;
                ctx.fill(cx, cy, Math.min(cx + cell, x + w), Math.min(cy + cell, y + h),
                        light ? rgba(210, 210, 210, fa) : rgba(140, 140, 140, fa));
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (!visible || btn != 0) {
            return false;
        }
        layout();
        int ix = (int) mx;
        int iy = (int) my;
        if (in(ix, iy, svX, svY, svW, svH)) {
            dragSv = true;
            pickSv(ix, iy);
            return true;
        }
        if (in(ix, iy, svX, hueY - SLIDER_HIT_PAD, barW, BAR_H + SLIDER_HIT_PAD * 2)) {
            dragHue = true;
            pickHue(ix);
            return true;
        }
        if (in(ix, iy, svX, alphaY - SLIDER_HIT_PAD, barW, BAR_H + SLIDER_HIT_PAD * 2)) {
            dragAlpha = true;
            pickAlpha(ix);
            return true;
        }
        return in(ix, iy, getX(), getY(), width, height);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (dragSv) {
            pickSv((int) mx, (int) my);
        } else if (dragHue) {
            pickHue((int) mx);
        } else if (dragAlpha) {
            pickAlpha((int) mx);
        }
        return dragSv || dragHue || dragAlpha;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        dragSv = dragHue = dragAlpha = false;
        return false;
    }

    private void pickSv(int ix, int iy) {
        s = MathHelper.clamp((float) (ix - svX) / svW, 0f, 1f);
        v = 1f - MathHelper.clamp((float) (iy - svY) / svH, 0f, 1f);
        fire();
    }

    private void pickHue(int ix) {
        h = MathHelper.clamp((float) (ix - svX) / barW, 0f, 1f);
        if (s < 0.05f) {
            s = 0.9f;
        }
        fire();
    }

    private void pickAlpha(int ix) {
        alpha = (int) (MathHelper.clamp((float) (ix - svX) / barW, 0f, 1f) * 255);
        fire();
    }

    private void fire() {
        if (onChange != null) {
            onChange.accept(getColor());
        }
    }

    private static int withA(int argb, float a) {
        int al = (argb >> 24) & 0xFF;
        return ((int) (al * MathHelper.clamp(a, 0f, 1f)) << 24) | (argb & 0xFFFFFF);
    }

    private static int pack(int a, int rgb) {
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static int rgba(int r, int g, int b, int al) {
        return (al << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    private static int opaque(int rgb, int a) {
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static void quad(Matrix4f m, int x0, int y0, int x1, int y1, int tl, int tr, int bl, int br) {
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        vertex(b, m, x0, y1, bl);
        vertex(b, m, x1, y1, br);
        vertex(b, m, x1, y0, tr);
        vertex(b, m, x0, y0, tl);
        flush(b);
    }

    private static void vertex(BufferBuilder b, Matrix4f m, float x, float y, int c) {
        b.vertex(m, x, y, 0).color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF);
    }

    private static void flush(BufferBuilder b) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferRenderer.drawWithGlobalProgram(b.end());
    }

    private static void fillRound(DrawContext c, int x0, int y0, int x1, int y1, int r, int col) {
        int mr = Math.min((x1 - x0) / 2, (y1 - y0) / 2);
        r = Math.min(r, mr);
        c.fill(x0 + r, y0, x1 - r, y1, col);
        c.fill(x0, y0 + r, x0 + r, y1 - r, col);
        c.fill(x1 - r, y0 + r, x1, y1 - r, col);
        corner(c, x0 + r, y0 + r, r, 0, col);
        corner(c, x1 - r, y0 + r, r, 1, col);
        corner(c, x0 + r, y1 - r, r, 2, col);
        corner(c, x1 - r, y1 - r, r, 3, col);
    }

    private static void strokeRound(DrawContext c, int x0, int y0, int x1, int y1, int r, int col) {
        int mr = Math.min((x1 - x0) / 2, (y1 - y0) / 2);
        r = Math.min(r, mr);
        c.fill(x0 + r, y0, x1 - r, y0 + 1, col);
        c.fill(x0 + r, y1 - 1, x1 - r, y1, col);
        c.fill(x0, y0 + r, x0 + 1, y1 - r, col);
        c.fill(x1 - 1, y0 + r, x1, y1 - r, col);
    }

    private static void corner(DrawContext c, int cx, int cy, int r, int q, int col) {
        for (int i = 0; i < r; i++) {
            int x = (int) Math.sqrt(Math.max(0, (long) r * r - (long) i * i));
            switch (q) {
                case 0 -> c.fill(cx - x, cy - i, cx, cy - i + 1, col);
                case 1 -> c.fill(cx, cy - i, cx + x, cy - i + 1, col);
                case 2 -> c.fill(cx - x, cy + i, cx, cy + i + 1, col);
                default -> c.fill(cx, cy + i, cx + x, cy + i + 1, col);
            }
        }
    }

    public static int hsvToRgb(float hue, float sat, float val) {
        float hh = hue * 6f;
        int i = (int) hh;
        float f = hh - i;
        float p = val * (1f - sat);
        float q = val * (1f - sat * f);
        float t = val * (1f - sat * (1f - f));
        float rf, gf, bf;
        switch (i % 6) {
            case 0 -> { rf = val; gf = t; bf = p; }
            case 1 -> { rf = q; gf = val; bf = p; }
            case 2 -> { rf = p; gf = val; bf = t; }
            case 3 -> { rf = p; gf = q; bf = val; }
            case 4 -> { rf = t; gf = p; bf = val; }
            default -> { rf = val; gf = p; bf = q; }
        }
        return (c8(rf) << 16) | (c8(gf) << 8) | c8(bf);
    }

    public static float[] rgbToHsv(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float mx = Math.max(rf, Math.max(gf, bf));
        float mn = Math.min(rf, Math.min(gf, bf));
        float d = mx - mn;
        float hh = 0f;
        float ss = mx <= 0f ? 0f : d / mx;
        if (d > 0f) {
            if (mx == rf) {
                hh = ((gf - bf) / d) % 6f;
            } else if (mx == gf) {
                hh = (bf - rf) / d + 2f;
            } else {
                hh = (rf - gf) / d + 4f;
            }
            hh /= 6f;
            if (hh < 0f) {
                hh += 1f;
            }
        }
        return new float[]{hh, ss, mx};
    }

    private static int c8(float v) {
        return Math.max(0, Math.min(255, (int) (v * 255)));
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
    }
}
