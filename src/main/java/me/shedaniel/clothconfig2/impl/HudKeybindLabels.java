package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;


public final class HudKeybindLabels {

    private static final String[] MOUSE = {
            "LMB", "RMB", "MMB", "M4", "M5", "M6", "M7", "M8"
    };

    private HudKeybindLabels() {}

    public static String label(ConfigCategoryImpl mod) {
        if (mod.isBinding()) {
            return "...";
        }
        int k = mod.getKeybind();
        if (k < 0 || k == GLFW.GLFW_KEY_UNKNOWN) {
            return "";
        }
        if (k <= 7) {
            return MOUSE[k];
        }
        try {
            return InputUtil.fromKeyCode(k, 0).getLocalizedText().getString();
        } catch (Exception ignored) {
            return "K" + k;
        }
    }

    
    public static String compact(ConfigCategoryImpl mod) {
        String full = label(mod);
        if (full.isEmpty()) {
            return "";
        }
        return switch (full.toUpperCase(Locale.ROOT)) {
            case "LEFT SHIFT", "LEFTSHIFT" -> "LSH";
            case "RIGHT SHIFT", "RIGHTSHIFT" -> "RSH";
            case "LEFT CONTROL", "LEFT CTRL", "LEFTCTRL" -> "LC";
            case "RIGHT CONTROL", "RIGHT CTRL", "RIGHTCTRL" -> "RC";
            case "LEFT ALT", "LEFTALT" -> "LA";
            case "RIGHT ALT", "RIGHTALT" -> "RA";
            case "SPACE" -> "SPC";
            case "BACKSPACE" -> "BK";
            case "CAPS LOCK", "CAPSLOCK" -> "CAP";
            default -> full.length() > 6 ? full.substring(0, 5) + "…" : full;
        };
    }
}
