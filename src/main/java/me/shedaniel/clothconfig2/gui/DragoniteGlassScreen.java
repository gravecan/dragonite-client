package me.shedaniel.clothconfig2.gui;

import me.shedaniel.clothconfig2.gui.prestige.PrestigeRenderHelper;
import me.shedaniel.clothconfig2.impl.ConfigBuilderImpl;
import me.shedaniel.clothconfig2.impl.ConfigCategoryImpl;
import me.shedaniel.clothconfig2.impl.ConfigProfileStore;
import me.shedaniel.clothconfig2.impl.Config_StringList;
import me.shedaniel.clothconfig2.impl.DiscordAvatarTexture;
import me.shedaniel.clothconfig2.impl.FriendManager;
import me.shedaniel.clothconfig2.impl.GuiKeybinds;
import me.shedaniel.clothconfig2.impl.HudConfigInit;
import me.shedaniel.clothconfig2.impl.InjectedClientAssets;
import me.shedaniel.clothconfig2.impl.KawaseBlurHelper;
import me.shedaniel.clothconfig2.impl.RenderHelper;
import me.shedaniel.clothconfig2.impl.builders.AbstractFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.ActionFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.ColorFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import me.shedaniel.clothconfig2.impl.builders.FriendListFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.RangeSliderBuilder;
import me.shedaniel.clothconfig2.impl.builders.StringFieldBuilder;
import me.shedaniel.clothconfig2.internal.IntegrationHandler;
import me.shedaniel.clothconfig2.internal.NetworkHandler;
import me.shedaniel.clothconfig2.internal.SessionHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Minecraft-native port of the final Dragonite glass GUI preview.
 *
 * <p>The legacy {@link ClothConfigScreen} remains in source and still compiles,
 * but normal application routes open this screen.</p>
 */
public final class DragoniteGlassScreen extends Screen {
    private static volatile boolean blurFailureLogged;
    private static final int TEXT = 0xFFF4F6FF;
    private static final int TEXT_SOFT = 0xFFC0C7D8;
    private static final int TEXT_MUTED = 0xFF8892A8;
    private static final int TEXT_FAINT = 0xFF657086;
    private static final int GREEN = 0xFF42E390;
    private static final int RED = 0xFFFF6474;
    private static final int HEADER_H = 36;
    private static final int SIDEBAR_W = 148;
    private static final int SETTINGS_W = 252;
    private static final int BASE_W = 700;
    private static final int BASE_H = 430;
    private static final int GAP = 8;
    private static final int DRAWER_W = SETTINGS_W; // legacy name used by scroll math
    private static final int MIN_SIDEBAR_W = 116;

    private static final Identifier ICONS_TEXTURE = Identifier.of("cloth-config2", "textures/gui/icons.png");
    private static final Identifier LOGO_TEXTURE = Identifier.of("cloth-config2", "textures/gui/logo.png");
    private static final Identifier FONT_INTER = Identifier.of("cloth-config2", "inter");
    private static final Identifier FONT_INTER_SB = Identifier.of("cloth-config2", "inter_semibold");
    private static final int ICON_CELL = 120;
    private static final int ICON_COLUMNS = 9;
    private static final String[] ICON_ORDER = {
            "combat", "movement", "player", "visuals", "misc", "interface", "folder", "friends", "brush",
            "shirt", "discord", "search", "filter", "gear", "back", "info", "chevron", "all"
    };

    private final ConfigBuilderImpl manager;
    private final List<ConfigCategoryImpl> modules = new ArrayList<>();
    private final GlassUiPreferences preferences;
    private final ColorFieldBuilder accentField;
    private final Map<Object, Float> hoverAnimations = new HashMap<>();
    private final Map<ConfigCategoryImpl, Float> toggleAnimations = new HashMap<>();
    private final List<HitBox> hitBoxes = new ArrayList<>();

    private Page page = Page.ALL;
    private ConfigCategoryImpl selectedModule;
    private String search = "";
    private boolean searchFocused;
    private String friendInput = "";
    private boolean friendInputFocused;
    private StringFieldBuilder activeTextField;
    // Modern text-field feel: caret blink, edit pulse, Ctrl+A select-all.
    private long lastEditMillis;
    private float inputPulse;
    private boolean selectAll;
    private ConfigCategoryImpl bindingModule;
    private int bindCaptureDelay;

    private DoubleFieldBuilder draggedDouble;
    private RangeSliderBuilder draggedRange;
    private boolean draggedRangeMin;
    private DragAction appearanceDrag;
    private int dragSliderX;
    private int dragSliderWidth;
    private Integer guiScaleDragOriginX;
    private Integer guiScaleDragOriginY;
    private boolean appearanceDirty;

    private ColorFieldBuilder openColorField;
    private int colorPopupX;
    private int colorPopupY;

    private EnumSelectorBuilder openSelector;
    private int selectorPopupX;
    private int selectorPopupY;
    private int selectorPopupW;

    private float openAnimation;
    private float drawerAnimation;
    private float pageFade;
    private float moduleScroll;
    private float moduleScrollTarget;
    private float drawerScroll;
    private float drawerScrollTarget;
    private float sidebarScroll;
    private float sidebarScrollTarget;
    private float moduleContentHeight;
    private float drawerContentHeight;
    private float sidebarContentHeight;
    private float pageScroll;
    private float pageScrollTarget;
    private float pageContentHeight;
    private int hitOffsetY;
    private Layout layoutCache;
    private float layoutCacheScale = -1f;
    private int layoutCacheW = -1;
    private int layoutCacheH = -1;
    private boolean layoutCacheDrawer;
    private Integer layoutCacheOriginX;
    private Integer layoutCacheOriginY;

    private String configNameInput = "";
    private boolean configNameFocused;
    private String configShareInput = "";
    private boolean configShareFocused;
    private String configStatus = "";
    private String selectedConfigId;
    private long toastUntil;
    private boolean closing;
    private long lastFrameNanos;
    private Layout lastLayout;
    private volatile IntegrationHandler.DiscordUser discordUser;
    private boolean discordLookupStarted;
    private Integer savedMenuBlur;

    public DragoniteGlassScreen(Text title) {
        super(title == null ? Text.empty() : title);
        manager = HudConfigInit.getManager();
        if (manager != null) {
            modules.addAll(manager.getModules());
        }
        preferences = GlassUiPreferences.load();
        accentField = new ColorFieldBuilder("Accent color", "Primary interface color", preferences.accentArgb());
        accentField.setOnChange(preferences::setAccentArgb);
    }

    public static void open() {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        Runnable action = () -> minecraft.setScreen(new DragoniteGlassScreen(Text.empty()));
        if (minecraft.isOnThread()) action.run(); else minecraft.execute(action);
    }

    @Override
    protected void init() {
        if (client != null) {
            InjectedClientAssets.ensureGuiTextures(client);
        }
        // Avoid a fully transparent first frame while the blur pipeline warms up.
        if (!closing && openAnimation <= 0f) {
            openAnimation = 0.12f;
        }
        lastFrameNanos = System.nanoTime();
        // Vanilla GameRenderer projects a menu blur over ANY open screen when the
        // "Menu Background Blurriness" video setting is > 0. That blur washes out
        // every theme, so pin it to 0 while this GUI is open and restore on close.
        if (client != null && savedMenuBlur == null) {
            savedMenuBlur = client.options.getMenuBackgroundBlurriness().getValue();
            if (savedMenuBlur != null && savedMenuBlur > 0) {
                client.options.getMenuBackgroundBlurriness().setValue(0);
            }
        }
        if (!discordLookupStarted) {
            discordLookupStarted = true;
            CompletableFuture.supplyAsync(IntegrationHandler::getUser)
                    .thenAccept(user -> discordUser = user);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // The world is intentionally retained behind the glass shell.
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (manager == null || Config_StringList.isDestroyed()) {
            closeImmediately();
            return;
        }
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0f : Math.min(0.05f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;
        tickAnimations(dt);
        if (closing && openAnimation <= 0.015f) {
            closeImmediately();
            return;
        }

        hitBoxes.clear();
        lastLayout = currentLayout();
        float open = easeOutCubic(openAnimation);
        try {
            // Blur is optional accent only. Default themes keep it off so chrome stays sharp.
            float blur = preferences.blurStrength();
            if (blur > 0.35f) {
                KawaseBlurHelper.updateBlur(blur, 2);
                float radius = preferences.cornerRadius();
                float frost = Math.min(0.55f, 0.18f + blur * 0.05f) * open;
                KawaseBlurHelper.drawBlurredTexture(context,
                        lastLayout.x, lastLayout.y, lastLayout.width, lastLayout.height,
                        frost, 1.0f, radius,
                        0.02f, 0.03f, 0.05f, 0.04f,
                        width, height);
                if (selectedModule != null && drawerAnimation > 0.05f && lastLayout.settingsW > 0) {
                    KawaseBlurHelper.drawBlurredTexture(context,
                            lastLayout.settingsX, lastLayout.settingsY, lastLayout.settingsW, lastLayout.settingsH,
                            frost * drawerAnimation, 1.0f, radius,
                            0.02f, 0.03f, 0.05f, 0.04f,
                            width, height);
                }
            }
        } catch (Throwable t) {
            if (!blurFailureLogged) {
                blurFailureLogged = true;
                System.err.println("[DragoniteBlur] render blur failed: " + t);
            }
        }
        int dimAlpha = Math.round(120f * preferences.backgroundDim() * open);
        if (dimAlpha > 0) {
            // Light scrim only. The panels themselves are opaque, so the world
            // stays visible around the GUI instead of the whole screen going dark.
            context.fill(0, 0, width, height, (dimAlpha << 24) | 0x05070C);
        }

        // Grim-like: quick scale punch from center, not a long fade-out dissolve.
        float scale = 0.96f + 0.04f * open;
        float alpha = open;
        context.getMatrices().push();
        context.getMatrices().translate(width / 2f, height / 2f, 0f);
        context.getMatrices().scale(scale, scale, 1f);
        context.getMatrices().translate(-width / 2f, -height / 2f, 0f);
        renderShell(context, mouseX, mouseY, alpha, lastLayout);
        context.getMatrices().pop();
        if (toastUntil > System.currentTimeMillis() && !configStatus.isEmpty()) {
            int tw = tw(configStatus) + 20;
            int tx = (width - tw) / 2;
            int ty = height - 36;
            RenderHelper.drawRoundedRect(context, tx, ty, tw, 22, 6,
                    new Color(8, 12, 18, Math.round(210 * alpha)));
            draw(context, configStatus, tx + 10, ty + 7, withAlpha(TEXT, alpha), false);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void tickAnimations(float dt) {
        float animation = Math.max(0.15f, preferences.animationStrength());
        openAnimation = approach(openAnimation, closing ? 0f : 1f, 18f * animation, dt);
        drawerAnimation = approach(drawerAnimation, selectedModule == null ? 0f : 1f, 22f * animation, dt);
        pageFade = approach(pageFade, 1f, 20f * animation, dt);
        moduleScroll = approach(moduleScroll, moduleScrollTarget, 22f * animation, dt);
        drawerScroll = approach(drawerScroll, drawerScrollTarget, 22f * animation, dt);
        sidebarScroll = approach(sidebarScroll, sidebarScrollTarget, 22f * animation, dt);
        pageScroll = approach(pageScroll, pageScrollTarget, 22f * animation, dt);
        if (bindCaptureDelay > 0) bindCaptureDelay--;
        inputPulse = approach(inputPulse, 0f, 9f, dt);
        for (ConfigCategoryImpl module : modules) {
            if (module == null) continue;
            float target = module.isEnabled() ? 1f : 0f;
            toggleAnimations.put(module, approach(toggleAnimations.getOrDefault(module, target), target, 24f * animation, dt));
        }
    }

    private void renderShell(DrawContext context, int mouseX, int mouseY, float alpha, Layout layout) {
        int radius = Math.round(preferences.cornerRadius());
        int accent = preferences.accentArgb();
        Color accentColor = color(accent, Math.round(255 * alpha));
        DiscordAvatarTexture.ensure(client, discordUser);

        RenderHelper.drawDropShadow(context, layout.x, layout.y, layout.width, layout.height, radius);
        // Opaque themed surface. Accent lives in a soft tint + hairline, not in a
        // heavy neon border, so the panel reads modern instead of "outlined".
        RenderHelper.drawRoundedRect(context, layout.x, layout.y, layout.width, layout.height, radius,
                surface(0f, 0.04f, Math.min(253, Math.round((236f + 17f * preferences.glassOpacity()) * alpha))));
        RenderHelper.drawRoundedRectOutline(context, layout.x, layout.y, layout.width, layout.height, radius,
                rgb(mix(255, accentColor.getRed(), 0.55f),
                        mix(255, accentColor.getGreen(), 0.55f),
                        mix(255, accentColor.getBlue(), 0.55f),
                        46 * alpha));

        renderHeader(context, mouseX, mouseY, alpha, layout, accentColor);
        renderSidebar(context, mouseX, mouseY, alpha, layout, accentColor);

        if (page.isModulePage()) {
            renderModules(context, mouseX, mouseY, alpha, layout, accentColor);
        } else {
            renderSpecialPage(context, mouseX, mouseY, alpha, layout, accentColor);
        }

        if (drawerAnimation > 0.01f && selectedModule != null) {
            renderSettingsDrawer(context, mouseX, mouseY, alpha * drawerAnimation, layout, accentColor);
        }
        if (openSelector != null) {
            renderSelectorPopup(context, mouseX, mouseY, alpha, accentColor);
        }
        if (openColorField != null) {
            PrestigeRenderHelper.bind(context);
            PrestigeColorPicker.State state = PrestigeColorPicker.stateOf(openColorField);
            PrestigeColorPicker.renderFloatingPopup(context, colorPopupX, colorPopupY, alpha, state,
                    mouseX, mouseY, 1f / 60f);
        }
    }

    private void renderHeader(DrawContext context, int mouseX, int mouseY, float alpha,
                              Layout layout, Color accentColor) {
        int radius = Math.round(preferences.cornerRadius());
        RenderHelper.drawRoundedRect(context, layout.x + 1, layout.y + 1, layout.width - 2, HEADER_H,
                Math.max(2, radius - 1), surface(0.045f, 0.05f, 250 * alpha));
        context.fill(layout.x + 1, layout.y + HEADER_H - 1, layout.x + layout.width - 1,
                layout.y + HEADER_H,
                rgba(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), Math.round(60 * alpha)));

        int logoSize = 22;
        int logoX = layout.x + 12;
        int logoY = layout.y + (HEADER_H - logoSize) / 2;
        drawBrandLogo(context, logoX, logoY, logoSize, alpha);
        int brandX = logoX + logoSize + 8;
        draw(context, "Client", brandX, layout.y + 13, withAlpha(TEXT, alpha), true);
        int betaX = brandX + tw("Client") + 7;
        RenderHelper.drawRoundedRect(context, betaX, layout.y + 11, 27, 13, 4,
                rgb(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 180 * alpha));
        draw(context, "BETA", betaX + 4, layout.y + 14, withAlpha(0xFFF8F4FF, alpha), false);

        int crumbX = betaX + 39;
        draw(context, ">", crumbX, layout.y + 13, withAlpha(TEXT_FAINT, alpha), false);
        draw(context, page.title, crumbX + 14, layout.y + 13, withAlpha(preferences.accentArgb(), alpha), false);

        if (page.isModulePage()) {
            int searchW = Math.min(250, Math.max(145, layout.contentWidth / 3));
            int searchX = layout.x + layout.width - searchW - 139;
            int searchY = layout.y + 7;
            renderTextInput(context, searchX, searchY, searchW, 27, "search", search,
                    "Search modules...", searchFocused, mouseX, mouseY, alpha, accentColor, () -> {
                        searchFocused = true;
                        activeTextField = null;
                        friendInputFocused = false;
                        configNameFocused = false;
                        configShareFocused = false;
                    });

            renderHeaderButton(context, layout.x + layout.width - 129, searchY, "gear", "customization", mouseX, mouseY, alpha,
                    () -> selectPage(Page.CUSTOMIZATION));
            renderHeaderButton(context, layout.x + layout.width - 94, searchY, "shirt", "themes", mouseX, mouseY, alpha,
                    () -> selectPage(Page.THEMES));
        }
        draw(context, "ESC", layout.x + layout.width - 58, layout.y + 13,
                withAlpha(TEXT_SOFT, alpha), true);
        draw(context, "close", layout.x + layout.width - 35, layout.y + 13,
                withAlpha(TEXT_FAINT, alpha), false);
    }

    /**
     * Theme surface color, lifted toward white by {@code lift} and tinted with
     * the accent by {@code accentMix}. Every panel tone goes through this so a
     * theme actually changes the whole GUI, not just the highlight color.
     */
    private Color surface(float lift, float accentMix, float alphaValue) {
        int base = preferences.surfaceArgb();
        int accent = preferences.accentArgb();
        return rgb(mix(liftChannel(base >> 16 & 0xFF, lift), accent >> 16 & 0xFF, accentMix),
                mix(liftChannel(base >> 8 & 0xFF, lift), accent >> 8 & 0xFF, accentMix),
                mix(liftChannel(base & 0xFF, lift), accent & 0xFF, accentMix),
                alphaValue);
    }

    private static int liftChannel(int channel, float amount) {
        return MathHelper.clamp(Math.round(channel + (255 - channel) * clamp(amount, 0f, 1f)), 0, 255);
    }

    private void bumpInput() {
        lastEditMillis = System.currentTimeMillis();
        inputPulse = 1f;
        selectAll = false;
    }

    /**
     * Pill text field with hover/focus ring, blinking caret at the end of the
     * text, a short pulse on every keystroke and Ctrl+A select-all highlight.
     */
    private void renderTextInput(DrawContext context, int x, int y, int w, int h, Object key,
                                 String value, String placeholder, boolean focused,
                                 int mouseX, int mouseY, float alpha, Color accent, Runnable onClick) {
        float hover = hoverValue(key, hit(mouseX, mouseY, x, y, w, h));
        float focus = focused ? 1f : 0f;
        float lift = Math.max(hover * 0.55f, focus);
        int radius = h / 2;
        RenderHelper.drawRoundedRect(context, x, y, w, h, radius,
                surface(0.085f + 0.03f * lift, 0.04f + 0.06f * lift, 251 * alpha));
        RenderHelper.drawRoundedRectOutline(context, x, y, w, h, radius,
                rgb(accent.getRed(), accent.getGreen(), accent.getBlue(),
                        (24 + 70 * hover + 110 * focus + 45 * inputPulse * focus) * alpha));

        int textX = x + 13;
        int textY = y + (h - 8) / 2;
        int maxTextW = w - 26;
        boolean empty = value.isEmpty();
        String shown = empty ? placeholder : value;
        // Keep the tail visible while typing so the caret never leaves the field.
        while (tw(shown) > maxTextW && shown.length() > 1) {
            shown = shown.substring(1);
        }
        if (focused && selectAll && !empty) {
            RenderHelper.drawRoundedRect(context, textX - 2, y + 4, Math.min(maxTextW + 4, tw(shown) + 4), h - 8, 3,
                    rgb(accent.getRed(), accent.getGreen(), accent.getBlue(), 120 * alpha));
        }
        draw(context, shown, textX, textY, withAlpha(empty ? TEXT_FAINT : TEXT, alpha), false);

        if (focused) {
            long since = System.currentTimeMillis() - lastEditMillis;
            // Solid right after a keystroke, then a calm blink.
            boolean caretOn = since < 420 || (since / 520) % 2 == 0;
            if (caretOn) {
                int caretX = textX + (empty ? 0 : Math.min(maxTextW, tw(shown))) + 1;
                int caretH = h - 12 + Math.round(2 * inputPulse);
                int caretY = y + (h - caretH) / 2;
                context.fill(caretX, caretY, caretX + 1, caretY + caretH,
                        withAlpha(preferences.accentArgb(), alpha));
            }
        }
        addHit(x, y, w, h, (mx, my, button) -> {
            bumpInput();
            onClick.run();
        });
    }

    private void drawBrandLogo(DrawContext context, int x, int y, int size, float alpha) {
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, clamp(alpha, 0f, 1f));
        context.drawTexture(LOGO_TEXTURE, x, y, size, size, 0f, 0f, 1024, 1024, 1024, 1024);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void renderHeaderButton(DrawContext context, int x, int y, String icon, String key,
                                    int mouseX, int mouseY, float alpha, Runnable action) {
        float hover = hoverValue(key, hit(mouseX, mouseY, x, y, 28, 27));
        RenderHelper.drawRoundedRect(context, x, y, 28, 27, 6,
                rgb(mix(5, preferences.accentArgb() >> 16 & 0xFF, 0.12f + 0.18f * hover),
                        mix(9, preferences.accentArgb() >> 8 & 0xFF, 0.10f + 0.15f * hover),
                        mix(16, preferences.accentArgb() & 0xFF, 0.14f + 0.18f * hover),
                        (140 + hover * 50) * alpha));
        RenderHelper.drawRoundedRectOutline(context, x, y, 28, 27, 6,
                rgb(preferences.accentArgb() >> 16 & 0xFF, preferences.accentArgb() >> 8 & 0xFF,
                        preferences.accentArgb() & 0xFF, (50 + 90 * hover) * alpha));
        drawIcon(context, icon, x + 7, y + 7, 13,
                withAlpha(hover > 0.35f ? preferences.accentArgb() : TEXT_SOFT, alpha));
        addHit(x, y, 28, 27, (mx, my, button) -> action.run());
    }

    private void renderSidebar(DrawContext context, int mouseX, int mouseY, float alpha,
                               Layout layout, Color accentColor) {
        int sidebarW = layout.sidebarW;
        int sx = layout.x + 1;
        int sy = layout.y + HEADER_H;
        int sh = layout.height - HEADER_H - 1;
        RenderHelper.drawRoundedRect(context, sx, sy, sidebarW, sh,
                Math.max(4, Math.round(preferences.cornerRadius()) - 2),
                surface(0.018f, 0.035f,
                        Math.min(252, Math.round((228f + 24f * preferences.sidebarOpacity()) * alpha))));
        context.fill(sx + sidebarW - 1, sy, sx + sidebarW, sy + sh,
                rgba(255, 255, 255, Math.round(16 * alpha)));

        // Compact status card so the nav list keeps its room at every GUI scale.
        int cardH = 44;
        int navTop = sy + 12;
        int navBottom = sy + sh - cardH - 14;
        sidebarContentHeight = 20 + Page.modulePages().length * 31 + 18 + 20 + Page.generalPages().length * 31 + 6;
        float maxScroll = Math.max(0f, sidebarContentHeight - (navBottom - navTop));
        sidebarScrollTarget = clamp(sidebarScrollTarget, 0f, maxScroll);
        sidebarScroll = clamp(sidebarScroll, 0f, maxScroll);

        context.enableScissor(sx, navTop, sx + sidebarW, navBottom);
        int y = navTop - Math.round(sidebarScroll);
        draw(context, "MODULES", sx + 16, y + 3, withAlpha(TEXT_MUTED, alpha), true);
        y += 20;
        for (Page candidate : Page.modulePages()) {
            y = renderNavItem(context, mouseX, mouseY, alpha, accentColor, sx + 8, y, candidate);
        }
        y += 8;
        context.fill(sx + 16, y, sx + sidebarW - 16, y + 1, rgba(255, 255, 255, Math.round(20 * alpha)));
        y += 9;
        draw(context, "GENERAL", sx + 16, y + 3, withAlpha(TEXT_MUTED, alpha), true);
        y += 20;
        for (Page candidate : Page.generalPages()) {
            y = renderNavItem(context, mouseX, mouseY, alpha, accentColor, sx + 8, y, candidate);
        }
        context.disableScissor();
        if (maxScroll > 0.5f) {
            renderScrollBar(context, sx + sidebarW - 4, navTop, navBottom - navTop,
                    sidebarContentHeight, sidebarScroll, alpha);
        }
        renderDiscordCard(context, mouseX, mouseY, alpha, accentColor, sx + 8, sy + sh - cardH - 6, sidebarW - 16, cardH);
    }

    private void renderDiscordCard(DrawContext context, int mouseX, int mouseY, float alpha,
                                   Color accentColor, int cardX, int cardY, int cardW, int cardH) {
        float hover = hoverValue("discordCard", hit(mouseX, mouseY, cardX, cardY, cardW, cardH));
        RenderHelper.drawRoundedRect(context, cardX, cardY, cardW, cardH, 9,
                surface(0.06f + 0.025f * hover, 0.04f, 250 * alpha));
        RenderHelper.drawRoundedRectOutline(context, cardX, cardY, cardW, cardH, 9,
                rgb(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(),
                        (32 + 30 * hover) * alpha));

        int avatar = 26;
        int avatarY = cardY + (cardH - avatar) / 2;
        drawAvatar(context, cardX + 9, avatarY, avatar, alpha);
        drawPresenceDot(context, cardX + 27, avatarY + avatar - 7, alpha);
        String identity = discordIdentity();
        int textX = cardX + 9 + avatar + 8;
        draw(context, truncate(identity, cardW - (textX - cardX) - 10), textX, cardY + 10,
                withAlpha(TEXT, alpha), true);
        draw(context, discordUser == null ? "Not synced" : "Synced", textX, cardY + 24,
                withAlpha(discordUser == null ? TEXT_MUTED : GREEN, alpha), false);
        // Status card only — intentionally NOT clickable so no page can surface
        // session/network details (server address, connection info) in the GUI.
    }

    private int renderNavItem(DrawContext context, int mouseX, int mouseY, float alpha, Color accent,
                              int x, int y, Page candidate) {
        int w = lastLayout != null ? lastLayout.sidebarW - 16 : SIDEBAR_W - 16;
        int h = 28;
        boolean active = candidate == page;
        float hover = hoverValue(candidate, hit(mouseX, mouseY, x, y, w, h));
        if (active) {
            // Filled accent pill for the active page — reads instantly, no thin bars.
            RenderHelper.drawRoundedRect(context, x, y, w, h, h / 2,
                    rgb(accent.getRed(), accent.getGreen(), accent.getBlue(), 235 * alpha));
        } else if (hover > 0.02f) {
            RenderHelper.drawRoundedRect(context, x, y, w, h, h / 2,
                    surface(0.10f, 0.12f, (170 + 60 * hover) * alpha));
        }
        int iconColor = active ? withAlpha(0xFFFFFFFF, alpha)
                : withAlpha(hover > 0.4f ? TEXT : TEXT_SOFT, alpha);
        drawIcon(context, candidate.icon, x + 11, y + 9, 11, iconColor);
        draw(context, candidate.title, x + 30, y + 10,
                withAlpha(active ? 0xFFFFFFFF : TEXT, alpha), active);
        addHit(x, y, w, h, (mx, my, button) -> selectPage(candidate));
        return y + h + 3;
    }

    private void renderModules(DrawContext context, int mouseX, int mouseY, float alpha,
                               Layout layout, Color accentColor) {
        int contentX = layout.contentX;
        int contentY = layout.contentY;
        int contentW = layout.contentWidth;
        int contentH = layout.contentHeight;
        List<ConfigCategoryImpl> visible = visibleModules();
        // 2-column card grid when there's room; row-major fill keeps the
        // legacy registration order reading left-to-right, top-to-bottom.
        int columns = contentW >= 430 ? 2 : 1;
        int columnGap = 10;
        int columnW = (contentW - (columns - 1) * columnGap) / columns;
        int rowH = Math.round(48 * preferences.density());
        rowH = MathHelper.clamp(rowH, 40, 56);
        int rowGap = 8;
        int rows = (visible.size() + columns - 1) / columns;
        moduleContentHeight = 26 + rows * (rowH + rowGap);
        float maxScroll = Math.max(0f, moduleContentHeight - contentH);
        moduleScrollTarget = clamp(moduleScrollTarget, 0f, maxScroll);
        moduleScroll = clamp(moduleScroll, 0f, maxScroll);

        context.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);
        if (visible.isEmpty()) {
            String emptyLabel = search.isEmpty() ? "No modules in this category" : "No modules match your search";
            draw(context, emptyLabel, contentX + contentW / 2 - tw(emptyLabel) / 2, contentY + 32, withAlpha(TEXT_MUTED, alpha), false);
        }
        int baseY = contentY - Math.round(moduleScroll);
        draw(context, page.title.toUpperCase(Locale.ROOT), contentX + 3, baseY + 3,
                withAlpha(preferences.accentArgb(), alpha), false);
        for (int i = 0; i < visible.size(); i++) {
            int column = i % columns;
            int row = i / columns;
            int x = contentX + column * (columnW + columnGap);
            int y = baseY + 21 + row * (rowH + rowGap);
            renderModuleRow(context, visible.get(i), x, y, columnW, rowH, contentY, contentY + contentH,
                    mouseX, mouseY, alpha, accentColor);
        }
        context.disableScissor();
        renderScrollBar(context, contentX + contentW - 3, contentY, contentH,
                moduleContentHeight, moduleScroll, alpha);
    }

    private void renderModuleRow(DrawContext context, ConfigCategoryImpl module, int x, int y, int w, int h,
                                 int clipTop, int clipBottom, int mouseX, int mouseY, float alpha, Color accentColor) {
        if (y + h < clipTop || y > clipBottom) return;
        boolean hovered = hit(mouseX, mouseY, x, y, w, h);
        float hover = hoverValue(module, hovered);
        boolean selected = module == selectedModule;
        boolean enabled = module.isEnabled();
        float enabledAnim = toggleAnimations.getOrDefault(module, enabled ? 1f : 0f);
        // One palette only: the accent. No per-category colors mixed in.
        int surfaceAlpha = Math.min(252, Math.round((214f + 38f * preferences.cardOpacity()) * alpha));
        RenderHelper.drawRoundedRect(context, x, y, w, h, 9,
                surface(0.055f + 0.02f * hover, 0.04f + 0.13f * enabledAnim, surfaceAlpha));
        RenderHelper.drawRoundedRectOutline(context, x, y, w, h, 9,
                rgb(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(),
                        (selected ? 190 : 22 + 70 * enabledAnim + 45 * hover) * alpha));

        int switchW = 36;
        int switchX = x + w - switchW - 12;
        int switchY = y + (h - 16) / 2;
        renderSwitch(context, switchX, switchY, enabled, enabledAnim, alpha, accentColor);

        if (enabledAnim > 0.02f) {
            RenderHelper.drawRoundedRect(context, x + 1, y + 8, 3, h - 16, 2,
                    rgb(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(),
                            230 * enabledAnim * alpha));
        }

        String name = safe(module.getName(), "Unnamed module");
        String description = safe(module.getDescription(), "No description");
        int textX = x + 14;
        int textMax = Math.max(40, switchX - textX - 8);
        int nameColor = enabled || selected ? preferences.accentArgb() : TEXT;
        draw(context, truncate(name, textMax), textX, y + 9, withAlpha(nameColor, alpha), true);
        draw(context, truncate(description, textMax), textX, y + 24, withAlpha(TEXT_MUTED, alpha), false);

        // Left click toggles, right click opens settings (MogDive-style card + switch).
        addHit(x, y, w, h,
                (mx, my, button) -> module.setEnabled(!module.isEnabled()),
                (mx, my, button) -> {
                    selectedModule = module;
                    openSelector = null;
                    drawerScroll = drawerScrollTarget = 0f;
                    searchFocused = false;
                });
    }

    private void renderSettingsDrawer(DrawContext context, int mouseX, int mouseY, float alpha,
                                      Layout layout, Color accentColor) {
        int fullW = layout.settingsW;
        int drawerW = Math.max(1, Math.round(fullW * Math.min(1f, 0.85f + 0.15f * drawerAnimation)));
        // Grim-style: floating settings card to the right of the main panel.
        int x = layout.settingsX + (fullW - drawerW);
        int y = layout.settingsY;
        int h = Math.round(layout.settingsH * (0.92f + 0.08f * drawerAnimation));
        float slide = (1f - drawerAnimation) * 18f;
        x += Math.round(slide);
        RenderHelper.drawDropShadow(context, x, y, drawerW, h, Math.round(preferences.cornerRadius()));
        RenderHelper.drawRoundedRect(context, x, y, drawerW, h, Math.round(preferences.cornerRadius()),
                surface(0.025f, 0.045f, 252 * alpha));
        RenderHelper.drawRoundedRectOutline(context, x, y, drawerW, h, Math.round(preferences.cornerRadius()),
                rgb(mix(255, accentColor.getRed(), 0.55f), mix(255, accentColor.getGreen(), 0.55f),
                        mix(255, accentColor.getBlue(), 0.55f), 46 * alpha));
        RenderHelper.drawRoundedRect(context, x, y, 3, h, Math.round(preferences.cornerRadius()),
                rgb(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 210 * alpha));
        if (drawerW < 140) return;

        int pad = 12;
        int headerH = 52;
        draw(context, truncate(safe(selectedModule.getName(), "Module"), fullW - 70),
                x + pad, y + 12, withAlpha(preferences.accentArgb(), alpha), true);
        draw(context, "Right-click module · Esc closes", x + pad, y + 28, withAlpha(TEXT_MUTED, alpha), false);
        int closeX = x + fullW - 28;
        RenderHelper.drawRoundedRect(context, closeX, y + 10, 18, 18, 5,
                new Color(20, 24, 32, Math.round(180 * alpha)));
        draw(context, "x", closeX + 6, y + 15, withAlpha(TEXT_SOFT, alpha), false);
        addHit(closeX, y + 10, 18, 18, (mx, my, button) -> { selectedModule = null; openSelector = null; });

        // Enabled row like Grim
        int enabledY = y + headerH - 2;
        draw(context, "Enabled", x + pad, enabledY + 4, withAlpha(TEXT_SOFT, alpha), false);
        int switchX = x + fullW - pad - 36;
        renderSwitch(context, switchX, enabledY, selectedModule.isEnabled(),
                toggleAnimations.getOrDefault(selectedModule, selectedModule.isEnabled() ? 1f : 0f), alpha, accentColor);
        addHit(x + pad, enabledY - 4, fullW - pad * 2, 24,
                (mx, my, button) -> selectedModule.setEnabled(!selectedModule.isEnabled()));
        context.fill(x + 1, y + headerH + 22, x + fullW - 1, y + headerH + 23,
                rgba(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), Math.round(70 * alpha)));

        int clipTop = y + headerH + 28;
        int clipBottom = y + h - 10;
        int rowY = clipTop + 4 - Math.round(drawerScroll);
        context.enableScissor(x, clipTop, x + fullW, clipBottom);
        // Same order as legacy ClickGUI: module settings first, keybind last.
        for (AbstractFieldBuilder field : visibleSettings(selectedModule)) {
            int rowHeight = settingHeight(field);
            if (rowY + rowHeight >= clipTop && rowY <= clipBottom) {
                renderSetting(context, field, x + pad, rowY, fullW - pad * 2, rowHeight,
                        clipTop, clipBottom, mouseX, mouseY, alpha, accentColor);
            }
            rowY += rowHeight + 6;
        }
        if (rowY + 40 >= clipTop && rowY <= clipBottom) {
            renderKeybindRow(context, selectedModule, x + pad, rowY, fullW - pad * 2,
                    clipTop, clipBottom, alpha, accentColor);
        }
        rowY += 40;
        drawerContentHeight = Math.max(0, rowY + Math.round(drawerScroll) - clipTop);
        float maxScroll = Math.max(0f, drawerContentHeight - (clipBottom - clipTop));
        drawerScrollTarget = clamp(drawerScrollTarget, 0f, maxScroll);
        drawerScroll = clamp(drawerScroll, 0f, maxScroll);
        context.disableScissor();
        renderScrollBar(context, x + fullW - 4, clipTop, clipBottom - clipTop,
                drawerContentHeight, drawerScroll, alpha);
    }

    private void renderSetting(DrawContext context, AbstractFieldBuilder field, int x, int y, int w, int h,
                               int clipTop, int clipBottom, int mouseX, int mouseY, float alpha, Color accent) {
        // Opaque row: settings text must stay readable without blur behind it.
        RenderHelper.drawRoundedRect(context, x, y, w, h, 8, surface(0.075f, 0.035f, 251 * alpha));
        RenderHelper.drawRoundedRectOutline(context, x, y, w, h, 8,
                rgb(255, 255, 255, 26 * alpha));
        String name = safe(field.getName(), "Setting");
        draw(context, truncate(name, w - 18), x + 9, y + 8, withAlpha(TEXT, alpha), true);

        if (field instanceof BooleanToggleBuilder toggle) {
            // Real switch, not a hand-drawn pixel tick.
            boolean on = toggle.get();
            renderSwitch(context, x + w - 46, y + (h - 16) / 2, on, on ? 1f : 0f, alpha, accent);
            addHit(x, y, w, h, (mx2, my2, button) -> toggle.toggle());
        } else if (field instanceof DoubleFieldBuilder number) {
            String value = format(number);
            draw(context, value, x + w - 9 - tw(value), y + 8,
                    withAlpha(preferences.accentArgb(), alpha), false);
            int sliderX = x + 10;
            int sliderY = y + h - 13;
            int sliderW = w - 20;
            double fraction = normalized(number.get(), number.getMin(), number.getMax());
            renderSlider(context, sliderX, sliderY, sliderW, (float) fraction, alpha, accent);
            addHit(sliderX, sliderY - 8, sliderW, 18, (mx, my, button) -> {
                draggedDouble = number;
                draggedRange = null;
                dragSliderX = sliderX;
                dragSliderWidth = sliderW;
                applyDouble(number, mx);
            });
        } else if (field instanceof RangeSliderBuilder range) {
            String value = format(range.getMinVal()) + " - " + format(range.getMaxVal()) + range.getSuffix();
            draw(context, value, x + w - 9 - tw(value), y + 8,
                    withAlpha(preferences.accentArgb(), alpha), false);
            int sliderX = x + 10;
            int sliderY = y + h - 13;
            int sliderW = w - 20;
            float min = (float) normalized(range.getMinVal(), range.getMin(), range.getMax());
            float max = (float) normalized(range.getMaxVal(), range.getMin(), range.getMax());
            renderRangeSlider(context, sliderX, sliderY, sliderW, min, max, alpha, accent);
            addHit(sliderX, sliderY - 8, sliderW, 18, (mx, my, button) -> {
                draggedRange = range;
                draggedDouble = null;
                dragSliderX = sliderX;
                dragSliderWidth = sliderW;
                int minX = sliderX + Math.round(min * sliderW);
                int maxX = sliderX + Math.round(max * sliderW);
                draggedRangeMin = Math.abs(mx - minX) <= Math.abs(mx - maxX);
                applyRange(range, mx);
            });
        } else if (field instanceof EnumSelectorBuilder selector) {
            String value = safe(selector.get(), "-");
            int boxW = Math.min(112, Math.max(65, tw(value) + 25));
            int boxX = x + w - boxW - 8;
            boolean open = openSelector == selector;
            RenderHelper.drawRoundedRect(context, boxX, y + 5, boxW, h - 10, 5,
                    new Color(3, 8, 14, Math.round((open ? 210 : 170) * alpha)));
            RenderHelper.drawRoundedRectOutline(context, boxX, y + 5, boxW, h - 10, 5,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(),
                            Math.round((open ? 120 : 25) * alpha)));
            draw(context, truncate(value, boxW - 20), boxX + 8, y + 9, withAlpha(TEXT, alpha), false);
            drawIcon(context, "chevron", boxX + boxW - 14, y + 9, 8, withAlpha(TEXT_MUTED, alpha));
            addHit(x, y, w, h, (mx, my, button) -> openSelectorPopup(selector, boxX, y + h + 2, boxW));
        } else if (field instanceof StringFieldBuilder string) {
            int inputY = y + 20;
            renderTextInput(context, x + 8, inputY, w - 16, Math.max(20, h - 26), string,
                    safe(string.get(), ""), "Type here...", activeTextField == string,
                    mouseX, mouseY, alpha, accent, () -> {
                        activeTextField = string;
                        searchFocused = false;
                        friendInputFocused = false;
                        configNameFocused = false;
                        configShareFocused = false;
                    });
        } else if (field instanceof ColorFieldBuilder colorField) {
            int swatch = 21;
            int swatchX = x + w - swatch - 10;
            int swatchY = y + (h - swatch) / 2;
            RenderHelper.drawRoundedRect(context, swatchX, swatchY, swatch, swatch, 5,
                    color(colorField.getARGB(), Math.round(255 * alpha)));
            RenderHelper.drawRoundedRectOutline(context, swatchX, swatchY, swatch, swatch, 5,
                    new Color(255, 255, 255, Math.round(90 * alpha)));
            draw(context, "#" + colorField.toString(), swatchX - 8 - tw("#" + colorField),
                    y + 8, withAlpha(TEXT_MUTED, alpha), false);
            addHit(x, y, w, h, (mx, my, button) -> openColorPicker(colorField, swatchX, swatchY));
        } else if (field instanceof FriendListFieldBuilder list) {
            int listY = y + 22;
            for (String friend : list.getNames()) {
                draw(context, truncate(friend, w - 42), x + 10, listY, withAlpha(TEXT_SOFT, alpha), false);
                int removeX = x + w - 24;
                draw(context, "x", removeX, listY, withAlpha(RED, alpha), false);
                final String nameToRemove = friend;
                addHit(removeX - 5, listY - 4, 20, 18, (mx, my, button) -> list.remove(nameToRemove));
                listY += 15;
            }
        } else if (field instanceof ActionFieldBuilder action) {
            int buttonX = x + w - 69;
            RenderHelper.drawRoundedRect(context, buttonX, y + 5, 60, h - 10, 5,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), Math.round(95 * alpha)));
            draw(context, "Run", buttonX + 30 - tw("Run") / 2, y + 9, withAlpha(TEXT, alpha), false);
            addHit(x, y, w, h, (mx, my, button) -> action.run());
        } else {
            draw(context, "Read only", x + w - 58, y + 8, withAlpha(TEXT_FAINT, alpha), false);
        }
    }

    private void renderKeybindRow(DrawContext context, ConfigCategoryImpl module, int x, int y, int w,
                                  int clipTop, int clipBottom, float alpha, Color accent) {
        if (y + 36 < clipTop || y > clipBottom) return;
        RenderHelper.drawRoundedRect(context, x, y, w, 36, 7,
                new Color(6, 12, 16, Math.round((70 + 90 * preferences.cardOpacity()) * alpha)));
        draw(context, "Keybind", x + 9, y + 13, withAlpha(TEXT_SOFT, alpha), false);
        String value = bindingModule == module ? "Press a key..." : keyLabel(module);
        int keyW = Math.max(52, tw(value) + 16);
        int keyX = x + w - keyW - 8;
        RenderHelper.drawRoundedRect(context, keyX, y + 6, keyW, 24, 5,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), Math.round(54 * alpha)));
        draw(context, value, keyX + keyW / 2 - tw(value) / 2, y + 14, withAlpha(TEXT, alpha), false);
        addHit(x, y, w, 36, (mx, my, button) -> {
            bindingModule = module;
            bindCaptureDelay = 3;
        }, (mx, my, button) -> {
            module.setKeybind(-1);
            bindingModule = null;
            manager.saveKeybinds();
        });
    }

    private void renderSpecialPage(DrawContext context, int mouseX, int mouseY, float alpha,
                                   Layout layout, Color accent) {
        // Clip and scroll every non-module page so long content can never spill
        // past the panel floor at smaller GUI scales.
        int clipTop = layout.contentY - 4;
        int clipBottom = layout.y + layout.height - 8;
        float maxScroll = Math.max(0f, pageContentHeight - (clipBottom - clipTop));
        pageScrollTarget = clamp(pageScrollTarget, 0f, maxScroll);
        pageScroll = clamp(pageScroll, 0f, maxScroll);
        context.enableScissor(layout.contentX - 6, clipTop,
                layout.x + layout.width - 4, clipBottom);
        context.getMatrices().push();
        int scrolled = Math.round(pageScroll);
        context.getMatrices().translate(0f, -scrolled, 0f);
        // Hit boxes and hover tests live in unscrolled page space.
        hitOffsetY = -scrolled;
        int hoverY = mouseY + scrolled;
        int contentBottom;
        switch (page) {
            case CONFIGS -> contentBottom = renderConfigsPage(context, mouseX, hoverY, alpha, layout, accent);
            case FRIENDS -> contentBottom = renderFriendsPage(context, mouseX, hoverY, alpha, layout, accent);
            case CUSTOMIZATION -> contentBottom = renderCustomizationPage(context, mouseX, hoverY, alpha, layout, accent);
            case THEMES -> contentBottom = renderThemesPage(context, mouseX, hoverY, alpha, layout, accent);
            case DISCORD -> contentBottom = renderDiscordPage(context, mouseX, hoverY, alpha, layout, accent);
            default -> {
                context.getMatrices().pop();
                context.disableScissor();
                hitOffsetY = 0;
                renderModules(context, mouseX, mouseY, alpha, layout, accent);
                return;
            }
        }
        context.getMatrices().pop();
        context.disableScissor();
        hitOffsetY = 0;
        pageContentHeight = Math.max(0, contentBottom - clipTop + 10);
        if (maxScroll > 0.5f) {
            renderScrollBar(context, layout.x + layout.width - 6, clipTop, clipBottom - clipTop,
                    pageContentHeight, pageScroll, alpha);
        }
    }

    private int renderDiscordPage(DrawContext context, int mouseX, int mouseY, float alpha,
                                   Layout layout, Color accent) {
        int x = layout.contentX + 8;
        int y = layout.contentY + 8;
        int fullW = layout.contentWidth - 16;
        int leftW = Math.min(280, Math.max(210, fullW * 45 / 100));
        int gap = 12;
        int rightX = x + leftW + gap;
        int rightW = Math.max(160, fullW - leftW - gap);

        draw(context, "Discord Sync", x, y, withAlpha(preferences.accentArgb(), alpha), true);
        draw(context, "Live identity preview — visual only, nothing to configure here.", x, y + 15,
                withAlpha(TEXT_MUTED, alpha), false);
        y += 38;

        // Profile hero card
        int heroH = 118;
        RenderHelper.drawRoundedRect(context, x, y, leftW, heroH, 10,
                rgb(mix(8, accent.getRed(), 0.22f), mix(12, accent.getGreen(), 0.18f),
                        mix(18, accent.getBlue(), 0.24f), 210 * alpha));
        RenderHelper.drawRoundedRectOutline(context, x, y, leftW, heroH, 10,
                rgb(accent.getRed(), accent.getGreen(), accent.getBlue(), 150 * alpha));
        RenderHelper.drawRoundedRect(context, x, y, 4, heroH, 10,
                rgb(accent.getRed(), accent.getGreen(), accent.getBlue(), 230 * alpha));
        drawAvatar(context, x + 18, y + 28, 58, alpha);
        drawPresenceDot(context, x + 64, y + 74, alpha);
        String identity = discordIdentity();
        draw(context, truncate(identity, leftW - 100), x + 90, y + 34, withAlpha(TEXT, alpha), true);
        draw(context, discordUser == null ? "Not connected" : "Connected and online",
                x + 90, y + 52, withAlpha(discordUser == null ? TEXT_MUTED : GREEN, alpha), false);
        RenderHelper.drawRoundedRect(context, x + 90, y + 74, 74, 18, 5,
                rgb(accent.getRed(), accent.getGreen(), accent.getBlue(), 120 * alpha));
        draw(context, discordUser == null ? "OFFLINE" : "SYNCED", x + 102, y + 79,
                withAlpha(0xFFF4F0FF, alpha), false);

        // Rich presence panel
        RenderHelper.drawRoundedRect(context, rightX, y, rightW, heroH, 10,
                rgb(mix(7, accent.getRed(), 0.16f), mix(11, accent.getGreen(), 0.13f),
                        mix(16, accent.getBlue(), 0.18f), 205 * alpha));
        RenderHelper.drawRoundedRectOutline(context, rightX, y, rightW, heroH, 10,
                rgb(accent.getRed(), accent.getGreen(), accent.getBlue(), 110 * alpha));
        draw(context, "RICH PRESENCE", rightX + 14, y + 14, withAlpha(preferences.accentArgb(), alpha), false);
        draw(context, "Minecraft · Dragonite Client", rightX + 14, y + 32, withAlpha(TEXT, alpha), true);
        long enabled = modules.stream().filter(m -> m != null && m.isEnabled()).count();
        draw(context, "Server", rightX + 14, y + 58, withAlpha(TEXT_MUTED, alpha), false);
        // Never surface the current server address in the GUI (privacy).
        draw(context, "Hidden", rightX + 14, y + 72, withAlpha(TEXT_SOFT, alpha), false);
        draw(context, enabled + " modules on", rightX + 14, y + 92, withAlpha(preferences.accentArgb(), alpha), false);

        y += heroH + 14;
        int tileW = (fullW - 10) / 2;
        int tileH = 72;
        renderDiscordStatTile(context, x, y, tileW, tileH, alpha, accent, "Avatar",
                DiscordAvatarTexture.isReady() ? "Synced from Discord" : "Fallback initial");
        renderDiscordStatTile(context, x + tileW + 10, y, tileW, tileH, alpha, accent, "Account",
                discordUser == null ? "Sign in via Dragonite auth" : "Linked to this session");
        y += tileH + 12;
        renderDiscordStatTile(context, x, y, fullW, 58, alpha, accent, "What this page is",
                "A visual status board for Discord Sync. Use Themes if you want a stronger accent color.");
        return y + 58;
    }

    private void renderDiscordStatTile(DrawContext context, int x, int y, int w, int h, float alpha,
                                       Color accent, String title, String body) {
        RenderHelper.drawRoundedRect(context, x, y, w, h, 9,
                rgb(mix(8, accent.getRed(), 0.14f), mix(12, accent.getGreen(), 0.11f),
                        mix(17, accent.getBlue(), 0.16f), 195 * alpha));
        RenderHelper.drawRoundedRectOutline(context, x, y, w, h, 9,
                rgb(accent.getRed(), accent.getGreen(), accent.getBlue(), 85 * alpha));
        draw(context, title, x + 14, y + 12, withAlpha(preferences.accentArgb(), alpha), false);
        draw(context, truncate(body, w - 28), x + 14, y + 30, withAlpha(TEXT_SOFT, alpha), false);
    }

    private int renderConfigsPage(DrawContext context, int mouseX, int mouseY, float alpha,
                                   Layout layout, Color accent) {
        int x = layout.contentX + 6;
        int y = layout.contentY + 4;
        int w = Math.min(420, layout.contentWidth - 12);
        draw(context, "Configs", x, y, withAlpha(TEXT, alpha), true);
        draw(context, "Save setups locally, share a code, or load one from a friend.", x, y + 14,
                withAlpha(TEXT_MUTED, alpha), false);
        y += 36;

        renderTextInput(context, x, y, w - 70, 28, "configName", configNameInput,
                "New config name", configNameFocused, mouseX, mouseY, alpha, accent, () -> {
                    configNameFocused = true;
                    configShareFocused = false;
                    searchFocused = false;
                    friendInputFocused = false;
                    activeTextField = null;
                });        RenderHelper.drawRoundedRect(context, x + w - 64, y, 64, 28, 6,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), Math.round(190 * alpha)));
        draw(context, "Save", x + w - 64 + 18, y + 10, withAlpha(TEXT, alpha), true);
        addHit(x + w - 64, y, 64, 28, (mx, my, button) -> saveConfigProfile());
        y += 38;

        renderTextInput(context, x, y, w - 70, 28, "configShare", configShareInput,
                "Paste DN1: share code", configShareFocused, mouseX, mouseY, alpha, accent, () -> {
                    configShareFocused = true;
                    configNameFocused = false;
                    searchFocused = false;
                    friendInputFocused = false;
                    activeTextField = null;
                });
        RenderHelper.drawRoundedRect(context, x + w - 64, y, 64, 28, 6,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), Math.round(160 * alpha)));
        draw(context, "Import", x + w - 64 + 12, y + 10, withAlpha(TEXT, alpha), true);
        addHit(x + w - 64, y, 64, 28, (mx, my, button) -> importConfigShare());
        y += 34;
        drawActionChip(context, x, y, 88, "Cloud list", alpha, accent, this::browseCloudConfigs);
        draw(context, "Session only (resets on restart) · RMB publish · Copy = share code", x + 100, y + 8,
                withAlpha(TEXT_MUTED, alpha), false);
        y += 36;

        List<ConfigProfileStore.Profile> profiles = ConfigProfileStore.listLocal();
        if (profiles.isEmpty()) {
            draw(context, "No configs this session.", x, y,
                    withAlpha(TEXT_MUTED, alpha), false);
            return y + 14;
        }
        for (ConfigProfileStore.Profile profile : profiles) {
            boolean selected = profile.id.equals(selectedConfigId);
            int rowH = 52;
            RenderHelper.drawRoundedRect(context, x, y, w, rowH, 8,
                    surface(0.06f, 0.035f + (selected ? 0.05f : 0f), 251 * alpha));
            if (selected) {
                RenderHelper.drawRoundedRectOutline(context, x, y, w, rowH, 8,
                        new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), Math.round(120 * alpha)));
            }
            draw(context, truncate(profile.name, w - 160), x + 12, y + 10, withAlpha(TEXT, alpha), true);
            draw(context, "by " + safe(profile.author, "local"), x + 12, y + 26, withAlpha(TEXT_MUTED, alpha), false);
            int bx = x + w - 148;
            drawActionChip(context, bx, y + 12, 42, "Load", alpha, accent,
                    () -> loadConfigProfile(profile));
            drawActionChip(context, bx + 48, y + 12, 42, "Copy", alpha, accent,
                    () -> copyConfigShare(profile));
            drawActionChip(context, bx + 96, y + 12, 42, "Del", alpha, new Color(220, 80, 90),
                    () -> {
                        ConfigProfileStore.delete(profile.id);
                        if (profile.id.equals(selectedConfigId)) selectedConfigId = null;
                        toast("Deleted " + profile.name);
                    });
            addHit(x, y, w - 150, rowH,
                    (mx, my, button) -> selectedConfigId = profile.id,
                    (mx, my, button) -> publishConfigProfile(profile));
            y += rowH + 7;
        }
        return y;
    }

    private void drawActionChip(DrawContext context, int x, int y, int w, String label, float alpha,
                                Color accent, Runnable action) {
        RenderHelper.drawRoundedRect(context, x, y, w, 26, 6,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), Math.round(110 * alpha)));
        draw(context, label, x + (w - tw(label)) / 2, y + 9, withAlpha(TEXT, alpha), false);
        addHit(x, y, w, 26, (mx, my, button) -> action.run());
    }

    private void saveConfigProfile() {
        String name = configNameInput.trim();
        if (name.isEmpty()) name = "Config " + (ConfigProfileStore.listLocal().size() + 1);
        String author = discordIdentity();
        ConfigProfileStore.Profile profile = ConfigProfileStore.saveCurrent(manager, name, author);
        selectedConfigId = profile.id;
        configNameInput = "";
        toast("Saved " + profile.name);
    }

    private void loadConfigProfile(ConfigProfileStore.Profile profile) {
        if (ConfigProfileStore.apply(manager, profile)) {
            selectedConfigId = profile.id;
            toast("Loaded " + profile.name);
        } else {
            toast("Could not load config");
        }
    }

    private void copyConfigShare(ConfigProfileStore.Profile profile) {
        String code = ConfigProfileStore.toShareCode(profile);
        if (client != null) {
            client.keyboard.setClipboard(code);
        }
        toast("Share code copied");
    }

    private void importConfigShare() {
        String raw = configShareInput.trim();
        if (raw.isEmpty() && client != null) {
            raw = safe(client.keyboard.getClipboard(), "");
        }
        ConfigProfileStore.Profile profile = ConfigProfileStore.importShareCode(raw);
        if (profile == null) {
            toast("Invalid share code");
            return;
        }
        selectedConfigId = profile.id;
        configShareInput = "";
        toast("Imported " + profile.name);
    }

    private void publishConfigProfile(ConfigProfileStore.Profile profile) {
        NetworkHandler net = SessionHandler.getInstance().getNetworkHandler();
        if (net == null || !net.hasLiveSession()) {
            toast("Login required to publish");
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                com.google.gson.JsonObject body = new com.google.gson.JsonObject();
                body.addProperty("name", profile.name);
                body.addProperty("author", profile.author);
                body.addProperty("shareCode", ConfigProfileStore.toShareCode(profile));
                com.google.gson.JsonObject res = net.postAuthed("/v1/configs/publish", body);
                String msg = res != null && res.has("ok") && res.get("ok").getAsBoolean()
                        ? "Published " + profile.name
                        : "Publish failed";
                if (client != null) client.execute(() -> toast(msg));
            } catch (Exception e) {
                if (client != null) client.execute(() -> toast("Publish failed"));
            }
        });
        toast("Publishing...");
    }

    private void browseCloudConfigs() {
        NetworkHandler net = SessionHandler.getInstance().getNetworkHandler();
        if (net == null || !net.hasLiveSession()) {
            toast("Login required for cloud configs");
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                com.google.gson.JsonObject res = net.postAuthed("/v1/configs/list", new com.google.gson.JsonObject());
                if (res == null || !res.has("configs") || !res.get("configs").isJsonArray()) {
                    if (client != null) client.execute(() -> toast("No cloud configs"));
                    return;
                }
                int imported = 0;
                for (com.google.gson.JsonElement el : res.getAsJsonArray("configs")) {
                    if (!el.isJsonObject()) continue;
                    com.google.gson.JsonObject obj = el.getAsJsonObject();
                    if (!obj.has("shareCode")) continue;
                    ConfigProfileStore.Profile profile = ConfigProfileStore.importShareCode(obj.get("shareCode").getAsString());
                    if (profile != null) imported++;
                    if (imported >= 8) break;
                }
                int finalImported = imported;
                if (client != null) client.execute(() -> toast(finalImported == 0 ? "No new configs" : "Imported " + finalImported + " cloud configs"));
            } catch (Exception e) {
                if (client != null) client.execute(() -> toast("Cloud list failed"));
            }
        });
        toast("Fetching cloud...");
    }

    private void toast(String message) {
        configStatus = message;
        toastUntil = System.currentTimeMillis() + 1800;
    }

    private void renderActionCard(DrawContext context, int x, int y, int w, String title, String detail,
                                  float alpha, Color accent, Runnable action) {
        RenderHelper.drawRoundedRect(context, x, y, w, 46, 8,
                new Color(8, 15, 25, Math.round(150 * alpha)));
        RenderHelper.drawRoundedRectOutline(context, x, y, w, 46, 8,
                new Color(255, 255, 255, Math.round(20 * alpha)));
        draw(context, title, x + 12, y + 9, withAlpha(TEXT, alpha), false);
        draw(context, truncate(detail, w - 92), x + 12, y + 25, withAlpha(TEXT_MUTED, alpha), false);
        int bx = x + w - 67;
        RenderHelper.drawRoundedRect(context, bx, y + 9, 55, 28, 6,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), Math.round(105 * alpha)));
        draw(context, "Apply", bx + 27 - tw("Apply") / 2, y + 19, withAlpha(TEXT, alpha), false);
        addHit(x, y, w, 46, (mx, my, button) -> action.run());
    }

    private int renderFriendsPage(DrawContext context, int mouseX, int mouseY, float alpha,
                                   Layout layout, Color accent) {
        FriendManager friends = manager.getModuleByClass(FriendManager.class);
        int x = layout.contentX + 8;
        int y = layout.contentY + 8;
        draw(context, "Friends", x, y, withAlpha(TEXT, alpha), true);
        if (friends == null) {
            draw(context, "Friend manager is unavailable.", x, y + 21, withAlpha(TEXT_MUTED, alpha), false);
            return y + 35;
        }
        draw(context, friends.isEnabled() ? "Protection active" : "Protection disabled",
                x, y + 18, withAlpha(friends.isEnabled() ? GREEN : TEXT_MUTED, alpha), false);
        renderSwitch(context, x + 145, y + 13, friends.isEnabled(), friends.isEnabled() ? 1f : 0f, alpha, accent);
        addHit(x + 140, y + 7, 45, 28, (mx, my, button) -> friends.setEnabled(!friends.isEnabled()));

        int inputY = y + 46;
        int inputW = Math.min(300, layout.contentWidth - 100);
        renderTextInput(context, x, inputY, inputW, 30, "friendInput", friendInput,
                "Minecraft username", friendInputFocused, mouseX, mouseY, alpha, accent, () -> {
                    friendInputFocused = true;
                    searchFocused = false;
                    configNameFocused = false;
                    configShareFocused = false;
                    activeTextField = null;
                });
        int addX = x + inputW + 8;
        RenderHelper.drawRoundedRect(context, addX, inputY, 58, 30, 7,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), Math.round(155 * alpha)));
        draw(context, "Add", addX + 29 - tw("Add") / 2, inputY + 11, withAlpha(TEXT, alpha), false);
        addHit(addX, inputY, 58, 30, (mx, my, button) -> addFriend(friends));

        int listY = inputY + 47;
        List<String> names = new ArrayList<>(friends.getFriends());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        if (names.isEmpty()) {
            draw(context, "No friends added yet.", x, listY, withAlpha(TEXT_MUTED, alpha), false);
        }
        for (String name : names) {
            int rowW = Math.min(380, layout.contentWidth - 16);
            RenderHelper.drawRoundedRect(context, x, listY, rowW, 35, 8,
                    surface(0.06f, 0.035f, 251 * alpha));
            draw(context, truncate(name, rowW - 58), x + 11, listY + 13, withAlpha(TEXT, alpha), false);
            int removeX = x + rowW - 45;
            draw(context, "Remove", removeX, listY + 13, withAlpha(RED, alpha), false);
            addHit(removeX - 5, listY + 5, 51, 25, (mx, my, button) -> friends.removeFriend(name));
            listY += 42;
        }
        return listY;
    }

    private int renderCustomizationPage(DrawContext context, int mouseX, int mouseY, float alpha,
                                         Layout layout, Color accent) {
        int x = layout.contentX + 8;
        int y = layout.contentY + 6;
        int w = Math.min(480, layout.contentWidth - 16);
        draw(context, "Personalization", x, y, withAlpha(TEXT, alpha), true);
        draw(context, "GUI scale, surfaces, and motion. Scroll for more.", x, y + 15,
                withAlpha(TEXT_MUTED, alpha), false);
        y += 38;

        y = renderAppearanceSlider(context, x, y, w, "GUI scale", preferences.guiScale(), 0.75f, 1.25f,
                alpha, accent, true, value -> {
                    if (guiScaleDragOriginX == null && lastLayout != null) {
                        guiScaleDragOriginX = lastLayout.x;
                        guiScaleDragOriginY = lastLayout.y;
                    }
                    preferences.setGuiScale(value);
                    appearanceDirty = true;
                });
        y = renderAppearanceSlider(context, x, y, w, "Blur strength", preferences.blurStrength(), 0f, 8f,
                alpha, accent, false, value -> { preferences.setBlurStrength(value); appearanceDirty = true; });
        y = renderAppearanceSlider(context, x, y, w, "Glass opacity", preferences.glassOpacity(), 0.36f, 0.94f,
                alpha, accent, false, value -> { preferences.setGlassOpacity(value); appearanceDirty = true; });
        y = renderAppearanceSlider(context, x, y, w, "Sidebar opacity", preferences.sidebarOpacity(), 0.28f, 0.92f,
                alpha, accent, false, value -> { preferences.setSidebarOpacity(value); appearanceDirty = true; });
        y = renderAppearanceSlider(context, x, y, w, "Card opacity", preferences.cardOpacity(), 0.22f, 0.90f,
                alpha, accent, false, value -> { preferences.setCardOpacity(value); appearanceDirty = true; });
        y = renderAppearanceSlider(context, x, y, w, "Corner radius", preferences.cornerRadius(), 4f, 18f,
                alpha, accent, false, value -> { preferences.setCornerRadius(value); appearanceDirty = true; });
        y = renderAppearanceSlider(context, x, y, w, "Animation snap", preferences.animationStrength(), 0.4f, 1.5f,
                alpha, accent, false, value -> { preferences.setAnimationStrength(value); appearanceDirty = true; });

        int colorY = y + 3;
        RenderHelper.drawRoundedRect(context, x, colorY, w, 38, 8, surface(0.075f, 0.035f, 251 * alpha));
        RenderHelper.drawRoundedRectOutline(context, x, colorY, w, 38, 8, rgb(255, 255, 255, 26 * alpha));
        draw(context, "Accent color", x + 11, colorY + 14, withAlpha(TEXT, alpha), true);
        int swatchX = x + w - 33;
        RenderHelper.drawRoundedRect(context, swatchX, colorY + 8, 22, 22, 6,
                color(preferences.accentArgb(), Math.round(255 * alpha)));
        addHit(x, colorY, w, 38, (mx, my, button) -> openColorPicker(accentField, swatchX, colorY + 8));
        return colorY + 38;
    }

    private int renderAppearanceSlider(DrawContext context, int x, int y, int w, String label,
                                       float value, float min, float max, float alpha, Color accent,
                                       boolean percent, FloatSetter setter) {
        int h = 45;
        RenderHelper.drawRoundedRect(context, x, y, w, h, 8, surface(0.075f, 0.035f, 251 * alpha));
        RenderHelper.drawRoundedRectOutline(context, x, y, w, h, 8, rgb(255, 255, 255, 26 * alpha));
        draw(context, label, x + 11, y + 9, withAlpha(TEXT, alpha), true);
        String display = percent
                ? Math.round(value * 100f) + "%"
                : String.format(Locale.ROOT, value >= 2f ? "%.1f" : "%.2f", value);
        draw(context, display, x + w - 11 - tw(display), y + 9,
                withAlpha(preferences.accentArgb(), alpha), false);
        int sliderX = x + 11;
        int sliderY = y + 32;
        int sliderW = w - 22;
        float fraction = max <= min ? 0f : (value - min) / (max - min);
        renderSlider(context, sliderX, sliderY, sliderW, fraction, alpha, accent);
        addHit(sliderX, sliderY - 8, sliderW, 18, (mx, my, button) -> {
            dragSliderX = sliderX;
            dragSliderWidth = sliderW;
            appearanceDrag = px -> {
                float raw = min + clamp((px - sliderX) / (float) Math.max(1, sliderW), 0f, 1f) * (max - min);
                setter.set(raw);
            };
            appearanceDrag.apply(mx);
        });
        return y + h + 7;
    }

    private int renderThemesPage(DrawContext context, int mouseX, int mouseY, float alpha,
                                  Layout layout, Color accent) {
        int x = layout.contentX + 8;
        int y = layout.contentY + 8;
        draw(context, "Themes", x, y, withAlpha(TEXT, alpha), true);
        draw(context, "Pick a premium preset, then fine-tune it in Customization.",
                x, y + 17, withAlpha(TEXT_MUTED, alpha), false);
        y += 45;
        int gap = 10;
        int cardW = Math.min(200, (layout.contentWidth - 16 - gap) / 2);
        int cardH = 74;
        GlassUiPreferences.Theme[] themes = {
                GlassUiPreferences.Theme.DRAGONITE_GLASS,
                GlassUiPreferences.Theme.CLEAR_GLASS,
                GlassUiPreferences.Theme.DARK_GLASS,
                GlassUiPreferences.Theme.MIDNIGHT,
                GlassUiPreferences.Theme.ROSE,
                GlassUiPreferences.Theme.OCEAN,
                GlassUiPreferences.Theme.EMERALD,
                GlassUiPreferences.Theme.CRIMSON
        };
        for (int i = 0; i < themes.length; i++) {
            GlassUiPreferences.Theme theme = themes[i];
            int cx = x + (i % 2) * (cardW + gap);
            int cy = y + (i / 2) * (cardH + gap);
            boolean active = preferences.theme() == theme;
            float hover = hoverValue(theme, hit(mouseX, mouseY, cx, cy, cardW, cardH));
            RenderHelper.drawRoundedRect(context, cx, cy, cardW, cardH, 10,
                    surface(0.06f + 0.025f * hover, 0.04f + (active ? 0.08f : 0f), 251 * alpha));
            RenderHelper.drawRoundedRectOutline(context, cx, cy, cardW, cardH, 10,
                    rgb(accent.getRed(), accent.getGreen(), accent.getBlue(),
                            (active ? 175 : 26 + 60 * hover) * alpha));
            draw(context, theme.displayName(), cx + 12, cy + 11,
                    withAlpha(active ? preferences.accentArgb() : TEXT, alpha), true);
            draw(context, themeDescription(theme), cx + 12, cy + 28,
                    withAlpha(TEXT_MUTED, alpha), false);
            renderThemePreview(context, cx + 12, cy + 49, cardW - 24, 20, theme, alpha);
            addHit(cx, cy, cardW, cardH, (mx, my, button) -> {
                preferences.applyTheme(theme);
                accentField.setARGB(preferences.accentArgb());
                preferences.save();
            });
        }
        return y + ((themes.length + 1) / 2) * (cardH + gap);
    }

    private void renderThemePreview(DrawContext context, int x, int y, int w, int h,
                                    GlassUiPreferences.Theme theme, float alpha) {
        int previewAccent = switch (theme) {
            case CLEAR_GLASS -> 0xFF66A8FF;
            case DARK_GLASS -> 0xFFA16CFF;
            case MIDNIGHT -> 0xFF6E8BFF;
            case ROSE -> 0xFFF472B6;
            case OCEAN -> 0xFF22D3EE;
            case EMERALD -> 0xFF34D399;
            case CRIMSON -> 0xFFF43F5E;
            default -> 0xFF8B5CF6;
        };
        RenderHelper.drawRoundedRect(context, x, y, w, h, 5,
                new Color(4, 9, 16, Math.round(180 * alpha)));
        RenderHelper.drawRoundedRect(context, x + 5, y + 5, w / 2, h - 10, 3,
                color(previewAccent, Math.round(130 * alpha)));
        RenderHelper.drawRoundedRect(context, x + w - 32, y + 6, 24, 8, 4,
                color(previewAccent, Math.round(225 * alpha)));
    }

    private void renderSwitch(DrawContext context, int x, int y, boolean enabled, float progress,
                              float alpha, Color accent) {
        int w = 36;
        int h = 16;
        progress = clamp(progress, 0f, 1f);
        int track = progress > 0.01f
                ? rgba(
                        Math.round(accent.getRed() * (0.55f + 0.45f * progress)),
                        Math.round(accent.getGreen() * (0.55f + 0.45f * progress)),
                        Math.round(accent.getBlue() * (0.55f + 0.45f * progress)),
                        Math.round(250 * alpha))
                : rgba(52, 58, 74, Math.round(250 * alpha));
        // Solid fills — tiny TRIANGLE_FAN rounded rects were vanishing in the drawer.
        context.fill(x + 1, y, x + w - 1, y + h, track);
        context.fill(x, y + 1, x + w, y + h - 1, track);
        int ks = h - 4;
        int kx = x + 2 + Math.round((w - ks - 4) * progress);
        int ky = y + 2;
        int knob = rgba(247, 249, 255, Math.round(255 * alpha));
        context.fill(kx + 1, ky, kx + ks - 1, ky + ks, knob);
        context.fill(kx, ky + 1, kx + ks, ky + ks - 1, knob);
    }

    private void renderSlider(DrawContext context, int x, int y, int w, float fraction,
                              float alpha, Color accent) {
        fraction = clamp(fraction, 0f, 1f);
        int track = rgba(255, 255, 255, Math.round(45 * alpha));
        int fillCol = rgba(accent.getRed(), accent.getGreen(), accent.getBlue(), Math.round(245 * alpha));
        int knob = rgba(255, 255, 255, Math.round(255 * alpha));
        int h = 5;
        context.fill(x, y, x + w, y + h, track);
        int fw = Math.max(2, Math.round(w * fraction));
        context.fill(x, y, x + fw, y + h, fillCol);
        int hx = x + fw;
        context.fill(hx - 2, y - 3, hx + 3, y + h + 3, knob);
    }

    private void renderRangeSlider(DrawContext context, int x, int y, int w, float min, float max,
                                   float alpha, Color accent) {
        min = clamp(min, 0f, 1f);
        max = clamp(max, min, 1f);
        int track = rgba(255, 255, 255, Math.round(45 * alpha));
        int fillCol = rgba(accent.getRed(), accent.getGreen(), accent.getBlue(), Math.round(245 * alpha));
        int knob = rgba(255, 255, 255, Math.round(255 * alpha));
        int h = 5;
        context.fill(x, y, x + w, y + h, track);
        int minX = x + Math.round(w * min);
        int maxX = x + Math.round(w * max);
        context.fill(minX, y, Math.max(minX + 2, maxX), y + h, fillCol);
        context.fill(minX - 2, y - 3, minX + 3, y + h + 3, knob);
        context.fill(maxX - 2, y - 3, maxX + 3, y + h + 3, knob);
    }

    private void renderScrollBar(DrawContext context, int x, int y, int viewportHeight,
                                 float contentHeight, float scroll, float alpha) {
        if (contentHeight <= viewportHeight + 1f) return;
        float ratio = viewportHeight / contentHeight;
        int thumbH = Math.max(22, Math.round(viewportHeight * ratio));
        int travel = viewportHeight - thumbH;
        float maxScroll = contentHeight - viewportHeight;
        int thumbY = y + Math.round(travel * clamp(scroll / maxScroll, 0f, 1f));
        RenderHelper.drawRoundedRect(context, x, y, 2, viewportHeight, 1,
                new Color(255, 255, 255, Math.round(18 * alpha)));
        RenderHelper.drawRoundedRect(context, x - 1, thumbY, 4, thumbH, 2,
                color(preferences.accentArgb(), Math.round(135 * alpha)));
    }

    private List<ConfigCategoryImpl> visibleModules() {
        String needle = search.trim().toLowerCase(Locale.ROOT);
        List<ConfigCategoryImpl> out = new ArrayList<>();
        for (ConfigCategoryImpl module : modules) {
            if (module == null || !module.isGuiVisible() || !page.matches(module)) continue;
            String name = safe(module.getName(), "").toLowerCase(Locale.ROOT);
            String description = safe(module.getDescription(), "").toLowerCase(Locale.ROOT);
            if (!needle.isEmpty() && !name.contains(needle) && !description.contains(needle)) continue;
            out.add(module);
        }
        // Keep ConfigBuilderImpl registration order (same as legacy ClickGUI).
        return out;
    }

    private static String categoryIcon(ConfigCategoryImpl.Cat category) {
        if (category == null) return "misc";
        return switch (category) {
            case COMBAT -> "combat";
            case MOVEMENT -> "movement";
            case VISUALS -> "visuals";
            case PLAYER -> "player";
            case M, RENDER -> "interface";
            case MISC, OTHER -> "misc";
        };
    }

    private List<AbstractFieldBuilder> visibleSettings(ConfigCategoryImpl module) {
        List<AbstractFieldBuilder> out = new ArrayList<>();
        if (module == null || module.getSettings() == null) return out;
        for (AbstractFieldBuilder field : module.getSettings()) {
            if (field != null && field.isVisible()) out.add(field);
        }
        return out;
    }

    private int settingHeight(AbstractFieldBuilder field) {
        if (field instanceof DoubleFieldBuilder || field instanceof RangeSliderBuilder) return 48;
        if (field instanceof StringFieldBuilder) return 54;
        if (field instanceof FriendListFieldBuilder list) return 30 + Math.max(1, list.getNames().size()) * 15;
        return 34;
    }

    private void selectPage(Page value) {
        page = value;
        selectedModule = null;
        openSelector = null;
        searchFocused = false;
        activeTextField = null;
        friendInputFocused = false;
        configNameFocused = false;
        configShareFocused = false;
        moduleScroll = moduleScrollTarget = 0f;
        drawerScroll = drawerScrollTarget = 0f;
        pageScroll = pageScrollTarget = 0f;
        pageContentHeight = 0f;
        pageFade = 0f;
    }

    private void openColorPicker(ColorFieldBuilder field, int anchorX, int anchorY) {
        if (openColorField == field) {
            openColorField = null;
            return;
        }
        openColorField = field;
        PrestigeColorPicker.stateOf(field).syncFromField();
        colorPopupX = PrestigeColorPicker.clampPopupX(anchorX - PrestigeColorPicker.POPUP_W - 8, width);
        colorPopupY = PrestigeColorPicker.clampPopupY(anchorY - 18, height, field);
    }

    private void openSelectorPopup(EnumSelectorBuilder selector, int anchorX, int anchorY, int boxW) {
        if (openSelector == selector) {
            openSelector = null;
            return;
        }
        openSelector = selector;
        int popupW = Math.max(boxW, widestModeWidth(selector) + 26);
        selectorPopupW = popupW;
        selectorPopupX = MathHelper.clamp(anchorX + boxW - popupW, 4, Math.max(4, width - popupW - 4));
        int popupH = selector.getModes().size() * 22 + 8;
        selectorPopupY = MathHelper.clamp(anchorY, 4, Math.max(4, height - popupH - 4));
    }

    private int widestModeWidth(EnumSelectorBuilder selector) {
        int widest = 0;
        for (String mode : selector.getModes()) {
            widest = Math.max(widest, tw(mode));
        }
        return widest;
    }

    private void renderSelectorPopup(DrawContext context, int mouseX, int mouseY, float alpha, Color accent) {
        List<String> modes = openSelector.getModes();
        int popupH = modes.size() * 22 + 8;
        RenderHelper.drawDropShadow(context, selectorPopupX, selectorPopupY, selectorPopupW, popupH, 7);
        RenderHelper.drawRoundedRect(context, selectorPopupX, selectorPopupY, selectorPopupW, popupH, 7,
                new Color(6, 11, 17, Math.round(232 * alpha)));
        RenderHelper.drawRoundedRectOutline(context, selectorPopupX, selectorPopupY, selectorPopupW, popupH, 7,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), Math.round(70 * alpha)));
        String current = safe(openSelector.get(), "");
        int rowY = selectorPopupY + 4;
        for (String mode : modes) {
            boolean hovered = hit(mouseX, mouseY, selectorPopupX, rowY, selectorPopupW, 22);
            boolean selected = mode.equals(current);
            if (hovered || selected) {
                RenderHelper.drawRoundedRect(context, selectorPopupX + 3, rowY, selectorPopupW - 6, 22, 5,
                        new Color(accent.getRed(), accent.getGreen(), accent.getBlue(),
                                Math.round((selected ? 60 : 28) * alpha)));
            }
            draw(context, truncate(mode, selectorPopupW - 20), selectorPopupX + 11, rowY + 7,
                    withAlpha(selected ? TEXT : TEXT_SOFT, alpha), selected);
            rowY += 22;
        }
    }

    private boolean handleSelectorPopupClick(int mx, int my) {
        List<String> modes = openSelector.getModes();
        int popupH = modes.size() * 22 + 8;
        if (!hit(mx, my, selectorPopupX, selectorPopupY, selectorPopupW, popupH)) {
            openSelector = null;
            return false;
        }
        int index = (my - selectorPopupY - 4) / 22;
        if (index >= 0 && index < modes.size()) {
            openSelector.set(modes.get(index));
        }
        openSelector = null;
        return true;
    }

    private void addFriend(FriendManager manager) {
        String value = friendInput.trim();
        if (!value.isEmpty()) {
            manager.addFriend(value);
            friendInput = "";
        }
    }

    private void applyDouble(DoubleFieldBuilder field, int mouseX) {
        double fraction = clamp((mouseX - dragSliderX) / (double) Math.max(1, dragSliderWidth), 0d, 1d);
        double value = field.getMin() + fraction * (field.getMax() - field.getMin());
        double increment = field.getIncrement();
        if (increment > 0d) value = Math.round(value / increment) * increment;
        field.set(value);
    }

    private void applyRange(RangeSliderBuilder field, int mouseX) {
        double fraction = clamp((mouseX - dragSliderX) / (double) Math.max(1, dragSliderWidth), 0d, 1d);
        double value = field.getMin() + fraction * (field.getMax() - field.getMin());
        double increment = field.getIncrement();
        if (increment > 0d) value = Math.round(value / increment) * increment;
        if (draggedRangeMin) field.setMinVal(value); else field.setMaxVal(value);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (openSelector != null && button == 0) {
            if (handleSelectorPopupClick(mx, my)) return true;
        }
        if (openColorField != null) {
            PrestigeColorPicker.State state = PrestigeColorPicker.stateOf(openColorField);
            if (PrestigeColorPicker.hitPopup(colorPopupX, colorPopupY, mx, my, openColorField)) {
                return PrestigeColorPicker.mouseClickedPopup(state, colorPopupX, colorPopupY, mx, my, button);
            }
            if (button == 0) openColorField = null;
        }
        if (bindingModule != null) {
            if (bindCaptureDelay > 0) return true;
            bindingModule.setKeybind(button <= 7 ? button : -1);
            bindingModule.setBinding(false);
            bindingModule = null;
            manager.saveKeybinds();
            return true;
        }
        for (int i = hitBoxes.size() - 1; i >= 0; i--) {
            HitBox box = hitBoxes.get(i);
            if (!box.contains(mx, my)) continue;
            MouseAction action = button == 1 ? box.right : box.left;
            if (action != null) {
                action.accept(mx, my, button);
                return true;
            }
        }
        searchFocused = false;
        friendInputFocused = false;
        configNameFocused = false;
        configShareFocused = false;
        activeTextField = null;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        int mx = (int) mouseX;
        if (draggedDouble != null) {
            applyDouble(draggedDouble, mx);
            return true;
        }
        if (draggedRange != null) {
            applyRange(draggedRange, mx);
            return true;
        }
        if (appearanceDrag != null) {
            appearanceDrag.apply(mx);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggedDouble = null;
        draggedRange = null;
        appearanceDrag = null;
        guiScaleDragOriginX = null;
        guiScaleDragOriginY = null;
        if (appearanceDirty) {
            preferences.save();
            appearanceDirty = false;
        }
        PrestigeColorPicker.mouseReleasedAll();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (lastLayout == null) return false;
        if (openSelector != null) return true;
        if (selectedModule != null && lastLayout.settingsW > 0
                && mouseX >= lastLayout.settingsX && mouseX < lastLayout.settingsX + lastLayout.settingsW
                && mouseY >= lastLayout.settingsY && mouseY < lastLayout.settingsY + lastLayout.settingsH) {
            float max = Math.max(0f, drawerContentHeight - Math.max(40, lastLayout.settingsH - 90));
            drawerScrollTarget = clamp(drawerScrollTarget - (float) verticalAmount * 36f, 0f, max);
            return true;
        }
        if (mouseX >= lastLayout.x && mouseX < lastLayout.x + lastLayout.sidebarW
                && mouseY >= lastLayout.y + HEADER_H && mouseY < lastLayout.y + lastLayout.height) {
            float max = Math.max(0f, sidebarContentHeight - (lastLayout.height - HEADER_H - 84));
            sidebarScrollTarget = clamp(sidebarScrollTarget - (float) verticalAmount * 30f, 0f, max);
            return true;
        }
        if (page.isModulePage() && mouseX >= lastLayout.contentX) {
            float max = Math.max(0f, moduleContentHeight - lastLayout.contentHeight);
            moduleScrollTarget = clamp(moduleScrollTarget - (float) verticalAmount * 34f, 0f, max);
            return true;
        }
        if (!page.isModulePage() && mouseX >= lastLayout.contentX - 6) {
            float max = Math.max(0f, pageContentHeight - (lastLayout.height - HEADER_H - 14));
            pageScrollTarget = clamp(pageScrollTarget - (float) verticalAmount * 34f, 0f, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (bindingModule != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                bindingModule.setBinding(false);
                bindingModule = null;
                return true;
            }
            bindingModule.setKeybind(keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE ? -1 : keyCode);
            bindingModule.setBinding(false);
            bindingModule = null;
            manager.saveKeybinds();
            return true;
        }
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { searchFocused = false; selectAll = false; return true; }
            if (keyCode == GLFW.GLFW_KEY_A && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                selectAll = !search.isEmpty();
                lastEditMillis = System.currentTimeMillis();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                if (selectAll) {
                    search = "";
                } else if (!search.isEmpty()) {
                    search = search.substring(0, search.length() - 1);
                }
                bumpInput();
                moduleScroll = moduleScrollTarget = 0f;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) { searchFocused = false; selectAll = false; return true; }
        }
        if (configNameFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { configNameFocused = false; selectAll = false; return true; }
            if (keyCode == GLFW.GLFW_KEY_A && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                selectAll = !configNameInput.isEmpty();
                lastEditMillis = System.currentTimeMillis();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                if (selectAll) {
                    configNameInput = "";
                } else if (!configNameInput.isEmpty()) {
                    configNameInput = configNameInput.substring(0, configNameInput.length() - 1);
                }
                bumpInput();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) { saveConfigProfile(); return true; }
        }
        if (configShareFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { configShareFocused = false; selectAll = false; return true; }
            if (keyCode == GLFW.GLFW_KEY_A && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                selectAll = !configShareInput.isEmpty();
                lastEditMillis = System.currentTimeMillis();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                if (selectAll) {
                    configShareInput = "";
                } else if (!configShareInput.isEmpty()) {
                    configShareInput = configShareInput.substring(0, configShareInput.length() - 1);
                }
                bumpInput();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) { importConfigShare(); return true; }
            if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && client != null) {
                String clip = safe(client.keyboard.getClipboard(), "");
                if (!clip.isEmpty()) {
                    configShareInput = clip.length() > 512 ? clip.substring(0, 512) : clip;
                }
                bumpInput();
                return true;
            }
        }
        if (friendInputFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { friendInputFocused = false; selectAll = false; return true; }
            if (keyCode == GLFW.GLFW_KEY_A && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                selectAll = !friendInput.isEmpty();
                lastEditMillis = System.currentTimeMillis();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                if (selectAll) {
                    friendInput = "";
                } else if (!friendInput.isEmpty()) {
                    friendInput = friendInput.substring(0, friendInput.length() - 1);
                }
                bumpInput();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                FriendManager friends = manager.getModuleByClass(FriendManager.class);
                if (friends != null) addFriend(friends);
                return true;
            }
        }
        if (activeTextField != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                activeTextField = null;
                selectAll = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_A && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                selectAll = !safe(activeTextField.get(), "").isEmpty();
                lastEditMillis = System.currentTimeMillis();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                String value = safe(activeTextField.get(), "");
                if (selectAll) {
                    activeTextField.set("");
                } else if (!value.isEmpty()) {
                    activeTextField.set(value.substring(0, value.length() - 1));
                }
                bumpInput();
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || GuiKeybinds.isOpenGuiKey(keyCode)) {
            if (openSelector != null) {
                openSelector = null;
            } else if (selectedModule != null) {
                selectedModule = null;
            } else {
                closing = true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (chr < 32 || chr == 127) return false;
        if (searchFocused && search.length() < 64) {
            search = selectAll ? String.valueOf(chr) : search + chr;
            bumpInput();
            moduleScroll = moduleScrollTarget = 0f;
            return true;
        }
        if (configNameFocused && configNameInput.length() < 32) {
            configNameInput = selectAll ? String.valueOf(chr) : configNameInput + chr;
            bumpInput();
            return true;
        }
        if (configShareFocused && configShareInput.length() < 512) {
            configShareInput = selectAll ? String.valueOf(chr) : configShareInput + chr;
            bumpInput();
            return true;
        }
        if (friendInputFocused && friendInput.length() < 32) {
            friendInput = selectAll ? String.valueOf(chr) : friendInput + chr;
            bumpInput();
            return true;
        }
        if (activeTextField != null) {
            String value = safe(activeTextField.get(), "");
            if (selectAll) {
                activeTextField.set(String.valueOf(chr));
            } else if (value.length() < activeTextField.getMaxLength()) {
                activeTextField.set(value + chr);
            }
            bumpInput();
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void close() {
        closing = true;
    }

    private void closeImmediately() {
        preferences.save();
        if (manager != null) manager.saveKeybinds();
        for (ConfigCategoryImpl module : modules) {
            if (module != null) {
                module.setBinding(false);
                module.purgeCache();
            }
        }
        if (client != null) client.setScreen(null);
    }

    @Override
    public void removed() {
        restoreMenuBlur();
        preferences.save();
        super.removed();
    }

    private void restoreMenuBlur() {
        if (client != null && savedMenuBlur != null) {
            try {
                client.options.getMenuBackgroundBlurriness().setValue(savedMenuBlur);
            } catch (Throwable ignored) {
            }
            savedMenuBlur = null;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private Layout currentLayout() {
        boolean drawerOpen = selectedModule != null || drawerAnimation > 0.02f;
        Integer originX = guiScaleDragOriginX;
        Integer originY = guiScaleDragOriginY;
        float scale = preferences.guiScale();
        if (layoutCache != null
                && layoutCacheScale == scale
                && layoutCacheW == width
                && layoutCacheH == height
                && layoutCacheDrawer == drawerOpen
                && java.util.Objects.equals(layoutCacheOriginX, originX)
                && java.util.Objects.equals(layoutCacheOriginY, originY)) {
            return layoutCache;
        }
        Layout layout = calculateLayout();
        layoutCache = layout;
        layoutCacheScale = scale;
        layoutCacheW = width;
        layoutCacheH = height;
        layoutCacheDrawer = drawerOpen;
        layoutCacheOriginX = originX;
        layoutCacheOriginY = originY;
        return layout;
    }

    private Layout calculateLayout() {
        float scale = preferences.guiScale();
        int panelW = Math.round(BASE_W * scale);
        int panelH = Math.round(BASE_H * scale);
        int settingsW = (selectedModule != null || drawerAnimation > 0.02f)
                ? Math.round(SETTINGS_W * scale) : 0;
        int gap = settingsW > 0 ? 10 : 0;
        int totalW = panelW + gap + settingsW;
        int margin = 16;
        panelW = Math.min(panelW, Math.max(320, width - margin * 2 - gap - settingsW));
        panelH = Math.min(panelH, Math.max(280, height - margin * 2));
        totalW = panelW + gap + settingsW;
        // Sidebar shrinks with the panel so the left bar always fits on screen,
        // even at small GUI scales or narrow windows.
        int sidebarW = MathHelper.clamp(Math.round(SIDEBAR_W * scale),
                MIN_SIDEBAR_W, Math.max(MIN_SIDEBAR_W, panelW / 3));
        int x = Math.max(margin, (width - totalW) / 2);
        int y = Math.max(margin, (height - panelH) / 2);
        // While dragging GUI scale, keep the panel origin fixed so the slider
        // doesn't run away under the cursor as the window recenters.
        if (guiScaleDragOriginX != null && guiScaleDragOriginY != null) {
            x = MathHelper.clamp(guiScaleDragOriginX, margin, Math.max(margin, width - panelW - margin));
            y = MathHelper.clamp(guiScaleDragOriginY, margin, Math.max(margin, height - panelH - margin));
        }
        int contentX = x + sidebarW + 10;
        int contentY = y + HEADER_H + 10;
        int contentW = panelW - sidebarW - 20;
        int contentH = panelH - HEADER_H - 20;
        int settingsX = x + panelW + gap;
        int settingsY = y;
        int settingsH = panelH;
        return new Layout(x, y, panelW, panelH, sidebarW, contentX, contentY, Math.max(120, contentW), contentH,
                settingsX, settingsY, settingsW, settingsH);
    }

    private String discordIdentity() {
        IntegrationHandler.DiscordUser user = discordUser;
        if (user != null && user.getFullUsername() != null && !user.getFullUsername().isBlank()) {
            return user.getFullUsername();
        }
        if (client != null && client.getSession() != null) {
            return client.getSession().getUsername();
        }
        return "Unknown#0000";
    }

    private String keyLabel(ConfigCategoryImpl module) {
        int key = module.getKeybind();
        if (key < 0 || key == GLFW.GLFW_KEY_UNKNOWN) return "None";
        if (key <= 7) {
            String[] mouse = {"LMB", "RMB", "MMB", "Mouse4", "Mouse5", "Mouse6", "Mouse7", "Mouse8"};
            return mouse[key];
        }
        try {
            return InputUtil.fromKeyCode(key, 0).getLocalizedText().getString();
        } catch (RuntimeException ignored) {
            return "Key " + key;
        }
    }

    private String format(DoubleFieldBuilder value) {
        return format(value.get()) + safe(value.getSuffix(), "");
    }

    private static String format(double value) {
        if (Math.rint(value) == value) return Long.toString((long) value);
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private String truncate(String value, int maxWidth) {
        value = safe(value, "");
        if (tw(value) <= maxWidth) return value;
        String ellipsis = "...";
        int target = Math.max(0, maxWidth - tw(ellipsis));
        int end = value.length();
        while (end > 0 && tw(value.substring(0, end)) > target) end--;
        return value.substring(0, end) + ellipsis;
    }

    private Style uiFont(boolean semibold) {
        // Late inject: Inter is not in ResourceManager → tofu boxes. Use default then.
        if (!InjectedClientAssets.fontsAvailable(client)) {
            return Style.EMPTY;
        }
        return Style.EMPTY.withFont(semibold ? FONT_INTER_SB : FONT_INTER);
    }

    private void draw(DrawContext context, String text, int x, int y, int argb, boolean semibold) {
        context.drawText(textRenderer,
                Text.literal(safe(text, "")).setStyle(uiFont(semibold)),
                x, y, argb, false);
    }

    private int tw(String text) {
        return textRenderer.getWidth(
                Text.literal(safe(text, "")).setStyle(uiFont(false)));
    }

    private void drawIcon(DrawContext context, String name, int x, int y, int size, int argb) {
        int index = -1;
        for (int i = 0; i < ICON_ORDER.length; i++) {
            if (ICON_ORDER[i].equals(name)) {
                index = i;
                break;
            }
        }
        if (index < 0) return;
        float u = (index % ICON_COLUMNS) * ICON_CELL;
        float v = (index / ICON_COLUMNS) * ICON_CELL;
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(
                ((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f);
        context.drawTexture(ICONS_TEXTURE, x, y, size, size, u, v,
                ICON_CELL, ICON_CELL, ICON_COLUMNS * ICON_CELL, 2 * ICON_CELL);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void drawAvatar(DrawContext context, int x, int y, int size, float alpha) {
        Identifier avatar = DiscordAvatarTexture.textureId();
        if (avatar != null) {
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, clamp(alpha, 0f, 1f));
            context.drawTexture(avatar, x, y, size, size, 0f, 0f,
                    DiscordAvatarTexture.texWidth(), DiscordAvatarTexture.texHeight(),
                    DiscordAvatarTexture.texWidth(), DiscordAvatarTexture.texHeight());
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            return;
        }
        RenderHelper.drawRoundedRect(context, x, y, size, size, size / 2,
                new Color(88, 101, 242, Math.round(160 * alpha)));
        String identity = discordIdentity();
        String initial = identity.isBlank() ? "?" : identity.substring(0, 1).toUpperCase(Locale.ROOT);
        draw(context, initial, x + size / 2 - tw(initial) / 2, y + size / 2 - 4, withAlpha(TEXT, alpha), true);
    }

    private void drawPresenceDot(DrawContext context, int x, int y, float alpha) {
        boolean online = discordUser != null;
        RenderHelper.drawRoundedRect(context, x - 1, y - 1, 8, 8, 4,
                new Color(5, 10, 17, Math.round(230 * alpha)));
        RenderHelper.drawRoundedRect(context, x, y, 6, 6, 3,
                color(online ? GREEN : TEXT_FAINT, Math.round(255 * alpha)));
    }

    private float hoverValue(Object key, boolean hovered) {
        float previous = hoverAnimations.getOrDefault(key, hovered ? 1f : 0f);
        float next = previous + ((hovered ? 1f : 0f) - previous) * 0.42f;
        hoverAnimations.put(key, next);
        return next;
    }

    private void addHit(int x, int y, int w, int h, MouseAction left) {
        addHit(x, y, w, h, left, null);
    }

    private void addHit(int x, int y, int w, int h, MouseAction left, MouseAction right) {
        hitBoxes.add(new HitBox(x, y + hitOffsetY, w, h, left, right));
    }

    private static String themeDescription(GlassUiPreferences.Theme theme) {
        return switch (theme) {
            case DRAGONITE_GLASS -> "Signature violet, deep contrast";
            case CLEAR_GLASS -> "Platinum light, soft neutral";
            case DARK_GLASS -> "Obsidian black, high contrast";
            case MIDNIGHT -> "Deep navy, calm and quiet";
            case ROSE -> "Rose gold, warm highlight";
            case OCEAN -> "Azure cyan, crisp and cool";
            case EMERALD -> "Emerald green, clean tech";
            case CRIMSON -> "Crimson red, aggressive";
            case CUSTOM -> "Your current values";
        };
    }

    private static String safe(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static float approach(float value, float target, float speed, float dt) {
        if (dt <= 0f) return value;
        float next = value + (target - value) * (1f - (float) Math.exp(-speed * dt));
        return Math.abs(target - next) < 0.001f ? target : next;
    }

    private static float easeOut(float value) {
        value = clamp(value, 0f, 1f);
        float inverse = 1f - value;
        return 1f - inverse * inverse * inverse;
    }

    private static float easeOutCubic(float value) {
        return easeOut(value);
    }

    private static double normalized(double value, double min, double max) {
        return max <= min ? 0d : clamp((value - min) / (max - min), 0d, 1d);
    }

    private static int withAlpha(int argb, float alpha) {
        int base = (argb >>> 24) & 0xFF;
        return (Math.round(base * clamp(alpha, 0f, 1f)) << 24) | (argb & 0x00FFFFFF);
    }

    private static int rgba(int red, int green, int blue, int alpha) {
        return (MathHelper.clamp(alpha, 0, 255) << 24)
                | (MathHelper.clamp(red, 0, 255) << 16)
                | (MathHelper.clamp(green, 0, 255) << 8)
                | MathHelper.clamp(blue, 0, 255);
    }

    private static Color color(int argb, int alpha) {
        return new Color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, MathHelper.clamp(alpha, 0, 255));
    }

    /** Safe AWT color — clamps alpha so enabled/hovered cards cannot crash render. */
    static Color rgb(int red, int green, int blue, float alpha) {
        return new Color(
                MathHelper.clamp(red, 0, 255),
                MathHelper.clamp(green, 0, 255),
                MathHelper.clamp(blue, 0, 255),
                MathHelper.clamp(Math.round(alpha), 0, 255));
    }

    static int moduleCardAlpha(float cardOpacity, float hover, float enabledAnim, float alpha) {
        return MathHelper.clamp(Math.round(
                (110 + 130 * cardOpacity + 24 * hover + 36 * enabledAnim) * alpha), 0, 255);
    }

    private static int mix(int base, int accent, float amount) {
        amount = clamp(amount, 0f, 1f);
        return MathHelper.clamp(Math.round(base + (accent - base) * amount), 0, 255);
    }

    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    private enum Page {
        ALL("All Modules", "all"),
        COMBAT("Combat", "combat"),
        MOVEMENT("Movement", "movement"),
        PLAYER("Player", "player"),
        VISUALS("Visuals", "visuals"),
        MISC("Misc", "misc"),
        INTERFACE("Interface", "interface"),
        CONFIGS("Configs", "folder"),
        FRIENDS("Friends", "friends"),
        CUSTOMIZATION("Customization", "brush"),
        THEMES("Themes", "shirt"),
        DISCORD("Discord Sync", "discord");

        private final String title;
        private final String icon;

        Page(String title, String icon) {
            this.title = title;
            this.icon = icon;
        }

        boolean isModulePage() {
            return ordinal() <= INTERFACE.ordinal();
        }

        boolean matches(ConfigCategoryImpl module) {
            ConfigCategoryImpl.Cat category = module.getCategory();
            String name = safe(module.getName(), "").toLowerCase(Locale.ROOT);
            return switch (this) {
                case ALL -> true;
                case COMBAT -> category == ConfigCategoryImpl.Cat.COMBAT;
                case MOVEMENT -> category == ConfigCategoryImpl.Cat.MOVEMENT;
                case PLAYER -> category == ConfigCategoryImpl.Cat.PLAYER;
                case VISUALS -> category == ConfigCategoryImpl.Cat.VISUALS
                        && !name.contains("hud") && !name.contains("arraylist") && !name.contains("bubble");
                case MISC -> category == ConfigCategoryImpl.Cat.MISC || category == ConfigCategoryImpl.Cat.OTHER;
                case INTERFACE -> category == ConfigCategoryImpl.Cat.M || category == ConfigCategoryImpl.Cat.RENDER
                        || name.contains("hud") || name.contains("arraylist") || name.contains("bubble");
                default -> false;
            };
        }

        static Page[] modulePages() {
            return new Page[]{ALL, COMBAT, MOVEMENT, PLAYER, VISUALS, MISC, INTERFACE};
        }

        static Page[] generalPages() {
            return new Page[]{CONFIGS, FRIENDS, CUSTOMIZATION, THEMES};
        }
    }

    private record Layout(int x, int y, int width, int height, int sidebarW,
                          int contentX, int contentY, int contentWidth, int contentHeight,
                          int settingsX, int settingsY, int settingsW, int settingsH) {}

    private record HitBox(int x, int y, int width, int height, MouseAction left, MouseAction right) {
        boolean contains(int mouseX, int mouseY) {
            return hit(mouseX, mouseY, x, y, width, height);
        }
    }

    @FunctionalInterface
    private interface MouseAction {
        void accept(int mouseX, int mouseY, int button);
    }

    @FunctionalInterface
    private interface DragAction {
        void apply(int mouseX);
    }

    @FunctionalInterface
    private interface FloatSetter {
        void set(float value);
    }
}
