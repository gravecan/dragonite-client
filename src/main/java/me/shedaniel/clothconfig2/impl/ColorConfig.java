package me.shedaniel.clothconfig2.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Session-only color picker state for the old ClickGUI color editor.
 * Not a backup — just recent/saved swatches in RAM. Resets every launch.
 */
public class ColorConfig {

    private static final int MAX_RECENT = 12;
    private static final ColorConfig SESSION = new ColorConfig();

    public int accentColor = 0xFF4A90D9;
    public int bgColor = 0xC8111316;
    public List<Integer> recentColors = new ArrayList<>();
    public List<Integer> savedPalette = new ArrayList<>();

    public static ColorConfig load() {
        return SESSION;
    }

    public void save() {
        // session only
    }

    public void addRecentColor(int argb) {
        recentColors.remove((Integer) argb);
        recentColors.add(0, argb);
        while (recentColors.size() > MAX_RECENT) {
            recentColors.remove(recentColors.size() - 1);
        }
    }

    public void saveToPalette(int argb) {
        if (!savedPalette.contains(argb)) {
            savedPalette.add(argb);
        }
    }

    public List<Integer> getRecentColors() {
        return Collections.unmodifiableList(recentColors);
    }

    public List<Integer> getSavedPalette() {
        return Collections.unmodifiableList(savedPalette);
    }
}
