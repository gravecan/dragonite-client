package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class Config_VanishDetector extends ConfigCategoryImpl {

    private final DoubleFieldBuilder range;
    private final BooleanToggleBuilder esp;
    private final BooleanToggleBuilder warning;
    
    private final List<PlayerEntity> detectedAdmins = new ArrayList<>();

    public Config_VanishDetector() {
        super("VanishDetector", "Detect spectating admins", Cat.VISUALS);
        
        range = new DoubleFieldBuilder("Range", "Detection distance", 50.0, 10.0, 200.0, 5.0);
        esp = new BooleanToggleBuilder("Admin ESP", "Render hitbox of invisible admins", true);
        warning = new BooleanToggleBuilder("Warning Text", "Show blinking warning on screen", true);

        addSetting(range);
        addSetting(esp);
        addSetting(warning);
    }

    public void onFrame(MinecraftClient mc) {
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        detectedAdmins.clear();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            
            
            if (player.isSpectator() && player.distanceTo(mc.player) <= range.get()) {
                detectedAdmins.add(player);
            }
        }
    }

    public void renderHUD(DrawContext ctx, MinecraftClient mc) {
        if (!isEnabled() || !warning.get() || detectedAdmins.isEmpty()) return;

        int y = 20;
        long time = System.currentTimeMillis();
        boolean blink = (time / 500) % 2 == 0;
        
        for (PlayerEntity admin : detectedAdmins) {
            double dist = admin.distanceTo(mc.player);
            String text = "ADMIN in reach " + String.format("%.1f", dist) + " blocks is spectating you";
            int width = mc.textRenderer.getWidth(text);
            int color = blink ? Color.RED.getRGB() : Color.YELLOW.getRGB();
            
            ctx.drawText(mc.textRenderer, text, (ctx.getScaledWindowWidth() - width) / 2, y, color, true);
            y += 12;
        }
    }

    public void renderWorld(Matrix4f matrix, Camera camera) {
        if (!isEnabled() || !esp.get() || detectedAdmins.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d cameraPos = camera.getPos();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.disableDepthTest();

        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        for (PlayerEntity admin : detectedAdmins) {
            Box box = admin.getBoundingBox().offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            drawBox(bb, matrix, box, Color.RED);
        }

        BufferRenderer.drawWithGlobalProgram(bb.end());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private void drawBox(BufferBuilder bb, Matrix4f m, Box box, Color color) {
        int r = color.getRed(), g = color.getGreen(), b = color.getBlue(), a = 255;
        
        
        bb.vertex(m, (float)box.minX, (float)box.minY, (float)box.minZ).color(r, g, b, a);
        bb.vertex(m, (float)box.maxX, (float)box.minY, (float)box.minZ).color(r, g, b, a);
        bb.vertex(m, (float)box.maxX, (float)box.minY, (float)box.minZ).color(r, g, b, a);
        bb.vertex(m, (float)box.maxX, (float)box.minY, (float)box.maxZ).color(r, g, b, a);
        bb.vertex(m, (float)box.maxX, (float)box.minY, (float)box.maxZ).color(r, g, b, a);
        bb.vertex(m, (float)box.minX, (float)box.minY, (float)box.maxZ).color(r, g, b, a);
        bb.vertex(m, (float)box.minX, (float)box.minY, (float)box.maxZ).color(r, g, b, a);
        bb.vertex(m, (float)box.minX, (float)box.minY, (float)box.minZ).color(r, g, b, a);

        
        bb.vertex(m, (float)box.minX, (float)box.maxY, (float)box.minZ).color(r, g, b, a);
        bb.vertex(m, (float)box.maxX, (float)box.maxY, (float)box.minZ).color(r, g, b, a);
        bb.vertex(m, (float)box.maxX, (float)box.maxY, (float)box.minZ).color(r, g, b, a);
        bb.vertex(m, (float)box.maxX, (float)box.maxY, (float)box.maxZ).color(r, g, b, a);
        bb.vertex(m, (float)box.maxX, (float)box.maxY, (float)box.maxZ).color(r, g, b, a);
        bb.vertex(m, (float)box.minX, (float)box.maxY, (float)box.maxZ).color(r, g, b, a);
        bb.vertex(m, (float)box.minX, (float)box.maxY, (float)box.maxZ).color(r, g, b, a);
        bb.vertex(m, (float)box.minX, (float)box.maxY, (float)box.minZ).color(r, g, b, a);

        
        bb.vertex(m, (float)box.minX, (float)box.minY, (float)box.minZ).color(r, g, b, a);
        bb.vertex(m, (float)box.minX, (float)box.maxY, (float)box.minZ).color(r, g, b, a);
        bb.vertex(m, (float)box.maxX, (float)box.minY, (float)box.minZ).color(r, g, b, a);
        bb.vertex(m, (float)box.maxX, (float)box.maxY, (float)box.minZ).color(r, g, b, a);
        bb.vertex(m, (float)box.maxX, (float)box.minY, (float)box.maxZ).color(r, g, b, a);
        bb.vertex(m, (float)box.maxX, (float)box.maxY, (float)box.maxZ).color(r, g, b, a);
        bb.vertex(m, (float)box.minX, (float)box.minY, (float)box.maxZ).color(r, g, b, a);
        bb.vertex(m, (float)box.minX, (float)box.maxY, (float)box.maxZ).color(r, g, b, a);
    }
}
