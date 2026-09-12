package me.shedaniel.clothconfig2.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

import java.awt.*;


public class RenderHelper {

    

    public static void drawRoundedRect(DrawContext ctx, int x, int y, int w, int h,
                                        int radius, Color color) {
        if (radius <= 0) { ctx.fill(x, y, x + w, y + h, color.getRGB()); return; }
        radius = Math.min(radius, Math.min(w, h) / 2);
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // These draws go straight to the GPU while the world depth buffer is
        // still bound, so near geometry would punch through the UI.
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        float cr = color.getRed()/255f, cg = color.getGreen()/255f,
              cb = color.getBlue()/255f, ca = color.getAlpha()/255f;
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        int segs = 14;
        buf.vertex(mat, x + w/2f, y + h/2f, 0).color(cr, cg, cb, ca);
        addCorner(buf, mat, x+radius,   y+radius,   radius, (float)Math.PI,       segs, cr,cg,cb,ca);
        addCorner(buf, mat, x+w-radius, y+radius,   radius, -(float)(Math.PI/2),  segs, cr,cg,cb,ca);
        addCorner(buf, mat, x+w-radius, y+h-radius, radius, 0,                    segs, cr,cg,cb,ca);
        addCorner(buf, mat, x+radius,   y+h-radius, radius, (float)(Math.PI/2),   segs, cr,cg,cb,ca);
        float a0 = (float)Math.PI + (float)(Math.PI/2.0/segs);
        buf.vertex(mat, x+radius+(float)(radius*Math.cos(a0)),
                        y+radius+(float)(radius*Math.sin(a0)), 0).color(cr,cg,cb,ca);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    

    public static void drawGradientRoundedRect(DrawContext ctx, int x, int y, int w, int h,
                                                int radius, Color top, Color bot) {
        if (radius <= 0) {
            drawGradientRect(ctx, x, y, w, h, top, bot);
            return;
        }
        radius = Math.min(radius, Math.min(w, h) / 2);
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // These draws go straight to the GPU while the world depth buffer is
        // still bound, so near geometry would punch through the UI.
        RenderSystem.disableDepthTest();

        
        Color mid = lerp(top, bot, 0.5f);
        float mr = mid.getRed()/255f, mg = mid.getGreen()/255f,
              mb = mid.getBlue()/255f, ma = mid.getAlpha()/255f;

        
        int segs = 14;
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        buf.vertex(mat, x+w/2f, y+h/2f, 0).color(mr,mg,mb,ma);
        gradCorner(buf, mat, x+radius,   y+radius,   radius, (float)Math.PI,      segs, x,y,w,h,top,bot);
        gradCorner(buf, mat, x+w-radius, y+radius,   radius, -(float)(Math.PI/2), segs, x,y,w,h,top,bot);
        gradCorner(buf, mat, x+w-radius, y+h-radius, radius, 0,                   segs, x,y,w,h,top,bot);
        gradCorner(buf, mat, x+radius,   y+h-radius, radius, (float)(Math.PI/2),  segs, x,y,w,h,top,bot);
        float a0 = (float)Math.PI + (float)(Math.PI/2.0/segs);
        float vx = x+radius+(float)(radius*Math.cos(a0));
        float vy = y+radius+(float)(radius*Math.sin(a0));
        Color vc = lerp(top, bot, MathHelper.clamp((vy-y)/(float)h, 0,1));
        buf.vertex(mat, vx, vy, 0).color(vc.getRed()/255f,vc.getGreen()/255f,vc.getBlue()/255f,vc.getAlpha()/255f);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void gradCorner(BufferBuilder buf, Matrix4f mat,
                                    float cx, float cy, int r, float startAngle, int segs,
                                    int bx, int by, int bw, int bh, Color top, Color bot) {
        float sweep = (float)(Math.PI/2.0);
        for (int i = 0; i <= segs; i++) {
            float a = startAngle + sweep*i/segs;
            float vx = cx + r*(float)Math.cos(a);
            float vy = cy + r*(float)Math.sin(a);
            float t = MathHelper.clamp((vy-by)/(float)bh, 0,1);
            Color c = lerp(top, bot, t);
            buf.vertex(mat, vx, vy, 0).color(c.getRed()/255f,c.getGreen()/255f,c.getBlue()/255f,c.getAlpha()/255f);
        }
    }

    

    public static void drawGradientRect(DrawContext ctx, int x, int y, int w, int h,
                                         Color top, Color bot) {
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // These draws go straight to the GPU while the world depth buffer is
        // still bound, so near geometry would punch through the UI.
        RenderSystem.disableDepthTest();
        float tr=top.getRed()/255f, tg=top.getGreen()/255f, tb=top.getBlue()/255f, ta=top.getAlpha()/255f;
        float br=bot.getRed()/255f, bg=bot.getGreen()/255f, bb=bot.getBlue()/255f, ba=bot.getAlpha()/255f;
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buf.vertex(mat, x,   y,   0).color(tr,tg,tb,ta);
        buf.vertex(mat, x,   y+h, 0).color(br,bg,bb,ba);
        buf.vertex(mat, x+w, y+h, 0).color(br,bg,bb,ba);
        buf.vertex(mat, x+w, y,   0).color(tr,tg,tb,ta);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    

    public static void drawGlowRect(DrawContext ctx, int x, int y, int w, int h,
                                     int radius, Color glowColor) {
        int gr = glowColor.getRed(), gg = glowColor.getGreen(), gb = glowColor.getBlue();
        
        drawRoundedRect(ctx, x-6, y-6, w+12, h+12, radius+6, new Color(gr,gg,gb, 18));
        drawRoundedRect(ctx, x-3, y-3, w+6,  h+6,  radius+3, new Color(gr,gg,gb, 30));
        drawRoundedRect(ctx, x-1, y-1, w+2,  h+2,  radius+1, new Color(gr,gg,gb, 45));
    }

    

    public static void drawDropShadow(DrawContext ctx, int x, int y, int w, int h, int radius) {
        drawRoundedRect(ctx, x+2, y+4, w, h, radius, new Color(0,0,0,60));
        drawRoundedRect(ctx, x+1, y+2, w, h, radius, new Color(0,0,0,45));
    }

    

    public static void drawRoundedRectOutline(DrawContext ctx, int x, int y, int w, int h,
                                               int radius, Color color) {
        if (radius <= 0) { drawRectOutline(ctx, x, y, w, h, color); return; }
        radius = Math.min(radius, Math.min(w, h) / 2);
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // These draws go straight to the GPU while the world depth buffer is
        // still bound, so near geometry would punch through the UI.
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        float cr=color.getRed()/255f, cg=color.getGreen()/255f,
              cb=color.getBlue()/255f, ca=color.getAlpha()/255f;
        int segs = 12;
        BufferBuilder buf = Tessellator.getInstance()
            .begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segs; i++) {
            float a = -(float)(Math.PI/2) + (float)(Math.PI/2)*i/segs;
            buf.vertex(mat, x+w-radius+radius*(float)Math.cos(a), y+radius+radius*(float)Math.sin(a), 0).color(cr,cg,cb,ca);
        }
        for (int i = 0; i <= segs; i++) {
            float a = (float)(Math.PI/2)*i/segs;
            buf.vertex(mat, x+w-radius+radius*(float)Math.cos(a), y+h-radius+radius*(float)Math.sin(a), 0).color(cr,cg,cb,ca);
        }
        for (int i = 0; i <= segs; i++) {
            float a = (float)(Math.PI/2)+(float)(Math.PI/2)*i/segs;
            buf.vertex(mat, x+radius+radius*(float)Math.cos(a), y+h-radius+radius*(float)Math.sin(a), 0).color(cr,cg,cb,ca);
        }
        for (int i = 0; i <= segs; i++) {
            float a = (float)Math.PI+(float)(Math.PI/2)*i/segs;
            buf.vertex(mat, x+radius+radius*(float)Math.cos(a), y+radius+radius*(float)Math.sin(a), 0).color(cr,cg,cb,ca);
        }
        buf.vertex(mat, x+radius, y, 0).color(cr,cg,cb,ca);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    

    public static void drawRectOutline(DrawContext ctx, int x, int y, int w, int h, Color color) {
        ctx.fill(x,       y,       x+w,   y+1,   color.getRGB());
        ctx.fill(x,       y+h-1,   x+w,   y+h,   color.getRGB());
        ctx.fill(x,       y,       x+1,   y+h,   color.getRGB());
        ctx.fill(x+w-1,   y,       x+w,   y+h,   color.getRGB());
    }

    

    
    public static void drawGradientLine2D(DrawContext ctx, float x0, float y0, float x1, float y1,
                                           int width, Color ca, Color cb) {
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // These draws go straight to the GPU while the world depth buffer is
        // still bound, so near geometry would punch through the UI.
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(width);
        float ar=ca.getRed()/255f,ag=ca.getGreen()/255f,ab=ca.getBlue()/255f,aa=ca.getAlpha()/255f;
        float br=cb.getRed()/255f,bg=cb.getGreen()/255f,bb=cb.getBlue()/255f,ba=cb.getAlpha()/255f;
        BufferBuilder buf = Tessellator.getInstance()
            .begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        buf.vertex(mat, x0, y0, 0).color(ar,ag,ab,aa);
        buf.vertex(mat, x1, y1, 0).color(br,bg,bb,ba);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.lineWidth(1f);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    

    public static Color lerp(Color a, Color b, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        return new Color(
            (int)(a.getRed()   + (b.getRed()   - a.getRed())   * t),
            (int)(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int)(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t),
            (int)(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t)
        );
    }

    public static Color withAlpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), MathHelper.clamp(a, 0, 255));
    }

    public static int toARGB(Color c) {
        return (c.getAlpha()<<24)|(c.getRed()<<16)|(c.getGreen()<<8)|c.getBlue();
    }

    
    public static int packRGBA(float r, float g, float b, float a) {
        return ((int)(a*255)<<24)|((int)(r*255)<<16)|((int)(g*255)<<8)|(int)(b*255);
    }

    

    private static void addCorner(BufferBuilder buf, Matrix4f mat, float cx, float cy, int r,
                                   float startAngle, int segs, float cr, float cg, float cb, float ca) {
        float sweep = (float)(Math.PI/2.0);
        for (int i = 0; i <= segs; i++) {
            float a = startAngle + sweep*i/segs;
            buf.vertex(mat, cx+r*(float)Math.cos(a), cy+r*(float)Math.sin(a), 0).color(cr,cg,cb,ca);
        }
    }
}
