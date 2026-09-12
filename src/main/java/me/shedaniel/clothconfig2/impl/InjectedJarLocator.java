package me.shedaniel.clothconfig2.impl;

/**
 * Holds the injected client JAR path. Must stay free of Minecraft imports so
 * native bootstrap can record the path before Knot/Lunar classloaders are wired.
 */
public final class InjectedJarLocator {

    private static volatile String jarPath;

    private InjectedJarLocator() {
    }

    public static void setJarPath(String path) {
        if (path != null && !path.isBlank()) {
            jarPath = path;
        }
    }

    public static String getJarPath() {
        return jarPath;
    }
}
