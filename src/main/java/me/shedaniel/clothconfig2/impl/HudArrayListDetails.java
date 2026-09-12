package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.AbstractFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;

import java.util.Locale;
import java.util.Set;


public final class HudArrayListDetails {

    private static final Set<String> MODE_SETTING_NAMES = Set.of(
            "mode", "style", "hitbox mode", "aim mode", "move fix", "target", "aim at"
    );

    private static final Set<String> VALUE_SETTING_NAMES = Set.of(
            "reach distance", "range", "max range", "attack range", "look range",
            "wall range", "combat range", "display time", "disappear time", "scale"
    );

    private static final Set<String> SKIP_MODE_NAMES = Set.of(
            "corner", "anchor", "list corner"
    );

    private HudArrayListDetails() {}

    public static String resolveDetail(ConfigCategoryImpl mod, boolean showMode, boolean showValue) {
        if (mod == null) {
            return "";
        }
        if (mod instanceof Config_Bubbles || mod instanceof Config_ArrayList) {
            return "";
        }

        String mode = showMode ? resolveMode(mod) : "";
        if (!mode.isEmpty()) {
            return mode;
        }
        if (showValue) {
            return resolveValue(mod);
        }
        return "";
    }

    private static String resolveMode(ConfigCategoryImpl mod) {
        String specific = specificMode(mod);
        if (specific != null && !specific.isEmpty()) {
            return specific;
        }
        return scanEnumMode(mod);
    }

    private static String specificMode(ConfigCategoryImpl mod) {
        if (mod instanceof ConfigEntryImpl esp) {
            return shortMode(esp.is2DMode() ? "2D" : "3D");
        }
        if (mod instanceof Config_TargetESP tes) {
            return shortMode(readEnum(tes, "Style"));
        }
        if (mod instanceof PhysicsConfig vel) {
            return shortMode(readEnum(vel, "Mode"));
        }
        if (mod instanceof Config_FloatList aim) {
            return shortMode(aim.getMode());
        }
        if (mod instanceof MovementConfig move) {
            return shortMode(readEnum(move, "Mode"));
        }
        return null;
    }

    private static String resolveValue(ConfigCategoryImpl mod) {
        String specific = specificValue(mod);
        if (specific != null && !specific.isEmpty()) {
            return specific;
        }
        return scanHeroValue(mod);
    }

    private static String specificValue(ConfigCategoryImpl mod) {
        if (mod instanceof DistanceConfig reach) {
            return formatNumber(reach.getRange());
        }

        if (mod instanceof Config_FloatList aim) {
            return formatNumber(aim.getRange()) + "m";
        }
        if (mod instanceof ConfigEntryImpl esp) {
            return formatNumber(esp.queryRange()) + "m";
        }
        if (mod instanceof Config_Tracers tr) {
            return formatNumber(tr.queryRange()) + "m";
        }
        if (mod instanceof OverlayRenderer hud) {
            return formatNumber(hud.getDisplayTimeSeconds()) + "s";
        }
        return null;
    }

    private static String scanEnumMode(ConfigCategoryImpl mod) {
        EnumSelectorBuilder best = null;
        int bestScore = -1;
        for (AbstractFieldBuilder field : mod.settings) {
            if (!(field instanceof EnumSelectorBuilder enumField) || !field.isVisible()) {
                continue;
            }
            String name = norm(field.getName());
            if (SKIP_MODE_NAMES.contains(name)) {
                continue;
            }
            int score = MODE_SETTING_NAMES.contains(name) ? 10 : 1;
            if (score > bestScore) {
                bestScore = score;
                best = enumField;
            }
        }
        if (best == null) {
            return "";
        }
        return shortMode(best.get());
    }

    private static String scanHeroValue(ConfigCategoryImpl mod) {
        DoubleFieldBuilder best = null;
        int bestScore = -1;
        for (AbstractFieldBuilder field : mod.settings) {
            if (!(field instanceof DoubleFieldBuilder num) || !field.isVisible()) {
                continue;
            }
            String name = norm(field.getName());
            int score = VALUE_SETTING_NAMES.contains(name) ? 10 : 0;
            if (name.contains("range") || name.contains("reach")) {
                score = Math.max(score, 8);
            }
            if (score > bestScore) {
                bestScore = score;
                best = num;
            }
        }
        if (best == null || bestScore <= 0) {
            return "";
        }
        String suffix = best.getSuffix();
        String formatted = formatNumber(best.get());
        return suffix != null && !suffix.isBlank() ? formatted + suffix : formatted;
    }

    private static String readEnumIfVisible(ConfigCategoryImpl mod, String settingName) {
        for (AbstractFieldBuilder field : mod.settings) {
            if (field instanceof EnumSelectorBuilder enumField
                    && settingName.equalsIgnoreCase(field.getName())
                    && field.isVisible()) {
                return enumField.get();
            }
        }
        return "";
    }

    private static String readEnum(ConfigCategoryImpl mod, String settingName) {
        for (AbstractFieldBuilder field : mod.settings) {
            if (field instanceof EnumSelectorBuilder enumField
                    && settingName.equalsIgnoreCase(field.getName())) {
                return enumField.get();
            }
        }
        return "";
    }

    private static String shortMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return switch (raw) {
            case "No Knockback" -> "NoKB";
            case "Orbits", "Orbit", "Ghosts" -> "Orbits";
            case "2 Orbs", "Two Orbs" -> "2 Orbs";
            case "Spinning Square" -> "Square";
            case "Smooth Circle" -> "Circle";
            default -> raw.length() > 10 ? raw.substring(0, 9) + "…" : raw;
        };
    }

    private static String formatNumber(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.001) {
            return String.format(Locale.US, "%.0f", v);
        }
        if (v >= 10) {
            return String.format(Locale.US, "%.1f", v);
        }
        return String.format(Locale.US, "%.2f", v);
    }

    private static String norm(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
