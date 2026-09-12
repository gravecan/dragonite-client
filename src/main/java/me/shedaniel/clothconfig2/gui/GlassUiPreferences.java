package me.shedaniel.clothconfig2.gui;

/** Session appearance for the ClickGUI. Defaults live in the client; no disk file. */
public final class GlassUiPreferences {
    public enum Theme {
        DRAGONITE_GLASS("Dragonite"),
        CLEAR_GLASS("Platinum"),
        DARK_GLASS("Obsidian"),
        MIDNIGHT("Midnight"),
        ROSE("Rose Gold"),
        OCEAN("Azure"),
        EMERALD("Emerald"),
        CRIMSON("Crimson"),
        CUSTOM("Custom");

        private final String displayName;

        Theme(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    private static final int DEFAULT_ACCENT = 0xFF8B5CF6;
    private static final int DEFAULT_SURFACE = 0xFF12101B;

    /** One in-memory instance for the whole client session. */
    private static final GlassUiPreferences SESSION = new GlassUiPreferences();

    private Theme theme = Theme.DRAGONITE_GLASS;
    private int accentArgb = DEFAULT_ACCENT;
    private int surfaceArgb = DEFAULT_SURFACE;
    private float glassOpacity = 0.88f;
    private float sidebarOpacity = 0.86f;
    private float cardOpacity = 0.72f;
    private float blurStrength = 0.0f;
    private float cornerRadius = 11.0f;
    private float density = 0.92f;
    private float animationStrength = 1.15f;
    private float backgroundDim = 0.28f;
    private float guiScale = 0.90f;

    public static GlassUiPreferences load() {
        return SESSION;
    }

    /** No-op — session memory only; resets next launch. */
    public void save() {
    }

    /** Themes never resize the GUI: keep the user's scale, just re-clamp it. */
    private void keepUserGuiScale() {
        guiScale = clamp(guiScale, 0.75f, 1.25f);
    }

    public void applyTheme(Theme value) {
        theme = value == null ? Theme.DRAGONITE_GLASS : value;
        switch (theme) {
            case DRAGONITE_GLASS -> {
                accentArgb = 0xFF8B5CF6;
                surfaceArgb = 0xFF12101B;
                glassOpacity = 0.88f;
                sidebarOpacity = 0.86f;
                cardOpacity = 0.72f;
                blurStrength = 0.0f;
                cornerRadius = 11f;
                density = 0.92f;
                animationStrength = 1.15f;
                backgroundDim = 0.28f;
                keepUserGuiScale();
            }
            case CLEAR_GLASS -> {
                accentArgb = 0xFF66A8FF;
                surfaceArgb = 0xFF262B36;
                glassOpacity = 0.72f;
                sidebarOpacity = 0.68f;
                cardOpacity = 0.55f;
                blurStrength = 0.0f;
                cornerRadius = 13f;
                density = 0.90f;
                animationStrength = 1.20f;
                backgroundDim = 0.16f;
                keepUserGuiScale();
            }
            case DARK_GLASS -> {
                accentArgb = 0xFFA16CFF;
                surfaceArgb = 0xFF08090C;
                glassOpacity = 0.92f;
                sidebarOpacity = 0.90f;
                cardOpacity = 0.80f;
                blurStrength = 0.0f;
                cornerRadius = 10f;
                density = 0.94f;
                animationStrength = 1.05f;
                backgroundDim = 0.36f;
                keepUserGuiScale();
            }
            case MIDNIGHT -> {
                accentArgb = 0xFF6E8BFF;
                surfaceArgb = 0xFF0A1128;
                glassOpacity = 0.94f;
                sidebarOpacity = 0.92f;
                cardOpacity = 0.84f;
                blurStrength = 0.0f;
                cornerRadius = 8f;
                density = 0.88f;
                animationStrength = 1.0f;
                backgroundDim = 0.42f;
                keepUserGuiScale();
            }
            case ROSE -> {
                accentArgb = 0xFFF472B6;
                surfaceArgb = 0xFF1B1016;
                glassOpacity = 0.86f;
                sidebarOpacity = 0.82f;
                cardOpacity = 0.68f;
                blurStrength = 0.0f;
                cornerRadius = 12f;
                density = 0.92f;
                animationStrength = 1.18f;
                backgroundDim = 0.26f;
                keepUserGuiScale();
            }
            case OCEAN -> {
                accentArgb = 0xFF22D3EE;
                surfaceArgb = 0xFF07161D;
                glassOpacity = 0.84f;
                sidebarOpacity = 0.80f;
                cardOpacity = 0.66f;
                blurStrength = 0.0f;
                cornerRadius = 12f;
                density = 0.90f;
                animationStrength = 1.18f;
                backgroundDim = 0.24f;
                keepUserGuiScale();
            }
            case EMERALD -> {
                accentArgb = 0xFF34D399;
                surfaceArgb = 0xFF081712;
                glassOpacity = 0.86f;
                sidebarOpacity = 0.82f;
                cardOpacity = 0.68f;
                blurStrength = 0.0f;
                cornerRadius = 11f;
                density = 0.92f;
                animationStrength = 1.15f;
                backgroundDim = 0.26f;
                keepUserGuiScale();
            }
            case CRIMSON -> {
                accentArgb = 0xFFF43F5E;
                surfaceArgb = 0xFF1B0B0F;
                glassOpacity = 0.88f;
                sidebarOpacity = 0.84f;
                cardOpacity = 0.72f;
                blurStrength = 0.0f;
                cornerRadius = 10f;
                density = 0.93f;
                animationStrength = 1.12f;
                backgroundDim = 0.28f;
                keepUserGuiScale();
            }
            case CUSTOM -> {
                // Preserve the current values when switching to custom.
            }
        }
    }

    public Theme theme() { return theme; }
    public int accentArgb() { return accentArgb; }
    public int surfaceArgb() { return surfaceArgb; }
    public float glassOpacity() { return glassOpacity; }
    public float sidebarOpacity() { return sidebarOpacity; }
    public float cardOpacity() { return cardOpacity; }
    public float blurStrength() { return blurStrength; }
    public float cornerRadius() { return cornerRadius; }
    public float density() { return density; }
    public float animationStrength() { return animationStrength; }
    public float backgroundDim() { return backgroundDim; }
    public float guiScale() { return guiScale; }

    public void setAccentArgb(int value) { accentArgb = 0xFF000000 | (value & 0x00FFFFFF); custom(); }
    public void setSurfaceArgb(int value) { surfaceArgb = 0xFF000000 | (value & 0x00FFFFFF); custom(); }
    public void setGlassOpacity(float value) { glassOpacity = clamp(value, 0.36f, 0.94f); custom(); }
    public void setSidebarOpacity(float value) { sidebarOpacity = clamp(value, 0.28f, 0.92f); custom(); }
    public void setCardOpacity(float value) { cardOpacity = clamp(value, 0.22f, 0.90f); custom(); }
    public void setBlurStrength(float value) { blurStrength = clamp(value, 0f, 8f); custom(); }
    public void setCornerRadius(float value) { cornerRadius = clamp(value, 4f, 18f); custom(); }
    public void setDensity(float value) { density = clamp(value, 0.76f, 1.18f); custom(); }
    public void setAnimationStrength(float value) { animationStrength = clamp(value, 0f, 1.5f); custom(); }
    public void setBackgroundDim(float value) { backgroundDim = clamp(value, 0f, 0.70f); custom(); }
    public void setGuiScale(float value) {
        float clamped = clamp(value, 0.75f, 1.25f);
        guiScale = clamp(Math.round(clamped / 0.05f) * 0.05f, 0.75f, 1.25f);
        custom();
    }

    private void custom() {
        theme = Theme.CUSTOM;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
