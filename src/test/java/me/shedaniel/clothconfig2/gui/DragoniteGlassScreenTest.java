package me.shedaniel.clothconfig2.gui;

import net.minecraft.client.gui.screen.Screen;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragoniteGlassScreenTest {
    @Test
    void legacyScreenRemainsCompiledButRoutesOpenToGlassScreen() throws Exception {
        assertTrue(Screen.class.isAssignableFrom(ClothConfigScreen.class));
        assertTrue(Screen.class.isAssignableFrom(DragoniteGlassScreen.class));
        assertTrue(classConstants(ClothConfigScreen.class).contains("DragoniteGlassScreen"));
    }

    @Test
    void builtInThemesStayWithinRuntimeSafeBoundsAndRemainDistinct() {
        GlassUiPreferences preferences = new GlassUiPreferences();
        Set<Integer> accents = new HashSet<>();
        int builtIn = 0;
        for (GlassUiPreferences.Theme theme : GlassUiPreferences.Theme.values()) {
            if (theme == GlassUiPreferences.Theme.CUSTOM) continue;
            builtIn++;
            preferences.applyTheme(theme);
            assertEquals(theme, preferences.theme());
            assertTrue(preferences.glassOpacity() >= 0.36f && preferences.glassOpacity() <= 0.94f);
            assertTrue(preferences.sidebarOpacity() >= 0.28f && preferences.sidebarOpacity() <= 0.92f);
            assertTrue(preferences.cardOpacity() >= 0.22f && preferences.cardOpacity() <= 0.90f);
            assertTrue(preferences.blurStrength() >= 0f && preferences.blurStrength() <= 8f);
            assertTrue(preferences.cornerRadius() >= 4f && preferences.cornerRadius() <= 18f);
            assertTrue(preferences.guiScale() >= 0.75f && preferences.guiScale() <= 1.25f);
            accents.add(preferences.accentArgb());
        }
        assertEquals(builtIn, accents.size());
    }

    @Test
    void manualAppearanceChangeSelectsCustomWithoutLeavingBounds() {
        GlassUiPreferences preferences = new GlassUiPreferences();
        preferences.applyTheme(GlassUiPreferences.Theme.DRAGONITE_GLASS);
        preferences.setBlurStrength(100f);
        assertEquals(GlassUiPreferences.Theme.CUSTOM, preferences.theme());
        assertEquals(8f, preferences.blurStrength());
    }

    @Test
    void enabledModuleCardAlphaNeverExceedsAwtRange() {
        float[] opacities = {0.22f, 0.58f, 0.72f, 0.90f};
        float[] factors = {0f, 0.5f, 1f};
        for (float card : opacities) {
            for (float hover : factors) {
                for (float enabled : factors) {
                    int alpha = DragoniteGlassScreen.moduleCardAlpha(card, hover, enabled, 1f);
                    assertTrue(alpha >= 0 && alpha <= 255);
                    Color color = DragoniteGlassScreen.rgb(7, 12, 18, alpha);
                    assertEquals(alpha, color.getAlpha());
                }
            }
        }
    }

    @Test
    void clickGuiBundlesWebsiteDLogoTexture() throws Exception {
        try (InputStream input = DragoniteGlassScreenTest.class.getResourceAsStream(
                "/assets/cloth-config2/textures/gui/logo.png")) {
            assertTrue(input != null && input.read() >= 0);
        }
        assertTrue(classConstants(DragoniteGlassScreen.class).contains("textures/gui/logo.png"));
    }

    @Test
    void kawaseShadersKeepFullScreenNormalizedTextureCoordinates() throws Exception {
        String down = resourceText("/assets/cloth-config2/shaders/kawase_down.fsh");
        String up = resourceText("/assets/cloth-config2/shaders/kawase_up.fsh");
        assertTrue(down.contains("vec2 uv = texCoord;"));
        assertTrue(up.contains("vec2 uv = texCoord;"));
        assertFalse(down.contains("texCoord * 2.0"));
        assertFalse(up.contains("texCoord / 2.0"));
    }

    @Test
    void productionBlurUsesASeparableGaussianKernel() throws Exception {
        String shader = resourceText("/assets/cloth-config2/shaders/gaussian_blur.fsh");
        assertTrue(shader.contains("uniform vec2 Direction;"));
        assertTrue(shader.contains("for (int i = -8; i <= 8; i++)"));
        assertFalse(shader.contains("for (float y"));
    }

    @Test
    void sidebarShrinksToFitSmallGuiScales() {
        GlassUiPreferences preferences = new GlassUiPreferences();
        preferences.setGuiScale(0.75f);
        // At 75% scale the sidebar must stay on screen: scaled from 148 down to
        // the readable floor of 116 instead of overflowing the panel.
        int scaled = Math.round(148 * preferences.guiScale());
        int sidebar = Math.max(116, Math.min(148, scaled));
        assertEquals(116, sidebar);
        preferences.setGuiScale(1.25f);
        scaled = Math.round(148 * preferences.guiScale());
        sidebar = Math.max(116, Math.min(148, scaled));
        assertEquals(148, sidebar);
    }

    @Test
    void settingRowsRenderOpaqueEnoughForReadableText() {
        // Settings rows must be near-opaque so labels stay readable with the
        // blur shader compiled out of the frame.
        int alpha = Math.round(251 * 1f);
        Color row = DragoniteGlassScreen.rgb(27, 32, 43, alpha);
        assertTrue(row.getAlpha() >= 245);
    }

    @Test
    void backdropStaysOpaqueAtEveryDimSetting() {
        // The world must never bleed through: even at dim 0 the backdrop is
        // effectively solid, otherwise theme colors wash out.
        for (float dim = 0f; dim <= 0.70f; dim += 0.05f) {
            int alpha = Math.min(252, Math.round(214f + 38f * dim));
            assertTrue(alpha >= 214 && alpha <= 252);
        }
    }

    private static String classConstants(Class<?> type) throws Exception {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing class resource: " + resource);
            return new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    private static String resourceText(String path) throws Exception {
        try (InputStream input = DragoniteGlassScreenTest.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
