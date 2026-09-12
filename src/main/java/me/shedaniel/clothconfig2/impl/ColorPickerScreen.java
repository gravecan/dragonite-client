package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;


public class ColorPickerScreen extends Screen {

    private static final int W = 220;
    private static final int H = 230;

    private final Screen         parent;
    private final int            initial;
    private final Consumer<Integer> callback;

    private ColorPickerWidget picker;
    private int wx, wy;

    private boolean dragging;
    private int     dragOX, dragOY;

    public ColorPickerScreen(Screen parent, int initialArgb, Consumer<Integer> onConfirm) {
        super(Text.translatable("gui.colorpicker.title"));
        this.parent   = parent;
        this.initial  = initialArgb;
        this.callback = onConfirm;
    }

    @Override
    protected void init() {
        wx = (width  - W) / 2;
        wy = (height - H) / 2;
        rebuild();
    }

    private void rebuild() {
        clearChildren();

        picker = new ColorPickerWidget(wx, wy, W, H);
        picker.setColor(initial);
        picker.onColorChange(argb -> { if (callback != null) callback.accept(argb); });
        addDrawableChild(picker);

        
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
            .dimensions(wx + W - 70, wy + H + 6, 70, 18).build());

        
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), b -> {
            if (callback != null) callback.accept(initial);
            close();
        }).dimensions(wx, wy + H + 6, 62, 18).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("gui.colorpicker.title"),
            wx + W / 2, wy - textRenderer.fontHeight - 5, 0xFFD0D8F0);
        super.render(ctx, mouseX, mouseY, delta);
    }

    
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0 && mx >= wx && mx <= wx + W && my >= wy - 18 && my < wy) {
            dragging = true; dragOX = (int) mx - wx; dragOY = (int) my - wy; return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (dragging && btn == 0) {
            wx = Math.max(0, Math.min(width  - W, (int) mx - dragOX));
            wy = Math.max(18, Math.min(height - H, (int) my - dragOY));
            rebuild(); return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override public boolean mouseReleased(double mx,double my,int btn){ dragging=false; return super.mouseReleased(mx,my,btn); }
    @Override public boolean keyPressed(int kc,int sc,int mod){ if(kc==256){close();return true;} return super.keyPressed(kc,sc,mod); }
    @Override public void close(){ if(client!=null) client.setScreen(parent); }
    @Override public boolean shouldPause(){ return false; }
}
