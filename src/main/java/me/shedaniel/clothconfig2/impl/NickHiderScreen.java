package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

public class NickHiderScreen extends Screen {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 340;
    private static final int PAD = 10;
    private static final int FIELD_H = 32;

    private final Config_NickHider module;
    private String fakeNick = "";
    private String prefix = "";
    private String suffix = "";
    private String rankText = "";
    private int focusedField = -1;
    private int caretBlink;

    public NickHiderScreen(Config_NickHider module) {
        super(Text.literal("Nick Hider"));
        this.module = module;
    }

    @Override
    protected void init() {
        fakeNick = module.getFakeNickValue();
        prefix = module.getPrefixValue();
        suffix = module.getSuffixValue();
        rankText = module.getRankTextValue();
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        int x = (sw - WIDTH) / 2;
        int y = (sh - HEIGHT) / 2;

        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        fillRounded(mat, x + 4, y + 4, WIDTH, HEIGHT, 8, 0x80000000);
        fillRounded(mat, x, y, WIDTH, HEIGHT, 8, 0xF0181828);
        strokeRounded(mat, x, y, WIDTH, HEIGHT, 8, 2, 0xFF3B3B4F);

        ctx.drawText(textRenderer, "§lNick Hider", x + PAD, y + 12, 0xFFFFFFFF, false);
        ctx.drawText(textRenderer, "Edit display name & rank", x + PAD, y + 26, 0xFF8888AA, false);

        int fy = y + 48;
        drawField(ctx, mat, x + PAD, fy, WIDTH - PAD * 2, "Fake Nick", fakeNick, 0, mx, my);
        fy += FIELD_H + 8;
        drawField(ctx, mat, x + PAD, fy, WIDTH - PAD * 2, "Prefix", prefix, 1, mx, my);
        fy += FIELD_H + 8;
        drawField(ctx, mat, x + PAD, fy, WIDTH - PAD * 2, "Suffix", suffix, 2, mx, my);
        fy += FIELD_H + 8;
        drawField(ctx, mat, x + PAD, fy, WIDTH - PAD * 2, "Rank Text", rankText, 3, mx, my);

        int btnY = y + HEIGHT - 42;
        int saveX = x + WIDTH - PAD - 108;
        boolean saveHov = mx >= saveX && mx <= saveX + 100 && my >= btnY && my <= btnY + 28;
        fillRounded(mat, saveX, btnY, 100, 28, 4, saveHov ? 0xFF5B8FFF : 0xFF3B3B5F);
        ctx.drawText(textRenderer, "Save", saveX + 36, btnY + 10, 0xFFFFFFFF, false);

        int cancelX = x + PAD;
        boolean cancelHov = mx >= cancelX && mx <= cancelX + 80 && my >= btnY && my <= btnY + 28;
        fillRounded(mat, cancelX, btnY, 80, 28, 4, cancelHov ? 0xFF484858 : 0xFF2A2A38);
        ctx.drawText(textRenderer, "Cancel", cancelX + 20, btnY + 10, 0xFFCCCCDD, false);

        caretBlink = (caretBlink + 1) % 12;
    }

    private void drawField(DrawContext ctx, Matrix4f mat, int x, int y, int w, String label,
                           String value, int fieldId, int mx, int my) {
        ctx.drawText(textRenderer, label, x, y - 10, 0xFF8888AA, false);
        boolean focused = focusedField == fieldId;
        boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + FIELD_H;
        int bg = focused ? 0xFF282838 : (hovered ? 0xFF222232 : 0xFF1E1E2E);
        fillRounded(mat, x, y, w, FIELD_H, 4, bg);
        strokeRounded(mat, x, y, w, FIELD_H, 4, 1, focused ? 0xFF5B8FFF : 0xFF3B3B4F);

        String display = value.isEmpty() && !focused ? "Type here..." : value;
        int col = value.isEmpty() && !focused ? 0xFF666688 : 0xFFFFFFFF;
        if (focused && caretBlink < 6) {
            display = value + "|";
        }
        ctx.drawText(textRenderer, display, x + 8, y + 11, col, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int sw = width;
        int sh = height;
        int x = (sw - WIDTH) / 2;
        int y = (sh - HEIGHT) / 2;

        int fy = y + 48;
        if (hit(mx, my, x + PAD, fy, WIDTH - PAD * 2, FIELD_H)) {
            focusedField = 0;
            return true;
        }
        fy += FIELD_H + 8;
        if (hit(mx, my, x + PAD, fy, WIDTH - PAD * 2, FIELD_H)) {
            focusedField = 1;
            return true;
        }
        fy += FIELD_H + 8;
        if (hit(mx, my, x + PAD, fy, WIDTH - PAD * 2, FIELD_H)) {
            focusedField = 2;
            return true;
        }
        fy += FIELD_H + 8;
        if (hit(mx, my, x + PAD, fy, WIDTH - PAD * 2, FIELD_H)) {
            focusedField = 3;
            return true;
        }

        int btnY = y + HEIGHT - 42;
        if (hit(mx, my, x + WIDTH - PAD - 108, btnY, 100, 28)) {
            save();
            return true;
        }
        if (hit(mx, my, x + PAD, btnY, 80, 28)) {
            close();
            return true;
        }

        focusedField = -1;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            close();
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            save();
            return true;
        }
        if (focusedField >= 0 && keyCode == 259) {
            mutateFocused(s -> s.isEmpty() ? s : s.substring(0, s.length() - 1));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (focusedField >= 0 && chr >= 32 && chr <= 126) {
            mutateFocused(s -> {
                int max = focusedField == 0 ? 24 : (focusedField == 3 ? 8 : 10);
                if (s.length() >= max) {
                    return s;
                }
                return s + chr;
            });
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private void mutateFocused(java.util.function.UnaryOperator<String> op) {
        switch (focusedField) {
            case 0 -> fakeNick = op.apply(fakeNick);
            case 1 -> prefix = op.apply(prefix);
            case 2 -> suffix = op.apply(suffix);
            case 3 -> rankText = op.apply(rankText);
            default -> { }
        }
    }

    private void save() {
        module.applyEditorValues(fakeNick, prefix, suffix, rankText);
        close();
    }

    private static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void fillRounded(Matrix4f mat, float x, float y, float w, float h, float r, int color) {
        float a = ((color >> 24) & 0xFF) / 255f;
        float r1 = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buf.vertex(mat, x + r, y, 0).color(r1, g, b, a);
        buf.vertex(mat, x + r, y + h, 0).color(r1, g, b, a);
        buf.vertex(mat, x + w - r, y + h, 0).color(r1, g, b, a);
        buf.vertex(mat, x + w - r, y, 0).color(r1, g, b, a);
        buf.vertex(mat, x, y + r, 0).color(r1, g, b, a);
        buf.vertex(mat, x, y + h - r, 0).color(r1, g, b, a);
        buf.vertex(mat, x + r, y + h - r, 0).color(r1, g, b, a);
        buf.vertex(mat, x + r, y + r, 0).color(r1, g, b, a);
        buf.vertex(mat, x + w - r, y + r, 0).color(r1, g, b, a);
        buf.vertex(mat, x + w - r, y + h - r, 0).color(r1, g, b, a);
        buf.vertex(mat, x + w, y + h - r, 0).color(r1, g, b, a);
        buf.vertex(mat, x + w, y + r, 0).color(r1, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    private void strokeRounded(Matrix4f mat, float x, float y, float w, float h, float r, float lineWidth, int color) {
        float a = ((color >> 24) & 0xFF) / 255f;
        float r1 = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        int segs = 16;
        for (int i = 0; i <= segs; i++) {
            double angle = Math.PI * 2 * i / segs;
            float px = (float) (x + w / 2 + (w / 2 - r) * Math.cos(angle));
            float py = (float) (y + h / 2 + (h / 2 - r) * Math.sin(angle));
            buf.vertex(mat, px, py, 0).color(r1, g, b, a);
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
