package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;


public class ColorConfigEntry extends ClickableWidget {

    private static final int SWATCH_SIZE    = 16;
    private static final int BUTTON_WIDTH   = 46;
    private static final int BG_COLOR       = 0xFF1A1D22;
    private static final int BORDER_COLOR   = 0xFF2A2D35;
    private static final int HOVER_COLOR    = 0x20FFFFFF;
    private static final int TEXT_COLOR     = 0xFFE0E4EE;
    private static final int TEXT_DIM       = 0xFF8A8FA8;

    private final Screen           parent;
    private final Text             label;
    private       int              color;         
    private final Consumer<Integer> onChange;
    private final TextRenderer     tr;

    private boolean hovered = false;

    public ColorConfigEntry(Screen parent, int x, int y, int width, int height,
                            Text label, int initialColorArgb, Consumer<Integer> onChange) {
        super(x, y, width, height, label);
        this.parent   = parent;
        this.label    = label;
        this.color    = initialColorArgb;
        this.onChange = onChange;
        this.tr       = MinecraftClient.getInstance().textRenderer;
    }

    
    public int  getColor()            { return color; }
    public void setColor(int argb)    { this.color = argb; }

    
    @Override
    public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        hovered = mouseX >= getX() && mouseX < getX() + width
               && mouseY >= getY() && mouseY < getY() + height;

        
        ctx.fill(getX(), getY(), getX() + width, getY() + height, BG_COLOR);
        if (hovered) ctx.fill(getX(), getY(), getX() + width, getY() + height, HOVER_COLOR);

        
        ctx.fill(getX(), getY(), getX() + width, getY() + 1, BORDER_COLOR);
        ctx.fill(getX(), getY() + height - 1, getX() + width, getY() + height, BORDER_COLOR);

        int textY = getY() + (height - tr.fontHeight) / 2;
        int x     = getX() + 6;

        
        ctx.drawText(tr, label, x, textY, TEXT_COLOR, false);
        x += tr.getWidth(label) + 8;

        
        int sy = getY() + (height - SWATCH_SIZE) / 2;
        
        ctx.fill(x,            sy, x + SWATCH_SIZE / 2, sy + SWATCH_SIZE, 0xFF999999);
        ctx.fill(x + SWATCH_SIZE/2, sy, x + SWATCH_SIZE, sy + SWATCH_SIZE, 0xFF666666);
        ctx.fill(x, sy + SWATCH_SIZE/2, x + SWATCH_SIZE/2, sy + SWATCH_SIZE, 0xFF666666);
        ctx.fill(x + SWATCH_SIZE/2, sy + SWATCH_SIZE/2, x + SWATCH_SIZE, sy + SWATCH_SIZE, 0xFF999999);
        
        ctx.fill(x, sy, x + SWATCH_SIZE, sy + SWATCH_SIZE, color);
        
        ctx.fill(x - 1, sy - 1, x + SWATCH_SIZE + 1, sy, BORDER_COLOR);
        ctx.fill(x - 1, sy + SWATCH_SIZE, x + SWATCH_SIZE + 1, sy + SWATCH_SIZE + 1, BORDER_COLOR);
        ctx.fill(x - 1, sy, x, sy + SWATCH_SIZE, BORDER_COLOR);
        ctx.fill(x + SWATCH_SIZE, sy, x + SWATCH_SIZE + 1, sy + SWATCH_SIZE, BORDER_COLOR);
        x += SWATCH_SIZE + 6;

        
        String hex = colorToHex(color);
        ctx.drawText(tr, Text.literal("#" + hex.toUpperCase()), x, textY, TEXT_DIM, false);

        
        int btnX = getX() + width - BUTTON_WIDTH - 4;
        ctx.fill(btnX, getY() + 2, btnX + BUTTON_WIDTH, getY() + height - 2, 0xFF232830);
        ctx.fill(btnX, getY() + 2, btnX + BUTTON_WIDTH, getY() + 3, 0xFF3A3F50);
        ctx.drawCenteredTextWithShadow(tr, Text.literal("Edit"),
                btnX + BUTTON_WIDTH / 2, textY, 0xFF9AAFCC);
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
            new ColorPickerScreen(parent, color, argb -> {
                this.color = argb;
                if (onChange != null) onChange.accept(argb);
            })
        );
    }

    
    private static String colorToHex(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8)  & 0xFF;
        int b =  argb        & 0xFF;
        if (a == 255) return String.format("%02x%02x%02x", r, g, b);
        return String.format("%02x%02x%02x%02x", a, r, g, b);
    }

    @Override
    protected void appendClickableNarrations(
            net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
        builder.put(net.minecraft.client.gui.screen.narration.NarrationPart.TITLE, label);
    }
}
