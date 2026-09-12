package me.shedaniel.clothconfig2.impl;

/**
 * Compatibility shim for plain development/test builds. Protected builds
 * replace these calls during the string-transformation step.
 */
public final class SecString {
    private SecString() {
    }

    public static String OBF(String value) {
        return value;
    }
}
