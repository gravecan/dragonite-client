package me.shedaniel.clothconfig2.internal.secure;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class Watermark {
    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) return;
        
        String text = "Dragonite Client - Pure Java";
        context.drawTextWithShadow(client.textRenderer, text, 5, 5, 0xFFFFFF);
    }
}
