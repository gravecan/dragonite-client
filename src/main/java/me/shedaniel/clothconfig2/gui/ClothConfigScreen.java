package me.shedaniel.clothconfig2.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import me.shedaniel.clothconfig2.gui.prestige.PrestigeRenderHelper;
import me.shedaniel.clothconfig2.impl.RenderHelper;
import me.shedaniel.clothconfig2.impl.builders.AbstractFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.ActionFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.ColorFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import me.shedaniel.clothconfig2.impl.builders.FriendListFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.RangeSliderBuilder;
import me.shedaniel.clothconfig2.impl.builders.HSVColorPicker;
import me.shedaniel.clothconfig2.impl.builders.StringFieldBuilder;
import me.shedaniel.clothconfig2.impl.ConfigCategoryImpl;
import me.shedaniel.clothconfig2.impl.ConfigBuilderImpl;
import me.shedaniel.clothconfig2.impl.GuiKeybinds;
import me.shedaniel.clothconfig2.impl.HudConfigInit;
import me.shedaniel.clothconfig2.impl.PersistenceHelper;
import me.shedaniel.clothconfig2.impl.Config_StringList;
import me.shedaniel.clothconfig2.impl.OverlayRenderer;
import me.shedaniel.clothconfig2.internal.AuthGate;
import me.shedaniel.clothconfig2.internal.SecurityVault;
import me.shedaniel.clothconfig2.internal.ConfigLoader;
import me.shedaniel.clothconfig2.internal.SessionHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClothConfigScreen extends Screen {

    /** Locked GUI scale while ClickGUI is open (Prestige-style). */
    /** Locked GUI scale while ClickGUI is open (Prestige-style). */
    private static final double GUI_SCALE_LOCK = 2.0;
    private static double savedWindowScale = -1;

    private ConfigCategoryImpl descHoverMod;

    private static final int BASE_W      = 190;
    private static final int BASE_HDR_H  = 22;
    private static final int BASE_TAB_H  = 22;
    private static final int BASE_MOD_H  = 17;
    private static final int BASE_SET_H  = 14;
    private static final int BASE_PX     = 8;
    private static final int BASE_PY     = 3;
    private static final int BASE_IND    = 12;
    private static final int BASE_SL_H   = 4;
    private static final int BASE_SL_GAP = 2;
    private static final int BASE_MARGIN = 8;
    private static final int BASE_TGL_W  = 22;
    private static final int BASE_TGL_H  = 11;

    private int W      = BASE_W;
    private int HDR_H  = BASE_HDR_H;
    private int TAB_H  = BASE_TAB_H;
    private int MOD_H  = BASE_MOD_H;
    private int SET_H  = BASE_SET_H;
    private int PX     = BASE_PX;
    private int PY     = BASE_PY;
    private int IND    = BASE_IND;
    private int SL_H   = BASE_SL_H;
    private int SL_GAP = BASE_SL_GAP;
    private int MARGIN = BASE_MARGIN;
    private int TGL_W  = BASE_TGL_W;
    private int TGL_H  = BASE_TGL_H;

    private static final int C_BG        = 0xF2060610;
    private static final int C_HDR       = 0xFF04040C;
    private static final int C_TAB_BG    = 0xFF07070F;
    private static final int C_ACCENT    = 0xFF5865F2;
    private static final int C_ACCENT_HI = 0xFF8B96FA;
    private static final int C_ACCENT_DK = 0xFF3A47C8;
    private static final int C_TITLE     = 0xFFE8EEFF;
    private static final int C_MOD_ON    = 0xFFD0D8FF;
    private static final int C_MOD_OFF   = 0xFF3A3A58;
    private static final int C_SEP       = 0x08FFFFFF;
    private static final int C_LBL       = 0xFF465878;
    private static final int C_VAL       = 0xFF8898C8;
    private static final int C_KEY       = 0xFF4A5080; 
    private static final int C_ARROW_DIM = 0xFF252840;
    private static final int C_TGL_OFF   = 0xFF141420;
    private static final int C_TGL_KNOB  = 0xFF2C2C44;
    private static final int C_SL_TRK    = 0xFF0A0A18;
    private static final int C_SET_TINT  = 0x07FFFFFF;

    
    
    private static final String[] TABS = {
        me.shedaniel.clothconfig2.impl.SecString.OBF("Combat"),
        me.shedaniel.clothconfig2.impl.SecString.OBF("Visual"),
        me.shedaniel.clothconfig2.impl.SecString.OBF("Movement"),
        me.shedaniel.clothconfig2.impl.SecString.OBF("Misc")
    };
    private static final String[] FILT = {
        me.shedaniel.clothconfig2.impl.SecString.OBF("aura|bot|aim|trigger|killaura|reach|hitbox|click|shield|displacement|lag|totem|backtrack|shifttap|velocity|antibot|hitsound|knockback|lagexploit|autototem|jump preset|elytratarget|stasis"),
        me.shedaniel.clothconfig2.impl.SecString.OBF("esp|tracer|hud|render|fullbright|visual|arrow|particle|jump|nick|target|hat|cham|fog|invisible|nametag|health|overlay|direction|show|glass|trail|skeleton|skel|bubble|client hud|fps|ping|tps"),
        me.shedaniel.clothconfig2.impl.SecString.OBF("fly|speed|sprint|step|nofall|blink|phase|move|elytra|firework|scaffold|clutch|fake|stuck|nofjump|air|placer|wtap|boat|freecam|camera"),
        me.shedaniel.clothconfig2.impl.SecString.OBF("pot|pearl|chest|exp|tool|place|break|critical|ghost|nuker|refill|keypearl|fastplace|quickexp|stealer|friend|discord|resource|destruct|nick|detect")
    };
    
    private static int lastActiveTab = 0;
    private int   activeTab = 0;
    private final float[] tabScroll  = new float[TABS.length];
    private final float[] tabScrollT = new float[TABS.length];

    private static final int[] _b1  = {41,194,105,177,78,62,197,180,118,205};
    private static final int[] _b2  = {60,211,197,3,192,184,244,254,254};
    private static final int[] _b3  = {67,211,197,14,224,230,242,243,249,166,244,178,227,235,249};
    private static final int[] _b4  = {60,212,219,49,230,235,87};
    private static final int[] _b5  = {53,158,66,2,202,180,148,16};
    private static final int[] _b6  = {59,210,24,66,196,167,22,144,5,243,88,141,227,235,249};
    private static final int[] _b7  = {46,150,141,250,167,23,247,106,62,73,241,212,76};
    private static final int[] _b8  = {59,210,24,66,196,167,22,144,5,243,88,141};
    private static final int[] _b9  = {43,164,187,18,174,29,130,27,212,249,98,36,39,109,195,227,235,249};
    private static final int[] _b10 = {46,155,119,82,92,204,53,29,189,128,142,150};

    private final ConfigBuilderImpl        manager;
    private final List<ConfigCategoryImpl> modules = new ArrayList<>();

    private int     panX, panY;
    private boolean dragPanel;
    private int     dragOX, dragOY;

    private boolean dragTargetHud;
    private float dragTargetHudOX;
    private float dragTargetHudOY;

    private ConfigCategoryImpl expanded     = null;
    private ConfigCategoryImpl lastExpanded = null;

    private DoubleFieldBuilder sliderDrag;
    private RangeSliderBuilder rangeDrag;
    private boolean rangeDragMin; 

    private String colorHexInput = "";
    private boolean colorHexFocused = false;

    private ColorFieldBuilder openColorField;
    private int colorPopupX;
    private int colorPopupY;

    private static final int CP_PAD = 8;
    private static final int CP_PALETTE_W = 100;
    private static final int CP_PALETTE_H = 80;
    private static final int CP_HUE_W = 10;
    private static final int CP_ALPHA_H = 10;

    private static final int CP_SAVED_MAX = 5;
    private static final List<Integer> savedColors = new ArrayList<>();

    private StringFieldBuilder activeText;
    private boolean            textFocused;
    private int                caretTick;

    private int curMx, curMy;
    private float frameDt;

    

    private final Map<ConfigCategoryImpl,   Float> enAnim  = new HashMap<>();
    private final Map<ConfigCategoryImpl,   Float> hvAnim  = new HashMap<>();
    private final Map<ConfigCategoryImpl,   Float> prAnim  = new HashMap<>();
    private final Map<ConfigCategoryImpl,   Float> exAnim  = new HashMap<>();
    private final Map<AbstractFieldBuilder, Float> tgAnim  = new HashMap<>();
    private final Map<AbstractFieldBuilder, Float> slAnim  = new HashMap<>();
    private final Map<AbstractFieldBuilder, Float> enAmS   = new HashMap<>();
    private final Map<AbstractFieldBuilder, Float> prAmS   = new HashMap<>();
    private final Map<AbstractFieldBuilder, Float> rowAnim = new HashMap<>();

    private final Map<ColorFieldBuilder, HSVColorPicker> hsv = new HashMap<>();

    private final Map<ColorFieldBuilder, Float> colorOpacity = new HashMap<>();

    private NativeImageBackedTexture svPaletteTex;
    private Identifier svPaletteTexId;
    private int svPaletteW = -1, svPaletteH = -1;
    private int svPaletteHueKey = Integer.MIN_VALUE;

    
    private float openAnim   = 0f;
    private float openTarget = 1f;
    private int bindCaptureDelay = 0;

    
    
    private float tabAlpha     = 1f; 
    private int   tabAlphaDir  = 1;  
    private int   pendingTab   = -1; 

    
    
    private long lastFrameNano = 0L;

    public static void open() {
        DragoniteGlassScreen.open();
    }

    private static void lockGuiScale(MinecraftClient mc) {
        if (savedWindowScale < 0) {
            savedWindowScale = mc.getWindow().getScaleFactor();
        }
        mc.getWindow().setScaleFactor(GUI_SCALE_LOCK);
        mc.onResolutionChanged();
    }

    static void restoreGuiScale(MinecraftClient mc) {
        if (mc == null || savedWindowScale < 0) {
            return;
        }
        double restore = savedWindowScale;
        savedWindowScale = -1;
        mc.getWindow().setScaleFactor(restore);
        mc.onResolutionChanged();
    }

    private static int f2b(float f) { return Math.round(f * 255f); }

    private void renderCheckerboard(DrawContext ctx, int x, int y, int w, int h, float alpha) {
        int cs = 3;
        for (int iy = 0; iy < h; iy += cs) {
            for (int ix = 0; ix < w; ix += cs) {
                boolean light = ((ix / cs) + (iy / cs)) % 2 == 0;
                int col = light ? 0xFFAAAAAA : 0xFF666666;
                ctx.fill(x + ix, y + iy,
                        Math.min(x + ix + cs, x + w),
                        Math.min(y + iy + cs, y + h),
                        wa(col, alpha));
            }
        }
    }

    private void border(DrawContext ctx, int x, int y, int w, int h, int col) {
        ctx.fill(x, y, x + w, y + 1, col);
        ctx.fill(x, y + h - 1, x + w, y + h, col);
        ctx.fill(x, y, x + 1, y + h, col);
        ctx.fill(x + w - 1, y, x + w, y + h, col);
    }

    
    private float colorPickerAnim = 0f;
    private float hueHandleAnim = 0f;
    private float alphaHandleAnim = 0f;
    private float svHandleAnim = 0f;

    private void renderSVPaletteRoundedTexture(DrawContext ctx, int x, int y, int w, int h, HSVColorPicker picker, float alpha, int rad) {
        renderSVPaletteTexture(ctx, x, y, w, h, picker);
        if (rad <= 0) {
            RenderHelper.drawRectOutline(ctx, x, y, w, h, new Color(70, 70, 82, (int)(255 * alpha)));
        } else {
            RenderHelper.drawRoundedRectOutline(ctx, x, y, w, h, rad, new Color(70, 70, 82, (int)(255 * alpha)));
        }
    }

    private void renderSVPaletteTexture(DrawContext ctx, int x, int y, int w, int h, HSVColorPicker picker) {
        int hueKey = Math.round(MathHelper.clamp(picker.getHue(), 0f, 360f));
        if (svPaletteTex == null || svPaletteTexId == null || svPaletteW != w || svPaletteH != h || svPaletteHueKey != hueKey) {
            rebuildSVTexture(w, h, hueKey);
        }
        if (svPaletteTexId == null) return;
        drawTexturedQuad(ctx, svPaletteTexId, x, y, w, h);
    }

    private void rebuildSVTexture(int w, int h, int hueKey) {
        svPaletteW = w;
        svPaletteH = h;
        svPaletteHueKey = hueKey;

        if (svPaletteTex != null) {
            try { svPaletteTex.close(); } catch (Exception ignored) {}
            svPaletteTex = null;
        }

        NativeImage img = new NativeImage(w, h, false);
        float hue = hueKey;
        for (int y = 0; y < h; y++) {
            float br = 1f - (y / (float) Math.max(1, h - 1));
            for (int x = 0; x < w; x++) {
                float sat = x / (float) Math.max(1, w - 1);
                int rgb = HSVColorPicker.hsvToRgb(hue, sat, br);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                
                int abgr = (0xFF << 24) | (b << 16) | (g << 8) | r;
                img.setColor(x, y, abgr);
            }
        }

        svPaletteTex = new NativeImageBackedTexture(img);
        svPaletteTex.setFilter(true, false);
        svPaletteTexId = registerDynamicTextureCompat("sv_palette", svPaletteTex);
    }

    private Identifier registerDynamicTextureCompat(String name, NativeImageBackedTexture tex) {
        try {
            Object tm = client.getTextureManager();
            try {
                var m = tm.getClass().getMethod("registerDynamicTexture", String.class, NativeImageBackedTexture.class);
                return (Identifier) m.invoke(tm, name, tex);
            } catch (NoSuchMethodException ignored) {
            }
            Identifier id = Identifier.of("cloth-config2", "dynamic/" + name);
            var m2 = tm.getClass().getMethod("registerTexture", Identifier.class, net.minecraft.client.texture.AbstractTexture.class);
            m2.invoke(tm, id, tex);
            return id;
        } catch (Exception e) {
            return null;
        }
    }

    private void drawTexturedQuad(DrawContext ctx, Identifier tex, int x, int y, int w, int h) {
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, tex);
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buf.vertex(mat, x,     y + h, 0).texture(0f, 1f);
        buf.vertex(mat, x + w, y + h, 0).texture(1f, 1f);
        buf.vertex(mat, x + w, y,     0).texture(1f, 0f);
        buf.vertex(mat, x,     y,     0).texture(0f, 0f);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
    
    private void renderHueSliderRounded(DrawContext ctx, int x, int y, int w, int h, HSVColorPicker picker, float alpha, int rad) {
        
        for (int px = 0; px < w; px++) {
            float hue = (px / (float)w) * 360f;
            int col = HSVColorPicker.hsvToRgb(hue, 1f, 1f);
            int r = (col >> 16) & 0xFF, g = (col >> 8) & 0xFF, b = col & 0xFF;
            ctx.fill(x + px, y, x + px + 1, y + h, wa((255 << 24) | (r << 16) | (g << 8) | b, alpha));
        }
        if (rad <= 0) {
            RenderHelper.drawRectOutline(ctx, x, y, w, h, new Color(70, 70, 82, (int)(255 * alpha)));
        } else {
            RenderHelper.drawRoundedRectOutline(ctx, x, y, w, h, rad, new Color(70, 70, 82, (int)(255 * alpha)));
        }
    }
    
    private void renderAlphaSliderRounded(DrawContext ctx, int x, int y, int w, int h, float opacity, HSVColorPicker picker, float alpha, int rad) {
        
        int cs = 3;
        for (int py = 0; py < h; py += cs) {
            for (int px = 0; px < w; px += cs) {
                if (((px / cs) + (py / cs)) % 2 == 0) {
                    ctx.fill(x + px, y + py, Math.min(x + w, x + px + cs), Math.min(y + h, y + py + cs), wa(0xFF353545, alpha));
                }
            }
        }
        
        int rgb = picker.toRGB();
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        for (int px = 0; px < w; px++) {
            float t = px / (float)(w - 1);
            int col = ((int)(t * 255 * alpha) << 24) | (r << 16) | (g << 8) | b;
            ctx.fill(x + px, y, x + px + 1, y + h, col);
        }
        if (rad <= 0) {
            RenderHelper.drawRectOutline(ctx, x, y, w, h, new Color(70, 70, 82, (int)(255 * alpha)));
        } else {
            RenderHelper.drawRoundedRectOutline(ctx, x, y, w, h, rad, new Color(70, 70, 82, (int)(255 * alpha)));
        }
    }
    
    private void renderPopupHandlesV2(DrawContext ctx, int palX, int palY, int palW, int palH, int hueY, int alphaY, int sliderH, HSVColorPicker picker, float alpha, float opacity) {
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        
        int handleA = (int)(255 * alpha);
        int white = wa(0xFFFFFFFF, alpha);

        
        float sat = MathHelper.clamp(picker.getSaturation(), 0f, 1f);
        float br = MathHelper.clamp(picker.getBrightness(), 0f, 1f);
        float svhx = palX + sat * (palW - 1);
        float svhy = palY + (1f - br) * (palH - 1);
        
        
        drawCircle(ctx, svhx, svhy, 5.5f, new Color(0, 0, 0, (int)(230 * alpha)), 48);
        drawCircle(ctx, svhx, svhy, 4.5f, new Color(255, 255, 255, handleA), 48);
        drawCircle(ctx, svhx, svhy, 3.0f, new Color(0, 0, 0, (int)(120 * alpha)), 48);
        
        
        float hueT = MathHelper.clamp(picker.getHue() / 360f, 0f, 1f);
        int hx = palX + (int)(hueT * (palW - 1));
        int knob = sliderH + 2;
        int kx0 = hx - knob / 2;
        int ky0 = hueY - 1;
        ctx.fill(kx0, ky0, kx0 + knob, ky0 + knob, wa(0xFF101014, alpha));
        ctx.fill(kx0 + 1, ky0 + 1, kx0 + knob - 1, ky0 + knob - 1, white);
        
        
        int ax = palX + (int)(opacity * (palW - 1));
        int akx0 = ax - knob / 2;
        int aky0 = alphaY - 1;
        ctx.fill(akx0, aky0, akx0 + knob, aky0 + knob, wa(0xFF101014, alpha));
        ctx.fill(akx0 + 1, aky0 + 1, akx0 + knob - 1, aky0 + knob - 1, white);
        
        RenderSystem.enableDepthTest();
    }

    private void renderAlphaSliderHorizontal(DrawContext ctx, int x, int y, int w, int h, float opacity, HSVColorPicker picker, float alpha) {
        RenderHelper.drawRoundedRect(ctx, x, y, w, h, 4, new Color(25, 25, 30, (int)(200 * alpha)));
        
        int s = 4;
        for (int yy = 0; yy < h; yy += s) {
            for (int xx = 0; xx < w; xx += s) {
                if (((xx / s) + (yy / s)) % 2 == 0) {
                    ctx.fill(x + xx, y + yy, Math.min(x + w, x + xx + s), Math.min(y + h, y + yy + s), wa(0x20FFFFFF, alpha));
                }
            }
        }
        int rgb = picker.toRGB();
        drawGradientHorizontalAlpha(ctx, x, y, w, h, (rgb>>16)&0xFF, (rgb>>8)&0xFF, rgb&0xFF, alpha);
        RenderHelper.drawRoundedRectOutline(ctx, x, y, w, h, 4, new Color(80, 80, 95, (int)(150 * alpha)));
    }

    private void drawGradientHorizontalAlpha(DrawContext ctx, int x, int y, int w, int h, int r, int g, int b, float alpha) {
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buf.vertex(mat, x, y, 0).color(r/255f, g/255f, b/255f, 0f);
        buf.vertex(mat, x, y + h, 0).color(r/255f, g/255f, b/255f, 0f);
        buf.vertex(mat, x + w, y + h, 0).color(r/255f, g/255f, b/255f, alpha);
        buf.vertex(mat, x + w, y, 0).color(r/255f, g/255f, b/255f, alpha);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }

    private void renderHandlesRedesign(DrawContext ctx, int hueX, int hueY, int hueW, int h, int svX, int svY, int svW, int opY, int opW, HSVColorPicker picker, float alpha, float opacity) {
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        
        int handleA = (int)(255 * alpha);

        
        float hueT = MathHelper.clamp(picker.getHue() / 360f, 0f, 1f);
        int hy = hueY + (int)(hueT * (h - 1));
        
        RenderHelper.drawRoundedRect(ctx, hueX - 2, hy - 2, hueW + 4, 4, 1, new Color(255, 255, 255, handleA));
        RenderHelper.drawRoundedRectOutline(ctx, hueX - 2, hy - 2, hueW + 4, 4, 1, new Color(0, 0, 0, (int)(150 * alpha)));

        
        float opX = svX + opacity * (opW - 1);
        float opYCenter = opY + CP_ALPHA_H / 2f;
        drawCircle(ctx, opX, opYCenter, 7.5f, new Color(0, 0, 0, (int)(200 * alpha)), 32);
        drawCircle(ctx, opX, opYCenter, 6.5f, new Color(255, 255, 255, handleA), 32);

        
        float sat = MathHelper.clamp(picker.getSaturation(), 0f, 1f);
        float br = MathHelper.clamp(picker.getBrightness(), 0f, 1f);
        float svhx = svX + sat * (svW - 1);
        float svhy = svY + (1f - br) * (h - 1);
        
        
        drawCircle(ctx, svhx, svhy, 8.5f, new Color(0, 0, 0, (int)(220 * alpha)), 64);
        drawCircle(ctx, svhx, svhy, 7.5f, new Color(255, 255, 255, handleA), 64);
        int currentColor = HSVColorPicker.hsvToRgb(picker.getHue(), picker.getSaturation(), picker.getBrightness());
        drawCircle(ctx, svhx, svhy, 5.5f, new Color((currentColor>>16)&0xFF, (currentColor>>8)&0xFF, currentColor&0xFF, handleA), 64);
        
        RenderSystem.enableDepthTest();
    }

    private void renderHueSlider(DrawContext ctx, int x, int y, int w, int h, HSVColorPicker picker, float alpha) {
        
        int[] hueColors = {
            0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000
        };
        float segH = h / 6f;
        for (int i = 0; i < 6; i++) {
            int y0 = y + (int)(i * segH);
            int y1 = y + (int)((i + 1) * segH);
            
            int c1 = hueColors[i];
            int c2 = hueColors[i + 1];
            int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
            int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
            for (int dy = 0; dy < (y1 - y0); dy++) {
                float t = dy / (float)Math.max(1, y1 - y0 - 1);
                int r = (int)(r1 + (r2 - r1) * t);
                int g = (int)(g1 + (g2 - g1) * t);
                int b = (int)(b1 + (b2 - b1) * t);
                ctx.fill(x, y0 + dy, x + w, y0 + dy + 1, wa((255 << 24) | (r << 16) | (g << 8) | b, alpha));
            }
        }
        
        ctx.fill(x, y, x + w, y + 1, wa(0xFF505060, alpha));
        ctx.fill(x, y + h - 1, x + w, y + h, wa(0xFF505060, alpha));
        ctx.fill(x, y, x + 1, y + h, wa(0xFF505060, alpha));
        ctx.fill(x + w - 1, y, x + w, y + h, wa(0xFF505060, alpha));
    }

    private void renderAlphaSlider(DrawContext ctx, int x, int y, int w, int h, float opacity, HSVColorPicker picker, float alpha) {
        
    }

    private void renderSVPalette(DrawContext ctx, int x, int y, int w, int h, HSVColorPicker picker, float alpha) {
        
        float hue = picker.getHue();
        
        
        
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                float sat = px / (float)Math.max(1, w - 1);
                float bright = 1f - py / (float)Math.max(1, h - 1);
                
                
                int col = HSVColorPicker.hsvToRgb(hue, sat, bright);
                int r = (col >> 16) & 0xFF;
                int g = (col >> 8) & 0xFF;
                int b = col & 0xFF;
                
                ctx.fill(x + px, y + py, x + px + 1, y + py + 1, wa((255 << 24) | (r << 16) | (g << 8) | b, alpha));
            }
        }
        
        
        ctx.fill(x, y, x + w, y + 1, wa(0xFF3A3A45, alpha));
        ctx.fill(x, y + h - 1, x + w, y + h, wa(0xFF3A3A45, alpha));
        ctx.fill(x, y, x + 1, y + h, wa(0xFF3A3A45, alpha));
        ctx.fill(x + w - 1, y, x + w, y + h, wa(0xFF3A3A45, alpha));
    }

    private void renderHexInput(DrawContext ctx, int x, int y, int w, int h, HSVColorPicker picker, float opacity, float alpha) {
        
    }

    
    private int renderInlineColorPicker(DrawContext ctx, ColorFieldBuilder c, int bx, int startY, float alpha) {
        HSVColorPicker picker = hsv.computeIfAbsent(c,
                k -> new HSVColorPicker(0xFF000000 | (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue()));
        float op = colorOpacity.computeIfAbsent(c, k -> c.getAlpha() / 255f);
        
        PrestigeColorPicker.State cp = PrestigeColorPicker.stateOf(c);
        if (!cp.over && !cp.over2 && !cp.over3) {
            int rgb = (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
            picker.setFromRGB(rgb);
            op = c.getAlpha() / 255f;
            colorOpacity.put(c, op);
        }

        colorPickerAnim = lerpF(colorPickerAnim, 1f, 0.2f);
        float anim = colorPickerAnim * alpha;
        if (anim < 0.01f) return startY;

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        
        int padding = 12;
        int pickerW = W - IND - PX;
        int pickerH = 130; 
        int pickerX = bx + IND;
        int pickerY = startY + 4;
        
        int sliderW = 12; 
        int sliderH = pickerH - 32;
        int spacing = 8; 
        int paletteW = pickerW - sliderW * 2 - spacing * 3;
        int paletteH = sliderH;
        
        
        int bgCol = wa(0xF0101014, anim);
        int borderCol = wa(0xFF505060, anim);
        int radius = 8;
        
        fillRoundedRect(ctx, pickerX, pickerY, pickerX + pickerW, pickerY + pickerH, radius, bgCol);
        strokeRoundedRect(ctx, pickerX, pickerY, pickerX + pickerW, pickerY + pickerH, radius, 2, borderCol);

        int contentX = pickerX + padding;
        int contentY = pickerY + padding;

        
        renderHueSliderVertical(ctx, contentX, contentY, sliderW, paletteH, picker, anim);
        
        
        int alphaX = contentX + sliderW + spacing;
        renderAlphaSliderVerticalRounded(ctx, alphaX, contentY, sliderW, paletteH, op, picker, anim);
        
        
        int paletteX = contentX + sliderW * 2 + spacing * 2;
        renderSVPaletteInlineRounded(ctx, paletteX, contentY, paletteW, paletteH, picker, anim, 6);

        
        int hexY = contentY + paletteH + spacing + 4;
        int aHex = (int)(MathHelper.clamp(op, 0f, 1f) * 255f);
        String hex = aHex >= 255 ? String.format("#%06X", picker.toRGB() & 0xFFFFFF) 
                                 : String.format("#%02X%06X", aHex, picker.toRGB() & 0xFFFFFF);
        
        
        int hexW = client.textRenderer.getWidth(hex) + 8;
        fillRoundedRect(ctx, contentX - 2, hexY - 2, contentX + hexW, hexY + 10, 3, wa(0xFF1a1a22, anim));
        ctx.drawText(client.textRenderer, hex, contentX + 2, hexY, wa(0xFFD0D8FF, anim), false);
        
        
        int previewX = pickerX + pickerW - padding - 52;
        int previewW = 48, previewH = 18;
        int previewCol = (aHex << 24) | (picker.getRed() << 16) | (picker.getGreen() << 8) | picker.getBlue();
        
        
        int cs = 4;
        for (int py = 0; py < previewH; py += cs) {
            for (int px = 0; px < previewW; px += cs) {
                if (((px / cs) + (py / cs)) % 2 == 0) {
                    ctx.fill(previewX + px, hexY - 2 + py, 
                            Math.min(previewX + previewW, previewX + px + cs), 
                            Math.min(hexY - 2 + previewH, hexY - 2 + py + cs), 
                            wa(0xFF808080, anim));
                } else {
                    ctx.fill(previewX + px, hexY - 2 + py, 
                            Math.min(previewX + previewW, previewX + px + cs), 
                            Math.min(hexY - 2 + previewH, hexY - 2 + py + cs), 
                            wa(0xFF404040, anim));
                }
            }
        }
        
        fillRoundedRect(ctx, previewX, hexY - 2, previewX + previewW, hexY + previewH - 2, 4, previewCol);
        strokeRoundedRect(ctx, previewX, hexY - 2, previewX + previewW, hexY + previewH - 2, 4, 2, borderCol);

        
        renderInlineHandlesRounded(ctx, contentX, contentY, sliderW, paletteH, alphaX, paletteX, paletteW, picker, anim, op);

        RenderSystem.enableDepthTest();
        return pickerY + pickerH + 4;
    }
    
    private void renderAlphaSliderVertical(DrawContext ctx, int x, int y, int w, int h, float opacity, HSVColorPicker picker, float alpha) {
        
        ctx.fill(x, y, x + w, y + h, wa(0xFF252530, alpha));
        
        
        int cs = 3;
        for (int py = 0; py < h; py += cs) {
            for (int px = 0; px < w; px += cs) {
                if (((px / cs) + (py / cs)) % 2 == 0) {
                    ctx.fill(x + px, y + py, Math.min(x + w, x + px + cs), Math.min(y + h, y + py + cs), wa(0xFF404050, alpha));
                }
            }
        }
        
        
        int rgb = picker.toRGB();
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        for (int py = 0; py < h; py++) {
            float t = py / (float)(h - 1);
            int col = ((int)(t * 255 * alpha) << 24) | (r << 16) | (g << 8) | b;
            ctx.fill(x, y + py, x + w, y + py + 1, col);
        }
        
        
        ctx.fill(x, y, x + w, y + 1, wa(0xFF505060, alpha));
        ctx.fill(x, y + h - 1, x + w, y + h, wa(0xFF505060, alpha));
        ctx.fill(x, y, x + 1, y + h, wa(0xFF505060, alpha));
        ctx.fill(x + w - 1, y, x + w, y + h, wa(0xFF505060, alpha));
    }
    
    private void renderInlineHandlesRounded(DrawContext ctx, int hueX, int hueY, int sliderW, int h, int alphaX, int palX, int palW, HSVColorPicker picker, float alpha, float opacity) {
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        
        int handleA = (int)(255 * alpha);

        
        float hueT = MathHelper.clamp(picker.getHue() / 360f, 0f, 1f);
        int hy = hueY + (int)(hueT * (h - 1));
        int hx = hueX + sliderW / 2;
        
        drawCircle(ctx, hx, hy, 10f, new Color(0, 0, 0, (int)(220 * alpha)), 32);
        drawCircle(ctx, hx, hy, 8f, new Color(255, 255, 255, handleA), 32);
        drawCircle(ctx, hx, hy, 6f, new Color(0, 0, 0, (int)(160 * alpha)), 32);

        
        int ay = hueY + (int)(opacity * (h - 1));
        int ax = alphaX + sliderW / 2;
        
        drawCircle(ctx, ax, ay, 10f, new Color(0, 0, 0, (int)(220 * alpha)), 32);
        drawCircle(ctx, ax, ay, 8f, new Color(255, 255, 255, handleA), 32);
        drawCircle(ctx, ax, ay, 6f, new Color(0, 0, 0, (int)(160 * alpha)), 32);

        
        float sat = MathHelper.clamp(picker.getSaturation(), 0f, 1f);
        float br = MathHelper.clamp(picker.getBrightness(), 0f, 1f);
        float svhx = palX + sat * (palW - 1);
        float svhy = hueY + (1f - br) * (h - 1);
        
        
        drawCircle(ctx, svhx, svhy, 11f, new Color(0, 0, 0, (int)(230 * alpha)), 48);
        
        drawCircle(ctx, svhx, svhy, 9f, new Color(255, 255, 255, handleA), 48);
        
        
        int currentColor = HSVColorPicker.hsvToRgb(picker.getHue(), picker.getSaturation(), picker.getBrightness());
        int cr = (currentColor >> 16) & 0xFF, cg = (currentColor >> 8) & 0xFF, cb = currentColor & 0xFF;
        drawCircle(ctx, svhx, svhy, 6.5f, new Color(cr, cg, cb, handleA), 48);
        
        
        ctx.fill((int)svhx - 1, (int)svhy - 12, (int)svhx + 1, (int)svhy - 9, wa(0xFFFFFFFF, alpha));
        ctx.fill((int)svhx - 1, (int)svhy + 9, (int)svhx + 1, (int)svhy + 12, wa(0xFFFFFFFF, alpha));
        ctx.fill((int)svhx - 12, (int)svhy - 1, (int)svhx - 9, (int)svhy + 1, wa(0xFFFFFFFF, alpha));
        ctx.fill((int)svhx + 9, (int)svhy - 1, (int)svhx + 12, (int)svhy + 1, wa(0xFFFFFFFF, alpha));
        
        RenderSystem.enableDepthTest();
    }
    
    
    private void fillRoundedRect(DrawContext ctx, int x0, int y0, int x1, int y1, int r, int col) {
        if (x1 <= x0 || y1 <= y0) return;
        int mr = Math.min((x1 - x0) / 2, (y1 - y0) / 2);
        r = Math.min(r, mr);
        if (r <= 0) { ctx.fill(x0, y0, x1, y1, col); return; }
        
        
        ctx.fill(x0 + r, y0, x1 - r, y1, col);
        ctx.fill(x0, y0 + r, x0 + r, y1 - r, col);
        ctx.fill(x1 - r, y0 + r, x1, y1 - r, col);
        
        
        for (int i = 0; i < r; i++) {
            int xo = (int) Math.sqrt((double) r * r - (double) i * i);
            ctx.fill(x0 + r - xo, y0 + r - i, x0 + r, y0 + r - i + 1, col);
            ctx.fill(x1 - r, y0 + r - i, x1 - r + xo, y0 + r - i + 1, col);
            ctx.fill(x0 + r - xo, y1 - r + i, x0 + r, y1 - r + i + 1, col);
            ctx.fill(x1 - r, y1 - r + i, x1 - r + xo, y1 - r + i + 1, col);
        }
    }
    
    
    private void strokeRoundedRect(DrawContext ctx, int x0, int y0, int x1, int y1, int r, int t, int col) {
        if (x1 <= x0 || y1 <= y0) return;
        int mr = Math.min((x1 - x0) / 2, (y1 - y0) / 2);
        r = Math.min(r, mr);
        
        
        ctx.fill(x0 + r, y0, x1 - r, y0 + t, col);
        ctx.fill(x0 + r, y1 - t, x1 - r, y1, col);
        ctx.fill(x0, y0 + r, x0 + t, y1 - r, col);
        ctx.fill(x1 - t, y0 + r, x1, y1 - r, col);
        
        
        for (int i = 0; i < r; i++) {
            int xo = (int) Math.sqrt((double) r * r - (double) i * i);
            int xi = (int) Math.sqrt((double) (r - t) * (r - t) - (double) i * i);
            if (xo > xi) {
                ctx.fill(x0 + r - xo, y0 + r - i, x0 + r - xi, y0 + r - i + 1, col);
                ctx.fill(x1 - r + xi, y0 + r - i, x1 - r + xo, y0 + r - i + 1, col);
                ctx.fill(x0 + r - xo, y1 - r + i, x0 + r - xi, y1 - r + i + 1, col);
                ctx.fill(x1 - r + xi, y1 - r + i, x1 - r + xo, y1 - r + i + 1, col);
            }
        }
    }
    
    
    private void renderHueSliderVertical(DrawContext ctx, int x, int y, int w, int h, HSVColorPicker picker, float alpha) {
        int r = w / 2;
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        int fa = (int)(255 * alpha);
        
        
        int segments = 60;
        float segH = h / (float)segments;
        
        for (int i = 0; i < segments; i++) {
            float hue1 = (i / (float)segments) * 360f;
            float hue2 = ((i + 1) / (float)segments) * 360f;
            
            int col1 = HSVColorPicker.hsvToRgb(hue1, 1f, 1f);
            int col2 = HSVColorPicker.hsvToRgb(hue2, 1f, 1f);
            
            int r1 = (col1 >> 16) & 0xFF, g1 = (col1 >> 8) & 0xFF, b1 = col1 & 0xFF;
            int r2 = (col2 >> 16) & 0xFF, g2 = (col2 >> 8) & 0xFF, b2 = col2 & 0xFF;
            
            float y1 = y + i * segH;
            float y2 = y + (i + 1) * segH;
            
            BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            buf.vertex(mat, x, y2, 0).color(r1, g1, b1, fa);
            buf.vertex(mat, x + w, y2, 0).color(r1, g1, b1, fa);
            buf.vertex(mat, x + w, y1, 0).color(r2, g2, b2, fa);
            buf.vertex(mat, x, y1, 0).color(r2, g2, b2, fa);
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }
        
        RenderSystem.disableBlend();
        
        
        strokeRoundedRect(ctx, x, y, x + w, y + h, r, 1, wa(0xFF505060, alpha));
    }
    
    
    private void renderAlphaSliderVerticalRounded(DrawContext ctx, int x, int y, int w, int h, float opacity, HSVColorPicker picker, float alpha) {
        int r = w / 2;
        
        
        int cs = 5;
        for (int py = 0; py < h; py += cs) {
            for (int px = 0; px < w; px += cs) {
                boolean even = ((px / cs) + (py / cs)) % 2 == 0;
                int col = even ? 0xFF808080 : 0xFF404040;
                ctx.fill(x + px, y + py, Math.min(x + w, x + px + cs), Math.min(y + h, y + py + cs), wa(col, alpha));
            }
        }
        
        
        int rgb = picker.toRGB();
        int rr = (rgb >> 16) & 0xFF, gg = (rgb >> 8) & 0xFF, bb = rgb & 0xFF;
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        int fa = (int)(255 * alpha);
        
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        
        buf.vertex(mat, x, y + h, 0).color(rr, gg, bb, fa);
        buf.vertex(mat, x + w, y + h, 0).color(rr, gg, bb, fa);
        buf.vertex(mat, x + w, y, 0).color(rr, gg, bb, 0);
        buf.vertex(mat, x, y, 0).color(rr, gg, bb, 0);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        
        RenderSystem.disableBlend();
        
        
        strokeRoundedRect(ctx, x, y, x + w, y + h, r, 1, wa(0xFF505060, alpha));
    }
    
    
    private void renderSVPaletteInlineRounded(DrawContext ctx, int x, int y, int w, int h, HSVColorPicker picker, float alpha, int rad) {
        float hue = picker.getHue();
        int pureHue = HSVColorPicker.hsvToRgb(hue, 1f, 1f);
        int hr = (pureHue >> 16) & 0xFF, hg = (pureHue >> 8) & 0xFF, hb = pureHue & 0xFF;
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        int fa = (int)(255 * alpha);
        
        
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buf.vertex(mat, x, y + h, 0).color(255, 255, 255, fa);
        buf.vertex(mat, x + w, y + h, 0).color(hr, hg, hb, fa);
        buf.vertex(mat, x + w, y, 0).color(hr, hg, hb, fa);
        buf.vertex(mat, x, y, 0).color(255, 255, 255, fa);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        
        
        buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buf.vertex(mat, x, y + h, 0).color(0, 0, 0, fa);
        buf.vertex(mat, x + w, y + h, 0).color(0, 0, 0, fa);
        buf.vertex(mat, x + w, y, 0).color(0, 0, 0, 0);
        buf.vertex(mat, x, y, 0).color(0, 0, 0, 0);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        
        RenderSystem.disableBlend();
        
        
        strokeRoundedRect(ctx, x, y, x + w, y + h, rad, 1, wa(0xFF505060, alpha));
    }
    
    
    private void renderHueSliderHorizontalRounded(DrawContext ctx, int x, int y, int w, int h, HSVColorPicker picker, float alpha) {
        int r = h / 2;
        
        
        for (int px = 0; px < w; px++) {
            float hue = (px / (float) w) * 360f;
            int col = HSVColorPicker.hsvToRgb(hue, 1f, 1f);
            int r1 = (col >> 16) & 0xFF, g1 = (col >> 8) & 0xFF, b1 = col & 0xFF;
            ctx.fill(x + px, y, x + px + 1, y + h, wa((255 << 24) | (r1 << 16) | (g1 << 8) | b1, alpha));
        }
        
        
        strokeRoundedRect(ctx, x, y, x + w, y + h, r, 1, wa(0xFF3A4050, alpha));
    }
    
    
    private void renderAlphaSliderHorizontalRounded(DrawContext ctx, int x, int y, int w, int h, float opacity, HSVColorPicker picker, float alpha) {
        int r = h / 2;
        
        
        int cs = 4;
        for (int py = 0; py < h; py += cs) {
            for (int px = 0; px < w; px += cs) {
                boolean even = ((px / cs) + (py / cs)) % 2 == 0;
                ctx.fill(x + px, y + py, Math.min(x + w, x + px + cs), Math.min(y + h, y + py + cs), 
                    wa(even ? 0xFF606060 : 0xFF404040, alpha));
            }
        }
        
        
        int rgb = picker.toRGB();
        int rr = (rgb >> 16) & 0xFF, gg = (rgb >> 8) & 0xFF, bb = rgb & 0xFF;
        for (int px = 0; px < w; px++) {
            float t = px / (float) w;
            int a = (int)(t * 255 * alpha);
            ctx.fill(x + px, y, x + px + 1, y + h, (a << 24) | (rr << 16) | (gg << 8) | bb);
        }
        
        
        strokeRoundedRect(ctx, x, y, x + w, y + h, r, 1, wa(0xFF3A4050, alpha));
    }
    
    
    private void renderPopupHandlesRounded(DrawContext ctx, int palX, int palY, int palW, int palH, int hueY, int alphaY, int sliderH, HSVColorPicker picker, float alpha, float opacity) {
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        
        int handleA = (int)(255 * alpha);

        
        float sat = MathHelper.clamp(picker.getSaturation(), 0f, 1f);
        float br = MathHelper.clamp(picker.getBrightness(), 0f, 1f);
        float svhx = palX + sat * (palW - 1);
        float svhy = palY + (1f - br) * (palH - 1);
        
        
        drawCircle(ctx, svhx, svhy, 11f, new Color(0, 0, 0, (int)(230 * alpha)), 48);
        
        drawCircle(ctx, svhx, svhy, 9f, new Color(255, 255, 255, handleA), 48);
        
        
        int currentColor = HSVColorPicker.hsvToRgb(picker.getHue(), picker.getSaturation(), picker.getBrightness());
        int cr = (currentColor >> 16) & 0xFF, cg = (currentColor >> 8) & 0xFF, cb = currentColor & 0xFF;
        drawCircle(ctx, svhx, svhy, 6.5f, new Color(cr, cg, cb, handleA), 48);
        
        
        ctx.fill((int)svhx - 1, (int)svhy - 12, (int)svhx + 1, (int)svhy - 9, wa(0xFFFFFFFF, alpha));
        ctx.fill((int)svhx - 1, (int)svhy + 9, (int)svhx + 1, (int)svhy + 12, wa(0xFFFFFFFF, alpha));
        ctx.fill((int)svhx - 12, (int)svhy - 1, (int)svhx - 9, (int)svhy + 1, wa(0xFFFFFFFF, alpha));
        ctx.fill((int)svhx + 9, (int)svhy - 1, (int)svhx + 12, (int)svhy + 1, wa(0xFFFFFFFF, alpha));

        
        float hueT = MathHelper.clamp(picker.getHue() / 360f, 0f, 1f);
        int hx = palX + (int)(hueT * palW);
        int hy = hueY + sliderH / 2;
        drawCircle(ctx, hx, hy, 10f, new Color(0, 0, 0, (int)(220 * alpha)), 32);
        drawCircle(ctx, hx, hy, 8f, new Color(255, 255, 255, handleA), 32);
        drawCircle(ctx, hx, hy, 6f, new Color(0, 0, 0, (int)(160 * alpha)), 32);

        
        int ax = palX + (int)(opacity * palW);
        int ay = alphaY + sliderH / 2;
        drawCircle(ctx, ax, ay, 10f, new Color(0, 0, 0, (int)(220 * alpha)), 32);
        drawCircle(ctx, ax, ay, 8f, new Color(255, 255, 255, handleA), 32);
        drawCircle(ctx, ax, ay, 6f, new Color(0, 0, 0, (int)(160 * alpha)), 32);
        
        RenderSystem.enableDepthTest();
    }

    
    private void drawGradientSegment(DrawContext ctx, int x, int y, int w, int h, int colTop, int colBot, float alpha, int radTL, int radTR, int radBL, int radBR) {
        if (h <= 0) return;
        int a = (int)(255 * alpha);
        int topR = ((colTop >> 16) & 0xFF), topG = ((colTop >> 8) & 0xFF), topB = (colTop & 0xFF);
        int botR = ((colBot >> 16) & 0xFF), botG = ((colBot >> 8) & 0xFF), botB = (colBot & 0xFF);
        
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buf.vertex(mat, x, y, 0).color(topR/255f, topG/255f, topB/255f, a/255f);
        buf.vertex(mat, x, y + h, 0).color(botR/255f, botG/255f, botB/255f, a/255f);
        buf.vertex(mat, x + w, y + h, 0).color(botR/255f, botG/255f, botB/255f, a/255f);
        buf.vertex(mat, x + w, y, 0).color(topR/255f, topG/255f, topB/255f, a/255f);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }

    private void drawCircle(DrawContext ctx, float cx, float cy, float r, Color color, int segments) {
        if (r <= 0.5f) return;
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        float cr = color.getRed() / 255f, cg = color.getGreen() / 255f, cb = color.getBlue() / 255f, ca = color.getAlpha() / 255f;
        int seg = Math.max(12, segments);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        buf.vertex(mat, cx, cy, 0).color(cr, cg, cb, ca);
        for (int i = 0; i <= seg; i++) {
            float a = (float)(Math.PI * 2.0) * (i / (float)seg);
            buf.vertex(mat, cx + (float)Math.cos(a) * r, cy + (float)Math.sin(a) * r, 0).color(cr, cg, cb, ca);
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }
    
    
    private void drawGradientAlpha(DrawContext ctx, int x, int y, int w, int h, int r, int g, int b, float alpha, int radius) {
        int a = (int)(255 * alpha);
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buf.vertex(mat, x, y, 0).color(r/255f, g/255f, b/255f, 0);
        buf.vertex(mat, x, y + h, 0).color(r/255f, g/255f, b/255f, a/255f);
        buf.vertex(mat, x + w, y + h, 0).color(r/255f, g/255f, b/255f, a/255f);
        buf.vertex(mat, x + w, y, 0).color(r/255f, g/255f, b/255f, 0);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }
    
    
    private void drawSolidRect(DrawContext ctx, int x, int y, int w, int h, int r, int g, int b, int a) {
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buf.vertex(mat, x, y, 0).color(r/255f, g/255f, b/255f, a/255f);
        buf.vertex(mat, x, y + h, 0).color(r/255f, g/255f, b/255f, a/255f);
        buf.vertex(mat, x + w, y + h, 0).color(r/255f, g/255f, b/255f, a/255f);
        buf.vertex(mat, x + w, y, 0).color(r/255f, g/255f, b/255f, a/255f);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }
    
    
    private void drawGradientH(DrawContext ctx, int x, int y, int w, int h,
                               int r1, int g1, int b1, int a1,
                               int r2, int g2, int b2, int a2) {
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        
        buf.vertex(mat, x, y, 0).color(r1/255f, g1/255f, b1/255f, a1/255f);
        buf.vertex(mat, x, y + h, 0).color(r1/255f, g1/255f, b1/255f, a1/255f);
        
        buf.vertex(mat, x + w, y + h, 0).color(r2/255f, g2/255f, b2/255f, a2/255f);
        buf.vertex(mat, x + w, y, 0).color(r2/255f, g2/255f, b2/255f, a2/255f);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }
    
    
    private void drawGradientV(DrawContext ctx, int x, int y, int w, int h,
                               int r1, int g1, int b1, int a1,
                               int r2, int g2, int b2, int a2) {
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        
        buf.vertex(mat, x, y, 0).color(r1/255f, g1/255f, b1/255f, a1/255f);
        buf.vertex(mat, x, y + h, 0).color(r2/255f, g2/255f, b2/255f, a2/255f);
        
        buf.vertex(mat, x + w, y + h, 0).color(r2/255f, g2/255f, b2/255f, a2/255f);
        buf.vertex(mat, x + w, y, 0).color(r1/255f, g1/255f, b1/255f, a1/255f);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }

    private void renderHueBarVertical(DrawContext ctx, int x, int y, int w, int h, HSVColorPicker picker, float alpha) {
        
        int[] hueColors = {
                0xFF000000 | HSVColorPicker.hsvToRgb(0f, 1f, 1f),
                0xFF000000 | HSVColorPicker.hsvToRgb(60f, 1f, 1f),
                0xFF000000 | HSVColorPicker.hsvToRgb(120f, 1f, 1f),
                0xFF000000 | HSVColorPicker.hsvToRgb(180f, 1f, 1f),
                0xFF000000 | HSVColorPicker.hsvToRgb(240f, 1f, 1f),
                0xFF000000 | HSVColorPicker.hsvToRgb(300f, 1f, 1f),
                0xFF000000 | HSVColorPicker.hsvToRgb(360f, 1f, 1f)
        };

        float segH = h / 6f;
        for (int i = 0; i < 6; i++) {
            int y0 = y + Math.round(i * segH);
            int y1 = y + Math.round((i + 1) * segH);
            int top = wa(hueColors[i], alpha);
            int bot = wa(hueColors[i + 1], alpha);
            gradient4(ctx, x, y0, w, Math.max(1, y1 - y0), top, top, bot, bot);
        }

        
        int hy = (int) (y + (1f - picker.getHue() / 360f) * h);
        ctx.fill(x - 3, hy - 4, x + w + 3, hy + 5, wa(0x70000000, alpha * 0.55f));
        ctx.fill(x - 2, hy - 3, x + w + 2, hy + 4, wa(0xFFFFFFFF, alpha));
        ctx.fill(x - 1, hy - 2, x + w + 1, hy + 3, wa(0xFF0B0B10, alpha));
    }

    private void renderAlphaBar(DrawContext ctx, int x, int y, int w, int h, float opacity, int rgb, float alpha) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;

        int left = ((int)(0 * alpha) << 24) | (r << 16) | (g << 8) | b;
        int right = ((int)(255 * alpha) << 24) | (r << 16) | (g << 8) | b;
        gradientH(ctx, x, y, w, h, left, right);

        int ax = x + (int)(opacity * (w - 1));
        ctx.fill(ax - 3, y - 3, ax + 4, y + h + 3, wa(0x70000000, alpha * 0.65f));
        ctx.fill(ax - 2, y - 2, ax + 3, y + h + 2, wa(0xFFFFFFFF, alpha));
        ctx.fill(ax - 1, y - 1, ax + 2, y + h + 1, wa(0xFF0B0B10, alpha));
    }

    @Override
    public void close() {
        restoreGuiScale(MinecraftClient.getInstance());
        if (modules != null) {
            for (ConfigCategoryImpl m : modules) {
                if (m != null) {
                    m.purgeCache();
                }
            }
        }
        System.gc();
        super.close();
    }

    @Override
    public void removed() {
        restoreGuiScale(client);
        super.removed();
    }

    public ClothConfigScreen(Text title) {
        super(title);
        manager = HudConfigInit.getManager();
        if (manager != null)
            modules.addAll(manager.getModules());
        int[] pos = PersistenceHelper.loadGuiPosition();
        panX = (pos != null && pos.length >= 2) ? Math.max(0, pos[0]) : 20;
        panY = (pos != null && pos.length >= 2) ? Math.max(0, pos[1]) : 20;
        
        activeTab = lastActiveTab;
    }

    @Override public void renderBackground(DrawContext ctx, int mx, int my, float delta) {}

    
    
    private List<ConfigCategoryImpl> visibleModules() {
        List<ConfigCategoryImpl> out = new ArrayList<>();
        for (ConfigCategoryImpl m : modules) {
            if (m == null || !m.isGuiVisible()) continue;
            if (matchesTab(m.getCategory(), activeTab)) {
                out.add(m);
            }
        }
        return out;
    }

    private static boolean matchesTab(ConfigCategoryImpl.Cat cat, int tab) {
        return switch (tab) {
            case 0 -> cat == ConfigCategoryImpl.Cat.COMBAT;
            case 1 -> cat == ConfigCategoryImpl.Cat.VISUALS
                    || cat == ConfigCategoryImpl.Cat.M
                    || cat == ConfigCategoryImpl.Cat.RENDER;
            case 2 -> cat == ConfigCategoryImpl.Cat.MOVEMENT
                    || cat == ConfigCategoryImpl.Cat.PLAYER;
            case 3 -> cat == ConfigCategoryImpl.Cat.MISC
                    || cat == ConfigCategoryImpl.Cat.OTHER;
            default -> false;
        };
    }

    

    private static float eo(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float u = 1f - t;
        return 1f - u * u * u;
    }

    private static float ap(float v, float t, float s, float dt) {
        float k = 1f - (float) Math.exp(-s * dt);
        float r = v + (t - v) * k;
        return Math.abs(t - r) < 0.003f ? t : r;
    }

    

    private void tick(float dt) {
        
        
        float openSpeed = openTarget == 1f ? 4f : 8f;
        openAnim = ap(openAnim, openTarget, openSpeed, dt);
        if (bindCaptureDelay > 0) {
            bindCaptureDelay--;
        }

        
        
        
        
        if (tabAlphaDir == -1) {
            tabAlpha -= dt * 8f; 
            if (tabAlpha <= 0f) {
                tabAlpha   = 0f;
                activeTab  = pendingTab;
                lastActiveTab = pendingTab; 
                pendingTab = -1;
                tabAlphaDir = 1;
                
                expanded = null;
            }
        } else {
            tabAlpha = Math.min(1f, tabAlpha + dt * 8f); 
        }

        
        if (expanded != lastExpanded) {
            if (lastExpanded != null && lastExpanded.getSettings() != null)
                for (AbstractFieldBuilder s : lastExpanded.getSettings())
                    if (s != null) rowAnim.put(s, 0f);
            lastExpanded = expanded;
            if (expanded != null && expanded.getSettings() != null)
                for (AbstractFieldBuilder s : expanded.getSettings())
                    if (s != null) rowAnim.put(s, 0f);
        }

        for (ConfigCategoryImpl m : modules) {
            if (m == null) continue;

            enAnim.put(m, ap(enAnim.getOrDefault(m, m.isEnabled() ? 1f : 0f), m.isEnabled() ? 1f : 0f, 6f, dt));
            exAnim.put(m, ap(exAnim.getOrDefault(m, m == expanded ? 1f : 0f), m == expanded ? 1f : 0f, 7f, dt));
            prAnim.put(m, ap(prAnim.getOrDefault(m, 0f), 0f, 12f, dt));
            hvAnim.put(m, ap(hvAnim.getOrDefault(m, 0f), 0f, 8f, dt));

            if (m.getSettings() == null) continue;

            if (m == expanded) {
                float curExp = exAnim.getOrDefault(m, 0f);
                
                for (AbstractFieldBuilder s : orderedSettings(m)) {
                    if (s == null || !s.isVisible()) continue;
                    float cur = rowAnim.getOrDefault(s, 0f);
                    
                    float target = curExp > 0.5f ? 1f : 0f;
                    rowAnim.put(s, ap(cur, target, 8f, dt));
                }
            }

            for (AbstractFieldBuilder s : m.getSettings()) {
                if (s == null) continue;
                prAmS.put(s, ap(prAmS.getOrDefault(s, 0f), 0f, 12f, dt));
                if (s instanceof BooleanToggleBuilder b) {
                    float tT = b.get() ? 1f : 0f;
                    tgAnim.put(s, ap(tgAnim.getOrDefault(s, tT), tT, 10f, dt));
                }
                if (s instanceof DoubleFieldBuilder d) {
                    double rng  = d.getMax() - d.getMin();
                    float  fill = rng > 0 ? (float)((d.get() - d.getMin()) / rng) : 0f;
                    slAnim.put(s, ap(slAnim.getOrDefault(s, fill), fill, 10f, dt));
                }
                if (s instanceof EnumSelectorBuilder)
                    enAmS.put(s, ap(enAmS.getOrDefault(s, 0f), 0f, 6f, dt));
            }
        }

        for (int t = 0; t < TABS.length; t++) {
            tabScroll[t] = ap(tabScroll[t], tabScrollT[t], 8f, dt);
            if (Math.abs(tabScrollT[t] - tabScroll[t]) < 0.3f) tabScroll[t] = tabScrollT[t];
        }
    }

    

    private static int wa(int c, float a) {
        a = Math.max(0f, Math.min(1f, a));
        return ((int)(((c >>> 24) & 0xFF) * a) << 24) | (c & 0x00FFFFFF);
    }
    private static int argb(int r, int g, int b, int a) { return (a << 24) | (r << 16) | (g << 8) | b; }
    private static float lerpF(float a, float b, float t) {
        return a + (b - a) * MathHelper.clamp(t, 0f, 1f);
    }
    private static int lerp(int c0, int c1, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int)((c0 >> 24 & 255) + ((c1 >> 24 & 255) - (c0 >> 24 & 255)) * t);
        int r = (int)((c0 >> 16 & 255) + ((c1 >> 16 & 255) - (c0 >> 16 & 255)) * t);
        int g = (int)((c0 >>  8 & 255) + ((c1 >>  8 & 255) - (c0 >>  8 & 255)) * t);
        int b = (int)((c0       & 255) + ((c1       & 255) - (c0       & 255)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (manager == null || Config_StringList.isDestroyed()) { close(); return; }
        super.render(ctx, mouseX, mouseY, delta);
        curMx = mouseX; curMy = mouseY;

        
        
        long  nowNano = System.nanoTime();
        frameDt       = lastFrameNano == 0L ? 0f
                      : Math.min(0.05f, (nowNano - lastFrameNano) / 1_000_000_000f);
        lastFrameNano = nowNano;
        tick(frameDt);
        caretTick++;

        List<ConfigCategoryImpl> vis = visibleModules();
        int cH    = contentH(vis);
        
        int expandedMinH = 0;
        if (expanded != null) {
            expandedMinH = MOD_H + expandedH(expanded);
        }
        int maxVH = Math.max(80, height - MARGIN * 2);
        
        int minViewH = Math.max(expandedMinH, 80);
        
        int viewH = Math.max(minViewH, Math.min(cH, maxVH - HDR_H - TAB_H));
        int panH  = HDR_H + TAB_H + viewH;
        tabScrollT[activeTab] = clampF(tabScrollT[activeTab], 0f, Math.max(0f, cH - viewH));
        panX = clamp(panX, MARGIN, width  - W - MARGIN);
        panY = clamp(panY, MARGIN, height - panH - MARGIN);

        int   visH  = Math.max(1, (int)(panH * eo(openAnim)));
        float alpha = Math.min(1f, eo(openAnim) * 1.25f);

        if (openTarget == 0f && openAnim < 0.015f) {
            modules.forEach(m -> m.setBinding(false));
            if (manager != null) manager.saveKeybinds();
            PersistenceHelper.saveGuiPosition(panX, panY);
            close();
            return;
        }

        for (int i = 5; i >= 1; i--) {
            int sa = (int)(18 * alpha / i);
            ctx.fill(panX - i, panY + i, panX + W + i, panY + visH + i, sa << 24);
        }

        ctx.enableScissor(Math.max(0, panX), Math.max(0, panY), Math.min(width, panX + W), Math.min(height, panY + visH));

        ctx.fill(panX, panY,         panX + W, panY + panH, wa(C_BG,        alpha));
        ctx.fill(panX, panY,         panX + W, panY + 1,    wa(C_ACCENT,    alpha));
        ctx.fill(panX, panY + 1,     panX + W, panY + 2,    wa(C_ACCENT_DK, alpha));
        ctx.fill(panX, panY + 2,     panX + W, panY + HDR_H,wa(C_HDR,       alpha));
        int ty = panY + 2 + (HDR_H - 2 - client.textRenderer.fontHeight) / 2;
        ctx.drawText(client.textRenderer, me.shedaniel.clothconfig2.internal.BuildFingerprint.decrypt("1b2d3e383031362b3a7f"), panX + PX, ty, wa(C_TITLE, alpha), false);
        int tw = client.textRenderer.getWidth(me.shedaniel.clothconfig2.internal.BuildFingerprint.decrypt("1b2d3e383031362b3a7f"));
        ctx.drawText(client.textRenderer, "Client", panX + PX + tw, ty, wa(C_ACCENT_HI, alpha), false);
        ctx.fill(panX, panY + HDR_H, panX + W, panY + HDR_H + 1, wa(0x18FFFFFF, alpha));

        renderTabs(ctx, panX, panY + HDR_H, alpha);

        int ctop = panY + HDR_H + TAB_H;
        ctx.enableScissor(Math.max(0, panX), Math.max(0, ctop), Math.min(width, panX + W), Math.min(height, panY + visH));

        
        float listAlpha = alpha * tabAlpha;

        int ry = ctop - (int)tabScroll[activeTab];
        descHoverMod = null;
        for (ConfigCategoryImpl m : vis) {
            if (m == null) continue;
            boolean hov = hit(curMx, curMy, panX, ry, W, MOD_H) && curMy >= ctop;
            if (hov) descHoverMod = m;
            hvAnim.put(m, ap(hvAnim.getOrDefault(m, 0f), hov ? 1f : 0f, 9f, frameDt));
            ry = renderModule(ctx, panX, ry, ctop, m, listAlpha);
        }
        ctx.disableScissor();

        if (cH > viewH)
            scrollBar(ctx, panX + W - 3, ctop, viewH, cH, tabScroll[activeTab], listAlpha);

        ctx.disableScissor();

        if (openColorField != null) {
            PrestigeRenderHelper.bind(ctx);
            PrestigeColorPicker.State cp = PrestigeColorPicker.stateOf(openColorField);
            PrestigeColorPicker.renderFloatingPopup(ctx, colorPopupX, colorPopupY, alpha, cp,
                    curMx, curMy, frameDt);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        renderBottomDescription(ctx, alpha);

    }

    private void renderBottomDescription(DrawContext ctx, float alpha) {
        if (descHoverMod == null || alpha <= 0.15f) {
            return;
        }
        DescParts parts = parseDescription(descHoverMod);
        if (!parts.hasWarning() && (parts.description() == null || parts.description().isEmpty())) {
            return;
        }

        float scale = 1.15f;
        int maxTextW = (int) (Math.min(width - 48, 520) / scale);
        List<String> warnLines = new ArrayList<>();
        List<String> descLines = new ArrayList<>();
        if (parts.hasWarning()) {
            warnLines.addAll(wrapDescription(WIP_WARNING_PREFIX, maxTextW));
        }
        if (parts.description() != null && !parts.description().isEmpty()) {
            descLines.addAll(wrapDescription(parts.description(), maxTextW));
        }
        if (warnLines.isEmpty() && descLines.isEmpty()) {
            return;
        }

        int lineH = (int) ((client.textRenderer.fontHeight + 3) * scale);
        int blockH = (warnLines.size() + descLines.size()) * lineH;
        int bottomPad = Math.max(48, height / 10);
        int ty = height - bottomPad - blockH;
        int normalCol = wa(0xFFE8EEFF, alpha);
        int warnCol = wa(0xFFFF5555, alpha);

        for (String line : warnLines) {
            int tw = (int) (client.textRenderer.getWidth(line) * scale);
            int tx = (width - tw) / 2;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(tx, ty, 0);
            ctx.getMatrices().scale(scale, scale, 1f);
            ctx.drawText(client.textRenderer, line, 0, 0, warnCol, true);
            ctx.getMatrices().pop();
            ty += lineH;
        }
        for (String line : descLines) {
            int tw = (int) (client.textRenderer.getWidth(line) * scale);
            int tx = (width - tw) / 2;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(tx, ty, 0);
            ctx.getMatrices().scale(scale, scale, 1f);
            ctx.drawText(client.textRenderer, line, 0, 0, normalCol, true);
            ctx.getMatrices().pop();
            ty += lineH;
        }
    }

    private static final String WIP_WARNING_PREFIX =
            "THIS MODULE IS NOT FINISHED AND FLAGS A LOT OF ANTICHEATS.";

    private record DescParts(boolean hasWarning, String description) {}

    private DescParts parseDescription(ConfigCategoryImpl mod) {
        String body = mod.getDescription();
        if (body == null || body.isEmpty()) {
            return new DescParts(false, "");
        }
        body = body.trim();
        if (body.regionMatches(true, 0, WIP_WARNING_PREFIX, 0, WIP_WARNING_PREFIX.length())) {
            String rest = body.substring(WIP_WARNING_PREFIX.length()).trim();
            if (rest.startsWith(".")) {
                rest = rest.substring(1).trim();
            }
            return new DescParts(true, rest);
        }
        return new DescParts(false, body);
    }

    private String moduleDescriptionText(ConfigCategoryImpl mod) {
        return parseDescription(mod).description();
    }

    private List<String> wrapDescription(String text, int maxPx) {
        List<String> out = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (client.textRenderer.getWidth(candidate) > maxPx && !line.isEmpty()) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            out.add(line.toString());
        }
        return out;
    }

    private void renderTabs(DrawContext ctx, int x, int y, float alpha) {
        ctx.fill(x, y, x + W, y + TAB_H, wa(C_TAB_BG, alpha));
        ctx.fill(x, y + TAB_H - 1, x + W, y + TAB_H, wa(0x14FFFFFF, alpha));
        int totalLabelW = 0;
        int[] lw = new int[TABS.length];
        for (int t = 0; t < TABS.length; t++) { lw[t] = client.textRenderer.getWidth(TABS[t]); totalLabelW += lw[t]; }
        int spacing = Math.max(4, (W - totalLabelW - PX * 2) / (TABS.length + 1));
        int tx = x + PX;
        for (int t = 0; t < TABS.length; t++) {
            int tabW  = lw[t] + spacing;
            boolean sel = t == activeTab;
            boolean hov = hit(curMx, curMy, tx, y, tabW, TAB_H);
            int textX = tx + (tabW - lw[t]) / 2;
            int textY = y + (TAB_H - client.textRenderer.fontHeight) / 2;
            int col   = sel ? C_ACCENT_HI : (hov ? 0xFF7A84B0 : 0xFF3A4060);
            ctx.drawText(client.textRenderer, TABS[t], textX, textY, wa(col, alpha), false);
            if (sel) ctx.fill(textX - 1, y + TAB_H - 2, textX + lw[t] + 1, y + TAB_H - 1, wa(C_ACCENT, alpha));
            tx += tabW;
        }
    }

    
    private int renderModule(DrawContext ctx, int x, int y, int ctop,
                              ConfigCategoryImpl mod, float alpha) {
        float en  = enAnim.getOrDefault(mod, mod.isEnabled() ? 1f : 0f);
        float hp  = hvAnim.getOrDefault(mod, 0f);
        float pp  = prAnim.getOrDefault(mod, 0f);
        float exp = exAnim.getOrDefault(mod, 0f);

        float pressAlpha = alpha * (1f - pp * 0.06f);

        float ba = Math.max(hp * 0.06f, exp > 0.01f ? 0.05f : 0f);
        if (ba > 0.003f) ctx.fill(x, y, x + W, y + MOD_H, wa(0xFFFFFFFF, ba * pressAlpha));

        String chev    = exp > 0.5f ? "\u25BE" : "\u25B8";
        int    chevCol = lerp(0xFF252840, C_ACCENT_HI, exp);
        int    chevY   = y + (MOD_H - client.textRenderer.fontHeight) / 2;
        ctx.drawText(client.textRenderer, chev, x + 5, chevY, wa(chevCol, pressAlpha), false);
        ctx.drawText(client.textRenderer, mod.getName(),
                x + 16, y + (MOD_H - client.textRenderer.fontHeight) / 2,
                wa(lerp(C_MOD_OFF, C_MOD_ON, en), pressAlpha), false);

        int tX = x + W - PX - TGL_W, tY = y + (MOD_H - TGL_H) / 2;
        drawToggle(ctx, tX, tY, null, mod.isEnabled(), en,
                hit(curMx, curMy, tX - 3, tY - 3, TGL_W + 6, TGL_H + 6), pp, pressAlpha);

        ctx.fill(x + 2, y + MOD_H - 1, x + W - 2, y + MOD_H, wa(C_SEP, alpha));

        int nextY = y + MOD_H;
        if (exp > 0.005f) {
            int fullH = expandedH(mod);
            int drawH = (int)(fullH * eo(exp));

            ctx.fill(x + 2, nextY, x + W, nextY + drawH, wa(C_SET_TINT, alpha));
            ctx.fill(x,     nextY, x + 1, nextY + drawH, wa(wa(C_ACCENT, 0.18f), alpha));
            
            ctx.enableScissor(Math.max(0, x), Math.max(0, nextY), Math.min(width, x + W), Math.min(height, nextY + drawH));

            int sy = nextY + 5;
            
            for (AbstractFieldBuilder s : orderedSettings(mod)) {
                if (s == null || !s.isVisible()) continue;
                sliderBaseX = x; 
                sy = renderSetting(ctx, x, sy, s, alpha);
            }

            
            int kbTop = 5 + PY;
            for (AbstractFieldBuilder ks : orderedSettings(mod))
                if (ks != null && ks.isVisible()) kbTop += entryH(ks);
            
            kbTop += SET_H;
            float kbAlpha = drawH >= kbTop
                    ? alpha * eo(Math.min(1f, (drawH - (kbTop - SET_H)) / 40f))
                    : 0f;

            String kbl = "Bind: " + keyLabel(mod);
            ctx.drawText(client.textRenderer, kbl, x + IND, sy + PY,
                    wa(mod.isBinding() ? 0xFFFFD050 : C_KEY, kbAlpha), false);

            ctx.disableScissor();
            ctx.fill(x + 2, nextY + drawH, x + W - 2, nextY + drawH + 1, wa(0x12FFFFFF, alpha));
            nextY += drawH;
        }
        return nextY;
    }

    private int renderSetting(DrawContext ctx, int bx, int sy,
                               AbstractFieldBuilder s, float alpha) {
        float ra     = eo(rowAnim.getOrDefault(s, 0f));
        float rAlpha = alpha * ra;
        int   drawY  = sy;

        float pp = prAmS.getOrDefault(s, 0f);
        boolean rowHov = curMx >= bx + IND && curMx < bx + W - PX
                      && curMy >= sy && curMy < sy + SET_H;
        int lblCol = rowHov ? lerp(C_LBL, 0xFF6070A0, 0.5f) : lerp(C_LBL, C_VAL, pp * 0.3f);

        String sn = s.getName() != null ? s.getName() : "";

        if (s instanceof RangeSliderBuilder r) {
            String val = (int)r.getMinVal() + " - " + (int)r.getMaxVal() + r.getSuffix();
            ctx.drawText(client.textRenderer, s.getName(), bx + IND, drawY + PY, wa(lblCol, rAlpha), false);
            ctx.drawText(client.textRenderer, val, bx + W - PX - client.textRenderer.getWidth(val), drawY + PY, wa(C_VAL, rAlpha), false);
            int slX = bx + IND, slW = W - IND - PX, slY = drawY + SET_H + SL_GAP;
            renderRangeSlider(ctx, slX, slY, slW, r, hit(curMx, curMy, slX, slY - 5, slW, SL_H + 10), rangeDrag == r, rAlpha);
            return sy + SET_H + SL_GAP + SL_H + 6;

        } else if (s instanceof DoubleFieldBuilder d) {
            String val = fmtD(d);
            ctx.drawText(client.textRenderer, s.getName(), bx + IND, drawY + PY, wa(lblCol, rAlpha), false);
            ctx.drawText(client.textRenderer, val, bx + W - PX - client.textRenderer.getWidth(val), drawY + PY, wa(C_VAL, rAlpha), false);
            int slX = bx + IND, slW = W - IND - PX, slY = drawY + SET_H + SL_GAP;
            renderSlider(ctx, slX, slY, slW, d, hit(curMx, curMy, slX, slY - 5, slW, SL_H + 10), sliderDrag == d, rAlpha);
            return sy + SET_H + SL_GAP + SL_H + 6;

        } else if (s instanceof StringFieldBuilder st) {
            String val = st.get() == null ? "" : st.get();
            int off = client.textRenderer.getWidth(st.getName() + ": ") + 2;
            int bX  = bx + IND + off, bW = W - (bX - bx) - PX, bHh = SET_H - 4, bY = drawY + 2;
            boolean fc = textFocused && activeText == st;
            
            
            int bg = fc ? 0xFF12121a : 0xFF0e0e16;
            ctx.fill(bX - 2, bY, bX + bW + 2, bY + bHh, wa(bg, rAlpha));
            
            
            int border = fc ? C_ACCENT : (rowHov ? 0xFF3a3a5a : 0xFF202030);
            ctx.fill(bX - 2, bY, bX + bW + 2, bY + 1, wa(border, rAlpha));
            ctx.fill(bX - 2, bY + bHh - 1, bX + bW + 2, bY + bHh, wa(border, rAlpha));
            ctx.fill(bX - 2, bY, bX - 1, bY + bHh, wa(border, rAlpha));
            ctx.fill(bX + bW + 1, bY, bX + bW + 2, bY + bHh, wa(border, rAlpha));
            
            
            String rn = val;
            while (client.textRenderer.getWidth(rn) > bW - 6 && !rn.isEmpty()) rn = rn.substring(1);
            int textCol = val.isEmpty() ? 0xFF555578 : C_TITLE;
            ctx.drawText(client.textRenderer, rn, bX + 4, bY + (bHh - 8) / 2, wa(textCol, rAlpha), false);
            
            
            if (fc && (caretTick / 6) % 2 == 0) {
                int cx2 = bX + 4 + client.textRenderer.getWidth(rn);
                ctx.fill(cx2, bY + 3, cx2 + 1, bY + bHh - 3, wa(0xFFFFFFFF, rAlpha));
            }
            return sy + SET_H;

        } else if (s instanceof BooleanToggleBuilder b) {
            ctx.drawText(client.textRenderer, s.getName() + ":", bx + IND, drawY + PY, wa(lblCol, rAlpha), false);
            float en = tgAnim.getOrDefault(s, b.get() ? 1f : 0f);
            int tX = bx + W - PX - TGL_W, tY = drawY + (SET_H - TGL_H) / 2;
            drawToggle(ctx, tX, tY, s, b.get(), en, hit(curMx, curMy, tX - 3, tY - 3, TGL_W + 6, TGL_H + 6), pp, rAlpha);
            return sy + SET_H;

        } else if (s instanceof EnumSelectorBuilder e) {
            ctx.drawText(client.textRenderer, s.getName() + ":", bx + IND, drawY + PY, wa(lblCol, rAlpha), false);
            float flash = enAmS.getOrDefault(s, 0f);
            String cur = e.get() != null ? e.get().toString() : "";
            String lA = "\u25C2 ", rA = " \u25B8";
            int arC = lerp(C_ARROW_DIM, C_ACCENT, flash), vC = lerp(C_VAL, C_ACCENT_HI, flash);
            int ex  = bx + W - PX - client.textRenderer.getWidth(lA + cur + rA);
            ctx.drawText(client.textRenderer, lA,  ex,                                          drawY + PY, wa(arC, rAlpha), false);
            ctx.drawText(client.textRenderer, cur, ex + client.textRenderer.getWidth(lA),       drawY + PY, wa(vC,  rAlpha), false);
            ctx.drawText(client.textRenderer, rA,  ex + client.textRenderer.getWidth(lA + cur), drawY + PY, wa(arC, rAlpha), false);
            return sy + SET_H;


        } else if (s instanceof ColorFieldBuilder c) {
            ctx.drawText(client.textRenderer, s.getName() + ":", bx + IND, drawY + PY, wa(lblCol, rAlpha), false);

            int swX = bx + W - PX - PrestigeColorPicker.SWATCH_SIZE;
            int swY = drawY + (SET_H - PrestigeColorPicker.SWATCH_SIZE) / 2;
            PrestigeColorPicker.renderSquareSwatch(ctx, swX, swY, c, rAlpha);
            return sy + SET_H;

        } else if (s instanceof FriendListFieldBuilder fl) {
            ctx.drawText(client.textRenderer, fl.getName() + ":", bx + IND, drawY + PY, wa(lblCol, rAlpha), false);
            int rowY = drawY + SET_H;
            for (String name : fl.getNames()) {
                ctx.drawText(client.textRenderer, name, bx + IND + 4, rowY + PY, wa(C_VAL, rAlpha), false);
                String xLabel = "x";
                int xW = client.textRenderer.getWidth(xLabel) + 8;
                int xX = bx + W - PX - xW;
                boolean hov = hit(curMx, curMy, xX, rowY, xW, SET_H);
                int xCol = hov ? 0xFFFF6060 : 0xFFCC4444;
                ctx.drawText(client.textRenderer, xLabel, xX + 4, rowY + PY, wa(xCol, rAlpha), false);
                rowY += SET_H;
            }
            return sy + SET_H * (1 + fl.getNames().size());

        } else if (s instanceof ActionFieldBuilder a) {
            
            ctx.drawText(client.textRenderer, s.getName() + ":", bx + IND, drawY + PY, wa(lblCol, rAlpha), false);
            
            String name = a.getName();
            int textW = client.textRenderer.getWidth(name) + 10;
            int btnW = Math.max(textW, 40);
            int btnH = TGL_H; 
            int btnX = bx + W - PX - btnW;
            int btnY = drawY + (SET_H - btnH) / 2;
            boolean hov = hit(curMx, curMy, btnX - 2, btnY - 2, btnW + 4, btnH + 4);
            
            
            int bg = hov ? C_ACCENT_HI : C_ACCENT;
            ctx.fill(btnX + 2, btnY, btnX + btnW - 2, btnY + btnH, wa(bg, rAlpha));
            ctx.fill(btnX, btnY + 2, btnX + btnW, btnY + btnH - 2, wa(bg, rAlpha));
            ctx.fill(btnX + 1, btnY + 1, btnX + btnW - 1, btnY + btnH - 1, wa(bg, rAlpha));
            
            
            int textX = btnX + btnW / 2 - client.textRenderer.getWidth(name) / 2;
            int textY = btnY + 2;
            ctx.drawText(client.textRenderer, name, textX, textY, wa(0xFFFFFFFF, rAlpha), false);
            return sy + SET_H;

        } else {
            String disp = display(s);
            ctx.drawText(client.textRenderer, s.getName() + ":", bx + IND, drawY + PY, wa(lblCol, rAlpha), false);
            if (!disp.isEmpty())
                ctx.drawText(client.textRenderer, disp, bx + W - PX - client.textRenderer.getWidth(disp), drawY + PY, wa(C_VAL, rAlpha), false);
            return sy + SET_H;
        }
    }

    private void drawToggle(DrawContext ctx, int x, int y, AbstractFieldBuilder s,
                             boolean on, float p, boolean hov, float press, float alpha) {
        float pA = alpha * (1f - press * 0.08f);

        if (p > 0.3f) {
            float glowA = (p - 0.3f) / 0.7f;
            ctx.fill(x - 1, y - 1, x + TGL_W + 1, y + TGL_H + 1,
                    wa(argb(88, 101, 242, (int)(glowA * 35)), pA));
        }

        int bg = lerp(C_TGL_OFF, C_ACCENT, p);
        if (hov) bg = lerp(bg, 0xFF6875F5, 0.18f);
        ctx.fill(x + 1, y, x + TGL_W - 1, y + TGL_H, wa(bg, pA));
        ctx.fill(x, y + 1, x + TGL_W, y + TGL_H - 1, wa(bg, pA));

        int ks = TGL_H - 4;
        int kx = x + (int)(2 + (TGL_W - ks - 4) * p);
        int ky = y + 2;
        int kc = on ? 0xFFFFFFFF : lerp(C_TGL_KNOB, 0xFFB0B8E0, p);
        ctx.fill(kx + 1, ky, kx + ks - 1, ky + ks, wa(kc, pA));
        ctx.fill(kx, ky + 1, kx + ks, ky + ks - 1, wa(kc, pA));
        ctx.fill(kx + 2, ky + 1, kx + ks - 2, ky + 2, wa(argb(255, 255, 255, (int)(40 + p * 55)), pA));
    }

    private void renderSlider(DrawContext ctx, int x, int y, int w,
                               DoubleFieldBuilder d, boolean hov, boolean drag, float alpha) {
        ctx.fill(x, y, x + w, y + SL_H, wa(C_SL_TRK, alpha));
        ctx.fill(x, y, x + w, y + 1,    wa(0x10FFFFFF, alpha));
        float pct = slAnim.getOrDefault(d, 0f);
        int   f   = (int)Math.max(2, Math.min(w - 2, pct * (w - 2)));
        int   fc  = drag ? C_ACCENT_HI : (hov ? lerp(C_ACCENT, C_ACCENT_HI, 0.35f) : C_ACCENT);
        ctx.fill(x, y, x + f, y + SL_H, wa(fc, alpha));
        ctx.fill(x, y, x + f, y + 1,    wa(0x30FFFFFF, alpha));
        int hx = x + f - 1;
        if (drag) ctx.fill(hx - 2, y - 2, hx + 4, y + SL_H + 2, wa(argb(88, 101, 242, 50), alpha));
        ctx.fill(hx, y - 1, hx + 3, y + SL_H + 1, wa(drag ? 0xFFFFFFFF : 0xFFD5DCFF, alpha));
    }

    
    private void renderRangeSlider(DrawContext ctx, int x, int y, int w,
                                    RangeSliderBuilder r, boolean hov, boolean drag, float alpha) {
        
        ctx.fill(x, y, x + w, y + SL_H, wa(C_SL_TRK, alpha));
        ctx.fill(x, y, x + w, y + 1, wa(0x10FFFFFF, alpha));
        
        
        double minPct = (r.getMinVal() - r.getMin()) / (r.getMax() - r.getMin());
        double maxPct = (r.getMaxVal() - r.getMin()) / (r.getMax() - r.getMin());
        int minX = x + (int)(minPct * w);
        int maxX = x + (int)(maxPct * w);
        
        
        int fc = drag ? C_ACCENT_HI : (hov ? lerp(C_ACCENT, C_ACCENT_HI, 0.35f) : C_ACCENT);
        ctx.fill(minX, y, maxX, y + SL_H, wa(fc, alpha));
        ctx.fill(minX, y, maxX, y + 1, wa(0x30FFFFFF, alpha));
        
        
        boolean dragMin = drag && rangeDragMin;
        int minHx = minX - 1; 
        if (dragMin) ctx.fill(minHx - 2, y - 2, minHx + 4, y + SL_H + 2, wa(argb(88, 101, 242, 50), alpha));
        ctx.fill(minHx, y - 1, minHx + 3, y + SL_H + 1, wa(dragMin ? 0xFFFFFFFF : 0xFFD5DCFF, alpha));
        
        
        boolean dragMax = drag && !rangeDragMin;
        int maxHx = maxX - 1; 
        if (dragMax) ctx.fill(maxHx - 2, y - 2, maxHx + 4, y + SL_H + 2, wa(argb(88, 101, 242, 50), alpha));
        ctx.fill(maxHx, y - 1, maxHx + 3, y + SL_H + 1, wa(dragMax ? 0xFFFFFFFF : 0xFFD5DCFF, alpha));
    }

    private void gradientH(DrawContext ctx, int x, int y, int w, int h, int left, int right) {
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        float lr = ((left >> 16) & 0xFF) / 255f;
        float lg = ((left >> 8) & 0xFF) / 255f;
        float lb = (left & 0xFF) / 255f;
        float la = ((left >> 24) & 0xFF) / 255f;
        float rr = ((right >> 16) & 0xFF) / 255f;
        float rg = ((right >> 8) & 0xFF) / 255f;
        float rb = (right & 0xFF) / 255f;
        float ra = ((right >> 24) & 0xFF) / 255f;
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buf.vertex(mat, x,     y,     0).color(lr, lg, lb, la);
        buf.vertex(mat, x,     y + h, 0).color(lr, lg, lb, la);
        buf.vertex(mat, x + w, y + h, 0).color(rr, rg, rb, ra);
        buf.vertex(mat, x + w, y,     0).color(rr, rg, rb, ra);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void gradient4(DrawContext ctx, int x, int y, int w, int h, int tl, int tr, int br, int bl) {
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        float tlr = ((tl >> 16) & 0xFF) / 255f, tlg = ((tl >> 8) & 0xFF) / 255f, tlb = (tl & 0xFF) / 255f, tla = ((tl >> 24) & 0xFF) / 255f;
        float trr = ((tr >> 16) & 0xFF) / 255f, trg = ((tr >> 8) & 0xFF) / 255f, trb = (tr & 0xFF) / 255f, tra = ((tr >> 24) & 0xFF) / 255f;
        float brr = ((br >> 16) & 0xFF) / 255f, brg = ((br >> 8) & 0xFF) / 255f, brb = (br & 0xFF) / 255f, bra = ((br >> 24) & 0xFF) / 255f;
        float blr = ((bl >> 16) & 0xFF) / 255f, blg = ((bl >> 8) & 0xFF) / 255f, blb = (bl & 0xFF) / 255f, bla = ((bl >> 24) & 0xFF) / 255f;

        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buf.vertex(mat, x,     y,     0).color(tlr, tlg, tlb, tla);
        buf.vertex(mat, x,     y + h, 0).color(blr, blg, blb, bla);
        buf.vertex(mat, x + w, y + h, 0).color(brr, brg, brb, bra);
        buf.vertex(mat, x + w, y,     0).color(trr, trg, trb, tra);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void renderSVSquare(DrawContext ctx, int x, int y, int w, int h, HSVColorPicker picker, float alpha) {
        float hue = picker.getHue();
        int pure = 0xFF000000 | HSVColorPicker.hsvToRgb(hue, 1f, 1f);

        
        gradient4(ctx, x, y, w, h,
                wa(0xFFFFFFFF, alpha),
                wa(pure, alpha),
                wa(pure, alpha),
                wa(0xFFFFFFFF, alpha));
        gradient4(ctx, x, y, w, h,
                wa(0x00000000, alpha),
                wa(0x00000000, alpha),
                wa(0xFF000000, alpha),
                wa(0xFF000000, alpha));
    }

    private void scrollBar(DrawContext ctx, int x, int y, int vH, int cH, float sc, float alpha) {
        int   bH = Math.max(14, (int)((float)vH / cH * vH));
        float ms = cH - vH;
        int   bY = ms > 0 ? y + (int)(sc / ms * (vH - bH)) : y;
        ctx.fill(x, bY + 1, x + 2, bY + bH - 1, wa(0x14FFFFFF, alpha));
        ctx.fill(x, bY + 2, x + 1, bY + bH - 2, wa(0x50FFFFFF, alpha));
    }

    

    private int entryH(AbstractFieldBuilder s) {
        if (s instanceof FriendListFieldBuilder fl) {
            return SET_H * (1 + fl.getNames().size());
        }
        if (s instanceof DoubleFieldBuilder || s instanceof RangeSliderBuilder) return SET_H + SL_GAP + SL_H + 6;
        return SET_H;
    }
    private int expandedH(ConfigCategoryImpl mod) {
        int h = 5;
        for (AbstractFieldBuilder s : orderedSettings(mod)) {
            if (s != null && s.isVisible()) {
                h += entryH(s);
            }
        }
        return h + SET_H + 3; 
    }
    private int contentH(List<ConfigCategoryImpl> vis) {
        int h = 0;
        for (ConfigCategoryImpl m : vis) {
            if (m == null) continue;
            h += MOD_H;
            float ex = exAnim.getOrDefault(m, m == expanded ? 1f : 0f);
            if (ex > 0.005f) h += (int)(expandedH(m) * eo(ex));
        }
        return h;
    }

    private List<AbstractFieldBuilder> orderedSettings(ConfigCategoryImpl mod) {
        List<AbstractFieldBuilder> raw = mod.getSettings();
        if (raw == null) {
            return List.of();
        }
        List<AbstractFieldBuilder> visible = new ArrayList<>();
        for (AbstractFieldBuilder s : raw) {
            if (s != null && s.isVisible()) {
                visible.add(s);
            }
        }
        return visible;
    }

    private String fmtD(DoubleFieldBuilder d) {
        double v = d.get();
        String nm = d.getName();
        if (nm != null && nm.equals("Re-enable Delay") && v >= 10.0) {
            return "Never";
        }
        String suf = d.getSuffix();
        if (suf.isEmpty() && nm != null) {
            if (nm.equals("Server Cooldown")) {
                suf = "%";
            } else if (nm.equals("Delay")) {
                suf = "ms";
            }
        }
        if (suf.isEmpty() && d.getDescription() != null) {
            String dc = d.getDescription().toLowerCase();
            if (dc.contains("millisecond")) {
                suf = "ms";
            } else if (dc.contains("%") || dc.contains("percent")) {
                suf = "%";
            }
        }
        double inc = d.getIncrement();
        String n;
        if (v == (long) v) {
            n = String.valueOf((long) v);
        } else if (inc >= 0.1) {
            n = String.format("%.1f", v);
        } else {
            n = String.format("%.2f", v);
        }
        return n + suf;
    }
    private String display(AbstractFieldBuilder s) {
        if (s instanceof DoubleFieldBuilder d)  return fmtD(d);
        if (s instanceof EnumSelectorBuilder e)  return e.get();
        if (s instanceof StringFieldBuilder st)  return st.get();
        return "";
    }
    private String keyLabel(ConfigCategoryImpl mod) {
        if (mod.isBinding()) return "[...]";
        int k = mod.getKeybind();
        if (k < 0 || k == GLFW.GLFW_KEY_UNKNOWN) return "None";
        if (k >= 0 && k <= 7) {
            String[] n = {
                "LMB",
                "RMB",
                "MMB",
                "Mouse4",
                "Mouse5",
                "Mouse6",
                "Mouse7",
                "Mouse8"
            };
            return n[k];
        }
        try { return InputUtil.fromKeyCode(k, 0).getLocalizedText().getString(); }
        catch (Exception e) { return "Key " + k; }
    }

    

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX, my = (int) mouseY;

        if (openColorField != null) {
            PrestigeColorPicker.State cp = PrestigeColorPicker.stateOf(openColorField);
            if (PrestigeColorPicker.hitPopup(colorPopupX, colorPopupY, mx, my, openColorField)) {
                if (PrestigeColorPicker.mouseClickedPopup(cp, colorPopupX, colorPopupY, mx, my, button)) {
                    return true;
                }
            } else if (button == 0) {
                openColorField = null;
            }
        }

        for (ConfigCategoryImpl m : modules) {
            if (m.isBinding()) {
                if (bindCaptureDelay > 0) {
                    return true;
                }
                m.setKeybind(button <= 7 ? button : -1);
                m.setBinding(false);
                if (manager != null) manager.saveKeybinds();
                return true;
            }
        }

        if (button == 0) {
            OverlayRenderer hud = manager != null ? manager.getModuleByClass(OverlayRenderer.class) : null;
            if (hud != null && hud.isEnabled() && hud.isEditPositionEnabled()) {
                float s = hud.getHudScale();
                int hx = (int) hud.getHudX();
                int hy = (int) hud.getHudY();
                int hw = (int) (hud.getHudWidth() * s);
                int hh = (int) (hud.getHudHeight() * s);
                if (hit(mx, my, hx, hy, hw, hh)) {
                    dragTargetHud = true;
                    dragTargetHudOX = mx - hx;
                    dragTargetHudOY = my - hy;
                    return true;
                }
            }
        }

        if (openTarget == 0f) return false;

        if (hit(mx, my, panX, panY, W, HDR_H)) {
            dragPanel = true;
            dragOX = mx - panX;
            dragOY = my - panY;
            return true;
        }

        int tabY = panY + HDR_H;
        if (hit(mx, my, panX, tabY, W, TAB_H)) {
            int totalLabelW = 0;
            int[] lw = new int[TABS.length];
            for (int t = 0; t < TABS.length; t++) {
                lw[t] = client.textRenderer.getWidth(TABS[t]);
                totalLabelW += lw[t];
            }
            int spacing = Math.max(4, (W - totalLabelW - PX * 2) / (TABS.length + 1));
            int tx = panX + PX;
            for (int t = 0; t < TABS.length; t++) {
                int tabW = lw[t] + spacing;
                if (mx >= tx && mx < tx + tabW) {
                    if (t != activeTab && pendingTab == -1) {
                        pendingTab = t;
                        tabAlphaDir = -1;
                    }
                    return true;
                }
                tx += tabW;
            }
            return true;
        }

        List<ConfigCategoryImpl> vis = visibleModules();
        int cH = contentH(vis);
        int maxVH = Math.max(80, height - MARGIN * 2);
        int viewH = Math.min(cH, maxVH - HDR_H - TAB_H);
        int ctop = panY + HDR_H + TAB_H;

        if (!hit(mx, my, panX, ctop, W, viewH)) return super.mouseClicked(mouseX, mouseY, button);

        int ry = ctop - (int) tabScroll[activeTab];
        int cbot = ctop + viewH;
        for (ConfigCategoryImpl mod : vis) {
            if (mod == null) continue;
            int headerY = Math.max(ry, ctop);
            int headerH = Math.min(ry + MOD_H, cbot) - headerY;
            if (headerH > 0 && hit(mx, my, panX, headerY, W, headerH)) {
                prAnim.put(mod, 1f);
                int tX = panX + W - PX - TGL_W;
                int tY = ry + (MOD_H - TGL_H) / 2;
                if (button == 0) {
                    mod.setEnabled(!mod.isEnabled());
                } else if (button == 1) {
                    expanded = (expanded == mod) ? null : mod;
                }
                return true;
            }
            ry += MOD_H;

            float ex = exAnim.getOrDefault(mod, 0f);
            if (ex > 0.08f) {
                int drawH = (int) (expandedH(mod) * eo(ex));
                int panelY = Math.max(ry, ctop);
                int panelH = Math.min(ry + drawH, cbot) - panelY;
                if (panelH > 0 && hit(mx, my, panX, panelY, W, panelH)) {
                    int sy = ry + 5;
                    for (AbstractFieldBuilder s : orderedSettings(mod)) {
                        if (s == null || !s.isVisible()) continue;
                        int eh = entryH(s);
                        if (hit(mx, my, panX, sy, W, eh)) {
                            prAmS.put(s, 1f);
                            sliderBaseX = panX; 
                            
                            if (s instanceof RangeSliderBuilder rs) {
                                int slX = panX + IND, slW = W - IND - PX;
                                int slY = sy + SET_H + SL_GAP;
                                if (hit(mx, my, slX, slY - 5, slW, SL_H + 10)) {
                                    rangeDrag = rs; applyRangeSlider(rs, mx); return true;
                                }
                            } else if (s instanceof DoubleFieldBuilder d) {
                                int slX = panX + IND, slW = W - IND - PX;
                                int slY = sy + SET_H + SL_GAP;
                                if (hit(mx, my, slX, slY - 5, slW, SL_H + 10)) {
                                    sliderDrag = d; applySlider(d, mx); return true;
                                }
                            } else if (s instanceof BooleanToggleBuilder b) { b.toggle(); return true; }
                            else if (s instanceof EnumSelectorBuilder e) { e.cycle(); enAmS.put(s, 1f); return true; }
                            else if (s instanceof StringFieldBuilder st && button == 0) { activeText = st; textFocused = true; return true; }
                            else if (s instanceof ColorFieldBuilder cf && button == 0) {
                                if (tryToggleColorPopup(cf, panX, sy, mx, my)) {
                                    return true;
                                }
                            }
                            else if (s instanceof FriendListFieldBuilder fl && button == 0) {
                                java.util.List<String> names = fl.getNames();
                                int listTop = sy + SET_H;
                                int row = (my - listTop) / SET_H;
                                if (row >= 0 && row < names.size()) {
                                    int rowY = listTop + row * SET_H;
                                    String xLabel = "x";
                                    int xW = client.textRenderer.getWidth(xLabel) + 8;
                                    int xX = panX + W - PX - xW;
                                    if (hit(mx, my, xX, rowY, xW, SET_H)) {
                                        fl.remove(names.get(row));
                                        return true;
                                    }
                                }
                            }
                            else if (s instanceof ActionFieldBuilder af) { af.run(); return true; }
                        }
                        sy += eh;
                    }
                    if (hit(mx, my, panX, sy, W, SET_H + 5)) {
                        if (button == 1) {
                            mod.setKeybind(-1);
                            mod.setBinding(false);
                            if (manager != null) manager.saveKeybinds();
                        } else if (button == 0) {
                            modules.forEach(m -> m.setBinding(false));
                            mod.setBinding(true);
                            bindCaptureDelay = 3;
                        }
                        return true;
                    }
                }
                ry += drawH;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    
    @Override public void mouseMoved(double mx2, double my2) { curMx = (int)mx2; curMy = (int)my2; super.mouseMoved(mx2, my2); }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        int mx = (int)mouseX, my = (int)mouseY;
        if (dragTargetHud) {
            OverlayRenderer hud = manager != null ? manager.getModuleByClass(OverlayRenderer.class) : null;
            if (hud != null && hud.isEditPositionEnabled()) {
                float s = hud.getHudScale();
                float hw = hud.getHudWidth() * s;
                float hh = hud.getHudHeight() * s;
                int sw = client.getWindow().getScaledWidth();
                int sh = client.getWindow().getScaledHeight();
                double nx = mx - dragTargetHudOX;
                double ny = my - dragTargetHudOY;
                nx = Math.max(0, Math.min(sw - hw, nx));
                ny = Math.max(0, Math.min(sh - hh, ny));
                hud.setHudPosition(nx, ny);
                return true;
            }
            dragTargetHud = false;
        }
        if (dragPanel) { panX = clamp(mx - dragOX, MARGIN, width - W - MARGIN); panY = clamp(my - dragOY, MARGIN, height - HDR_H - MARGIN); return true; }
        if (sliderDrag != null) { applySlider(sliderDrag, mx); return true; }
        if (rangeDrag != null) { applyRangeSlider(rangeDrag, mx); return true; }
        if (openColorField != null && button == 0) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragPanel) { dragPanel = false; PersistenceHelper.saveGuiPosition(panX, panY); }
        if (dragTargetHud) {
            dragTargetHud = false;
            OverlayRenderer hud = manager != null ? manager.getModuleByClass(OverlayRenderer.class) : null;
            if (hud != null) {
                PersistenceHelper.saveTargetHudPosition(hud.getHudX(), hud.getHudY());
            }
        }
        sliderDrag = null;
        rangeDrag = null;
        PrestigeColorPicker.mouseReleasedAll();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmt, double vAmt) {
        if (openTarget == 0f) return false; 
        if (manager == null) return super.mouseScrolled(mouseX, mouseY, hAmt, vAmt);
        int mx = (int)mouseX, my = (int)mouseY;
        
        List<ConfigCategoryImpl> vis = visibleModules();
        int cH = contentH(vis), maxVH = Math.max(80, height - MARGIN * 2);
        int viewH = Math.min(cH, maxVH - HDR_H - TAB_H);
        if (hit(mx, my, panX, panY, W, HDR_H + TAB_H + viewH)) {
            float ms = Math.max(0f, cH - viewH);
            tabScrollT[activeTab] = clampF(tabScrollT[activeTab] - (float)(vAmt * MOD_H * 2), 0f, ms);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmt, vAmt);
    }

    private int sliderBaseX;

    private boolean tryToggleColorPopup(ColorFieldBuilder cf, int bx, int rowY, int mx, int my) {
        int swX = bx + W - PX - PrestigeColorPicker.SWATCH_SIZE;
        int swY = rowY + (SET_H - PrestigeColorPicker.SWATCH_SIZE) / 2;
        if (!hit(mx, my, swX - 2, swY - 2,
                PrestigeColorPicker.SWATCH_SIZE + 4, PrestigeColorPicker.SWATCH_SIZE + 4)) {
            return false;
        }
        if (openColorField == cf) {
            openColorField = null;
        } else {
            openColorField = cf;
            PrestigeColorPicker.stateOf(cf).syncFromField();
            placeColorPopup(rowY);
        }
        return true;
    }

    private void placeColorPopup(int anchorRowY) {
        int px = panX + W + 12;
        int py = anchorRowY - 8;
        int popupH = PrestigeColorPicker.popupHeight(openColorField);
        if (px + PrestigeColorPicker.POPUP_W > width - MARGIN) {
            px = panX - PrestigeColorPicker.POPUP_W - 12;
        }
        if (py + popupH > height - MARGIN) {
            py = anchorRowY - popupH - 4;
        }
        if (py < panY - popupH - 4) {
            py = panY - popupH - 8;
        }
        colorPopupX = PrestigeColorPicker.clampPopupX(px, width);
        colorPopupY = PrestigeColorPicker.clampPopupY(py, height, openColorField);
    }

    private void applySlider(DoubleFieldBuilder d, int mx) {
        
        int    slX  = sliderBaseX + IND;
        int    slW  = W - IND - PX;
        double frac = Math.max(0.0, Math.min(1.0, (mx - slX) / (double)slW));
        double val  = d.getMin() + frac * (d.getMax() - d.getMin());
        double inc  = d.getIncrement();
        if (inc > 0) val = Math.round(val / inc) * inc;
        d.set(val);
        slAnim.put(d, (float)frac); 
    }
    
    private void applyRangeSlider(RangeSliderBuilder r, int mx) {
        
        int slX = sliderBaseX + IND;
        int slW = W - IND - PX;
        
        
        double minPct = (r.getMinVal() - r.getMin()) / (r.getMax() - r.getMin());
        double maxPct = (r.getMaxVal() - r.getMin()) / (r.getMax() - r.getMin());
        int minX = slX + (int)(minPct * slW);
        int maxX = slX + (int)(maxPct * slW);
        
        
        if (rangeDrag == r && !rangeDragMin && Math.abs(mx - minX) < Math.abs(mx - maxX)) {
            rangeDragMin = true;
        } else if (rangeDrag == r && rangeDragMin && Math.abs(mx - maxX) < Math.abs(mx - minX)) {
            rangeDragMin = false;
        }
        
        if (rangeDrag == r) {
            rangeDragMin = Math.abs(mx - minX) <= Math.abs(mx - maxX);
        }
        
        
        double frac = Math.max(0.0, Math.min(1.0, (mx - slX) / (double)slW));
        double val  = r.getMin() + frac * (r.getMax() - r.getMin());
        double inc  = r.getIncrement();
        if (inc > 0) val = Math.round(val / inc) * inc;
        
        
        if (rangeDragMin) {
            r.setMinVal(val);
        } else {
            r.setMaxVal(val);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        if (textFocused && activeText != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE)   { textFocused = false; activeText = null; return true; }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                String cur = activeText.get(); if (cur == null) cur = "";
                if (!cur.isEmpty()) activeText.set(cur.substring(0, cur.length() - 1));
                return true;
            }
        }
        for (ConfigCategoryImpl m : modules) {
            if (m.isBinding()) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    m.setBinding(false);
                    return true;
                }
                m.setKeybind((keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) ? -1 : keyCode);
                m.setBinding(false);
                if (manager != null) manager.saveKeybinds();
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE)        { openTarget = 0f; return true; }
        
        if (openTarget == 0f) return false;
        if (GuiKeybinds.isOpenGuiKey(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, mods);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (textFocused && activeText != null && chr >= 32 && chr != 127) {
            String cur = activeText.get(); if (cur == null) cur = "";
            if (cur.length() < activeText.getMaxLength()) activeText.set(cur + chr);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private boolean hit(int mx, int my, int rx, int ry, int rw, int rh) { return mx >= rx && mx < rx + rw && my >= ry && my < ry + rh; }
    private static int   clamp (int   v, int   lo, int   hi) { return Math.max(lo, Math.min(v, hi)); }
    private static float clampF(float v, float lo, float hi) { return Math.max(lo, Math.min(v, hi)); }

    @Override public boolean shouldPause()   { return false; }
    public    Text getSelectedCategory()     { return Text.empty(); }
}
