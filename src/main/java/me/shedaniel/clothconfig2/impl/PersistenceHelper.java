package me.shedaniel.clothconfig2.impl;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Session-only UI/state cache. Nothing is written to disk — resets every launch.
 * Real persistence belongs in the upcoming config system.
 */
public final class PersistenceHelper {
    private static int[] guiPos;
    private static double[] targetHudPos;
    private static final Map<String, Integer> keybinds = new HashMap<>();
    private static final Set<String> friends = new LinkedHashSet<>();

    private PersistenceHelper() {}

    public static int[] loadGuiPosition() {
        return guiPos == null ? null : guiPos.clone();
    }

    public static void saveGuiPosition(int x, int y) {
        guiPos = new int[]{x, y};
    }

    public static double[] loadTargetHudPosition() {
        return targetHudPos == null ? null : targetHudPos.clone();
    }

    public static void saveTargetHudPosition(double x, double y) {
        targetHudPos = new double[]{x, y};
    }

    public static Map<String, Integer> loadKeybinds() {
        return new HashMap<>(keybinds);
    }

    public static void saveKeybinds(Map<String, Integer> next) {
        keybinds.clear();
        if (next != null) {
            keybinds.putAll(next);
        }
    }

    public static Set<String> loadFriends() {
        return new LinkedHashSet<>(friends);
    }

    public static void saveFriends(Set<String> next) {
        friends.clear();
        if (next != null) {
            for (String name : next) {
                if (name != null && !name.isBlank()) {
                    friends.add(name.trim().toLowerCase());
                }
            }
        }
    }

    public static void clearKeybinds() {
        keybinds.clear();
    }
}
