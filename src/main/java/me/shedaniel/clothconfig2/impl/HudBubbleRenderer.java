package me.shedaniel.clothconfig2.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

import java.awt.*;


public final class HudBubbleRenderer {

    public static final int ICON_LOGO = 0;
    public static final int ICON_PLAYER = 1;
    public static final int ICON_FPS = 2;
    public static final int ICON_TIME = 3;
    public static final int ICON_COORDS = 4;
    public static final int ICON_PING = 5;
    public static final int ICON_TPS = 6;
    public static final int ICON_SPEED = 7;

    private HudBubbleRenderer() {}

    public static int chipWidth(TextRenderer tr, String text, float scale) {
        int pad = Math.round(6 * scale);
        int gap = Math.round(5 * scale);
        int icon = Math.round(14 * scale);
        int tw = tr.getWidth(text);
        return pad + icon + gap + tw + pad;
    }

    public static void drawChip(
            DrawContext ctx,
            TextRenderer tr,
            int x,
            int y,
            int icon,
            String text,
            float scale,
            int bgArgb,
            int textArgb,
            int iconArgb
    ) {
        int h = Math.round(20 * scale);
        int w = chipWidth(tr, text, scale);
        int radius = h / 2;
        Color bg = argb(bgArgb);
        Color border = new Color(255, 255, 255, 40);
        RenderHelper.drawRoundedRect(ctx, x, y, w, h, radius, bg);
        RenderHelper.drawRoundedRectOutline(ctx, x, y, w, h, radius, border);

        int pad = Math.round(6 * scale);
        int iconSize = Math.round(14 * scale);
        int iconY = y + (h - iconSize) / 2;
        int iconX = x + pad;
        Color iconBg = blend(argb(iconArgb), new Color(0, 0, 0, 140), 0.55f);
        RenderHelper.drawRoundedRect(ctx, iconX - 1, iconY - 1, iconSize + 2, iconSize + 2, (iconSize + 2) / 2, iconBg);
        drawIcon(ctx, icon, iconX, iconY, iconSize, 0xFFFFFFFF);

        int textX = x + pad + iconSize + Math.round(4 * scale);
        int textY = y + (h - tr.fontHeight) / 2 + 1;
        ctx.drawText(tr, text, textX, textY, textArgb, false);
    }

    public static void drawPanel(
            DrawContext ctx,
            int x,
            int y,
            int w,
            int h,
            float scale,
            int bgArgb
    ) {
        int radius = Math.max(6, Math.round(8 * scale));
        Color bg = argb(bgArgb);
        RenderHelper.drawDropShadow(ctx, x, y, w, h, radius);
        RenderHelper.drawRoundedRect(ctx, x, y, w, h, radius, bg);
        RenderHelper.drawRoundedRectOutline(ctx, x, y, w, h, radius, new Color(255, 255, 255, 35));
    }

    public static void drawGradientDivider(DrawContext ctx, int x, int y, int w) {
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        float mid = x + w / 2f;
        buf.vertex(mat, x, y, 0).color(1f, 1f, 1f, 0f);
        buf.vertex(mat, x, y + 1, 0).color(1f, 1f, 1f, 0f);
        buf.vertex(mat, mid, y + 1, 0).color(1f, 1f, 1f, 0.14f);
        buf.vertex(mat, mid, y, 0).color(1f, 1f, 1f, 0.14f);
        buf.vertex(mat, mid, y, 0).color(1f, 1f, 1f, 0.14f);
        buf.vertex(mat, mid, y + 1, 0).color(1f, 1f, 1f, 0.14f);
        buf.vertex(mat, x + w, y + 1, 0).color(1f, 1f, 1f, 0f);
        buf.vertex(mat, x + w, y, 0).color(1f, 1f, 1f, 0f);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    public static void drawLogo(DrawContext ctx, TextRenderer tr, int cx, int y, float scale) {
        int[] size = logoSize(scale, 72);
        drawLogo(ctx, tr, cx, y, scale, size[0], size[1], true);
    }

    
    public static int[] logoSize(float scale, int maxWidthPx) {
        int maxW = Math.max(48, Math.round(maxWidthPx * scale));
        int nativeW = HudLogoTexture.nativeWidth();
        int nativeH = HudLogoTexture.nativeHeight();
        if (nativeW > 0 && nativeH > 0) {
            int w = maxW;
            int h = Math.max(Math.round(10 * scale), Math.round(w * (nativeH / (float) nativeW)));
            return new int[]{w, h};
        }
        return new int[]{maxW, Math.max(Math.round(12 * scale), Math.round(maxW * 0.22f))};
    }

    private static final String LOGO_FALLBACK = me.shedaniel.clothconfig2.internal.BuildFingerprint.decrypt("1b0d1e181011160b1a");

    public static void drawLogo(DrawContext ctx, TextRenderer tr, int x, int y, float scale, int logoW, int logoH, boolean centered) {
        int drawX = centered ? x - logoW / 2 : x;
        Identifier id = HudLogoTexture.textureId();
        if (id != null && HudLogoTexture.isReady()) {
            drawTexturedQuad(ctx, id, drawX, y, logoW, logoH);
            return;
        }
        int tw = tr.getWidth(LOGO_FALLBACK);
        int textX = centered ? x - tw / 2 : x;
        ctx.drawText(tr, LOGO_FALLBACK, textX, y + (logoH - tr.fontHeight) / 2, 0xFF78B3FF, true);
    }

    
    private static void drawTexturedQuad(DrawContext ctx, Identifier tex, int x, int y, int w, int h) {
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, tex);
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buf.vertex(mat, x, y + h, 0).texture(0f, 1f);
        buf.vertex(mat, x + w, y + h, 0).texture(1f, 1f);
        buf.vertex(mat, x + w, y, 0).texture(1f, 0f);
        buf.vertex(mat, x, y, 0).texture(0f, 0f);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    public static void drawArrayListRow(
            DrawContext ctx,
            TextRenderer tr,
            int x,
            int y,
            int w,
            int h,
            String name,
            String detail,
            String bind,
            int accentArgb,
            int nameArgb,
            int detailArgb,
            int bindArgb,
            boolean rightAligned,
            int pad,
            int gap
    ) {
        int accentW = 2;
        int textY = y + (h - tr.fontHeight) / 2;
        int nameW = tr.getWidth(name);
        int detailW = detail == null || detail.isEmpty() ? 0 : tr.getWidth(detail);
        int bindW = bind == null || bind.isEmpty() ? 0 : tr.getWidth(bind);

        if (rightAligned) {
            int cursor = x + w - accentW - pad;
            int nameX = cursor - nameW;
            ctx.drawText(tr, name, nameX, textY, nameArgb, false);
            cursor = nameX;
            if (detailW > 0) {
                cursor -= gap;
                int detailX = cursor - detailW;
                ctx.drawText(tr, detail, detailX, textY, detailArgb, false);
                cursor = detailX;
            }
            if (bindW > 0) {
                cursor -= gap;
                ctx.drawText(tr, bind, cursor - bindW, textY, bindArgb, false);
            }
        } else {
            int cursor = x + accentW + pad;
            ctx.drawText(tr, name, cursor, textY, nameArgb, false);
            cursor += nameW;
            if (detailW > 0) {
                cursor += gap;
                ctx.drawText(tr, detail, cursor, textY, detailArgb, false);
                cursor += detailW;
            }
            if (bindW > 0) {
                cursor += gap;
                ctx.drawText(tr, bind, cursor, textY, bindArgb, false);
            }
        }
    }

    public static void drawAccentSpine(DrawContext ctx, int x, int y, int h, int accentArgb) {
        long time = System.currentTimeMillis();
        for (int i = 0; i < h; i++) {
            double wave = (double) (y + i) * 0.008 - (double) time * 0.003;
            float t = (float) (Math.sin(wave) * 0.5 + 0.5);
            
            Color color;
            if (t < 0.5f) {
                color = blend(new Color(0, 85, 255), new Color(110, 210, 255), t * 2.0f);
            } else {
                color = blend(new Color(110, 210, 255), new Color(255, 255, 255), (t - 0.5f) * 2.0f);
            }
            ctx.fill(x, y + i, x + 2, y + i + 1, color.getRGB());
        }
    }

    private static void fillRect(DrawContext ctx, int x, int y, int w, int h, Color color) {
        ctx.fill(x, y, x + w, y + h, color.getRGB());
    }

    private static int pack(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static Color blend(Color a, Color b, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        int al = (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t);
        return new Color(r, g, bl, al);
    }

    public static void drawIcon(DrawContext ctx, int type, int x, int y, int size, int argb) {
        switch (type) {
            case ICON_FPS -> drawBars(ctx, x, y, size, argb, new float[]{0.45f, 0.7f, 1f});
            case ICON_PING -> drawPing(ctx, x, y, size, argb);
            case ICON_TPS -> drawClock(ctx, x, y, size, argb, true);
            case ICON_TIME -> drawClock(ctx, x, y, size, argb, false);
            case ICON_COORDS -> drawCrosshair(ctx, x, y, size, argb);
            case ICON_SPEED -> drawArrow(ctx, x, y, size, argb);
            case ICON_PLAYER -> drawHead(ctx, x, y, size, argb);
            case ICON_LOGO -> drawDiamond(ctx, x, y, size, argb);
            default -> ctx.fill(x, y, x + size, y + size, argb);
        }
    }

    private static void drawBars(DrawContext ctx, int x, int y, int size, int argb, float[] heights) {
        int bars = heights.length;
        int gap = Math.max(2, size / 7);
        int bw = Math.max(2, (size - gap * (bars - 1)) / bars);
        for (int i = 0; i < bars; i++) {
            int bh = Math.max(3, Math.round(size * heights[i]));
            int bx = x + i * (bw + gap);
            int by = y + size - bh;
            ctx.fill(bx, by, bx + bw, by + bh, argb);
        }
    }

    private static void drawPing(DrawContext ctx, int x, int y, int size, int argb) {
        int cx = x + size / 2;
        int baseY = y + size - 2;
        ctx.fill(cx - 1, baseY - 2, cx + 2, baseY + 1, argb);
        ctx.fill(cx - 4, baseY - 4, cx - 3, baseY, argb);
        ctx.fill(cx + 3, baseY - 4, cx + 4, baseY, argb);
        ctx.fill(cx - 6, baseY - 6, cx - 5, baseY - 1, argb);
        ctx.fill(cx + 5, baseY - 6, cx + 6, baseY - 1, argb);
    }

    private static void drawClock(DrawContext ctx, int x, int y, int size, int argb, boolean ticks) {
        int cx = x + size / 2;
        int cy = y + size / 2;
        int rad = Math.max(3, Math.round(size * 0.42f));
        ctx.fill(cx - rad, cy - rad, cx + rad, cy - rad + 1, argb);
        ctx.fill(cx - rad, cy + rad - 1, cx + rad, cy + rad, argb);
        ctx.fill(cx - rad, cy - rad, cx - rad + 1, cy + rad, argb);
        ctx.fill(cx + rad - 1, cy - rad, cx + rad, cy + rad, argb);
        if (ticks) {
            ctx.fill(cx, cy - rad + 2, cx + 1, cy - rad / 2, argb);
        } else {
            ctx.fill(cx, cy - 1, cx + rad / 2, cy, argb);
            ctx.fill(cx, cy - rad + 2, cx + 1, cy, argb);
        }
    }

    private static void drawCrosshair(DrawContext ctx, int x, int y, int size, int argb) {
        int cx = x + size / 2;
        int cy = y + size / 2;
        ctx.fill(cx - 1, y + 1, cx + 1, y + size - 1, argb);
        ctx.fill(x + 1, cy - 1, x + size - 1, cy + 1, argb);
    }

    private static void drawArrow(DrawContext ctx, int x, int y, int size, int argb) {
        int mid = y + size / 2;
        ctx.fill(x + 2, mid - 1, x + size - 4, mid + 2, argb);
        ctx.fill(x + size - 5, mid - 3, x + size - 1, mid + 4, argb);
    }

    private static void drawHead(DrawContext ctx, int x, int y, int size, int argb) {
        int s = Math.max(4, size - 2);
        ctx.fill(x + 1, y + 3, x + 1 + s, y + 3 + s - 2, argb);
        ctx.fill(x + 2, y + 1, x + 2 + s - 2, y + 4, argb);
    }

    private static void drawDiamond(DrawContext ctx, int x, int y, int size, int argb) {
        int cx = x + size / 2;
        int cy = y + size / 2;
        ctx.fill(cx, y + 1, cx + 1, cy, argb);
        ctx.fill(cx, cy, x + size - 1, cy + 1, argb);
        ctx.fill(cx, cy, cx + 1, y + size - 1, argb);
        ctx.fill(x + 1, cy, cx, cy + 1, argb);
    }

    private static Color argb(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return new Color(r, g, b, a);
    }

    public static int lerpArgb(int from, int to, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        int af = (from >> 24) & 0xFF, rf = (from >> 16) & 0xFF, gf = (from >> 8) & 0xFF, bf = from & 0xFF;
        int at = (to >> 24) & 0xFF, rt = (to >> 16) & 0xFF, gt = (to >> 8) & 0xFF, bt = to & 0xFF;
        int a = (int) (af + (at - af) * t);
        int r = (int) (rf + (rt - rf) * t);
        int g = (int) (gf + (gt - gf) * t);
        int b = (int) (bf + (bt - bf) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
