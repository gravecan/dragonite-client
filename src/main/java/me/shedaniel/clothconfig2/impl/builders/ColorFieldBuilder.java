package me.shedaniel.clothconfig2.impl.builders;

import me.shedaniel.clothconfig2.impl.RainbowManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.client.font.TextRenderer;

import java.util.function.Consumer;


public class ColorFieldBuilder extends AbstractFieldBuilder {
    private int red;
    private int green;
    private int blue;
    private int alpha;
    private Consumer<Integer> onChange;
    private boolean rainbowOption;
    private boolean rainbowEnabled;

    public ColorFieldBuilder(String name, String description, int red, int green, int blue) {
        super(name, description);
        this.red = Math.max(0, Math.min(255, red));
        this.green = Math.max(0, Math.min(255, green));
        this.blue = Math.max(0, Math.min(255, blue));
        this.alpha = 255;
    }

    public ColorFieldBuilder(String name, String description, int argb) {
        super(name, description);
        setARGB(argb);
    }

    public int getRed() { return red; }
    public int getGreen() { return green; }
    public int getBlue() { return blue; }
    public int getAlpha() { return alpha; }

    public void setRed(int r) { this.red = Math.max(0, Math.min(255, r)); }
    public void setGreen(int g) { this.green = Math.max(0, Math.min(255, g)); }
    public void setBlue(int b) { this.blue = Math.max(0, Math.min(255, b)); }
    public void setAlpha(int a) { this.alpha = Math.max(0, Math.min(255, a)); }

    public int getRGB() {
        return (red << 16) | (green << 8) | blue;
    }

    public int getARGB() {
        return (alpha << 24) | getRGB();
    }

    public void setRGB(int rgb) {
        this.red = (rgb >> 16) & 0xFF;
        this.green = (rgb >> 8) & 0xFF;
        this.blue = rgb & 0xFF;
    }

    public void setARGB(int argb) {
        this.alpha = (argb >> 24) & 0xFF;
        setRGB(argb);
    }

    
    public void commitArgb(int argb) {
        setARGB(argb);
        if (onChange != null) {
            onChange.accept(getARGB());
        }
    }

    public void setOnChange(Consumer<Integer> callback) {
        this.onChange = callback;
    }

    
    public ColorFieldBuilder withRainbowOption() {
        this.rainbowOption = true;
        return this;
    }

    public boolean hasRainbowOption() {
        return rainbowOption;
    }

    public boolean isRainbow() {
        return rainbowEnabled;
    }

    public void setRainbow(boolean on) {
        this.rainbowEnabled = on;
    }

    public void toggleRainbow() {
        this.rainbowEnabled = !rainbowEnabled;
    }

    public void resolveRgb(RainbowManager mgr, float offset, double distance, float[] out) {
        if (rainbowEnabled) {
            if (distance >= 0) {
                mgr.getRainbowColorForDistanceInto(distance, out);
            } else {
                mgr.getRainbowColorInto(offset, 1.0f, 1.0f, out);
            }
            return;
        }
        out[0] = red / 255f;
        out[1] = green / 255f;
        out[2] = blue / 255f;
    }

    public int resolveDisplayArgb(RainbowManager mgr, float offset) {
        if (!rainbowEnabled) {
            return getARGB();
        }
        float[] rgb = mgr.getRainbowColor(offset);
        int r = Math.max(0, Math.min(255, (int) (rgb[0] * 255f)));
        int g = Math.max(0, Math.min(255, (int) (rgb[1] * 255f)));
        int b = Math.max(0, Math.min(255, (int) (rgb[2] * 255f)));
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    public static boolean anyRainbow(ColorFieldBuilder... fields) {
        for (ColorFieldBuilder field : fields) {
            if (field != null && field.isRainbow()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("%02X%02X%02X", red, green, blue);
    }

    
    public ClickableWidget createWidget(Screen parent, int x, int y, int width, int height) {
        return new ColorSwatchWidget(parent, x, y, width, height, this);
    }

    
    private class ColorSwatchWidget extends ClickableWidget {
        private static final int SWATCH_SIZE = 16;
        private static final int BG_COLOR     = 0xFF1A1D22;
        private static final int BORDER_COLOR = 0xFF2A2D35;
        private static final int HOVER_COLOR  = 0x20FFFFFF;
        private static final int TEXT_COLOR   = 0xFFE0E4EE;
        private static final int TEXT_DIM     = 0xFF8A8FA8;

        private final Screen parent;
        private final ColorFieldBuilder field;
        private final TextRenderer tr;
        private boolean hovered = false;

        public ColorSwatchWidget(Screen parent, int x, int y, int width, int height, ColorFieldBuilder field) {
            super(x, y, width, height, Text.literal(field.getName()));
            this.parent = parent;
            this.field = field;
            this.tr = MinecraftClient.getInstance().textRenderer;
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            hovered = mouseX >= getX() && mouseX < getX() + width
                   && mouseY >= getY() && mouseY < getY() + height;

            
            ctx.fill(getX(), getY(), getX() + width, getY() + height, BG_COLOR);
            if (hovered) ctx.fill(getX(), getY(), getX() + width, getY() + height, HOVER_COLOR);

            
            ctx.fill(getX(), getY(), getX() + width, getY() + 1, BORDER_COLOR);
            ctx.fill(getX(), getY() + height - 1, getX() + width, getY() + height, BORDER_COLOR);

            int textY = getY() + (height - tr.fontHeight) / 2;
            int x = getX() + 6;

            
            ctx.drawText(tr, Text.literal(field.getName()), x, textY, TEXT_COLOR, false);
            x += tr.getWidth(field.getName()) + 8;

            
            int sy = getY() + (height - SWATCH_SIZE) / 2;
            
            ctx.fill(x,            sy, x + SWATCH_SIZE / 2, sy + SWATCH_SIZE, 0xFF999999);
            ctx.fill(x + SWATCH_SIZE/2, sy, x + SWATCH_SIZE, sy + SWATCH_SIZE, 0xFF666666);
            ctx.fill(x, sy + SWATCH_SIZE/2, x + SWATCH_SIZE/2, sy + SWATCH_SIZE, 0xFF666666);
            ctx.fill(x + SWATCH_SIZE/2, sy + SWATCH_SIZE/2, x + SWATCH_SIZE, sy + SWATCH_SIZE, 0xFF999999);
            
            ctx.fill(x, sy, x + SWATCH_SIZE, sy + SWATCH_SIZE, field.getARGB());
            
            ctx.fill(x - 1, sy - 1, x + SWATCH_SIZE + 1, sy, BORDER_COLOR);
            ctx.fill(x - 1, sy + SWATCH_SIZE, x + SWATCH_SIZE + 1, sy + SWATCH_SIZE + 1, BORDER_COLOR);
            ctx.fill(x - 1, sy, x, sy + SWATCH_SIZE, BORDER_COLOR);
            ctx.fill(x + SWATCH_SIZE, sy, x + SWATCH_SIZE + 1, sy + SWATCH_SIZE, BORDER_COLOR);
            x += SWATCH_SIZE + 6;

            
            String hex = toString();
            ctx.drawText(tr, Text.literal("#" + hex.toUpperCase()), x, textY, TEXT_DIM, false);

            
            int btnX = getX() + width - 46 - 4;
            ctx.fill(btnX, getY() + 2, btnX + 46, getY() + height - 2, 0xFF232830);
            ctx.fill(btnX, getY() + 2, btnX + 46, getY() + 3, 0xFF3A3F50);
            ctx.drawCenteredTextWithShadow(tr, Text.literal("Edit"),
                    btnX + 23, textY, 0xFF9AAFCC);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) return false;
            if (mouseX >= getX() && mouseX < getX() + width
                    && mouseY >= getY() && mouseY < getY() + height) {
                openPicker();
                return true;
            }
            return false;
        }

        private void openPicker() {
            MinecraftClient.getInstance().setScreen(
                new me.shedaniel.clothconfig2.impl.ColorPickerScreen(parent, field.getARGB(), argb -> {
                    field.setARGB(argb);
                    if (field.onChange != null) field.onChange.accept(argb);
                })
            );
        }

        @Override
        protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
            builder.put(net.minecraft.client.gui.screen.narration.NarrationPart.TITLE, Text.literal(field.getName()));
        }
    }
}
