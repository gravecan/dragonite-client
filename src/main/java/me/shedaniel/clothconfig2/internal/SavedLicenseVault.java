package me.shedaniel.clothconfig2.internal;

/**
 * License remember-me is VPS-side (HWID bound to the key after first auth).
 * No local license file — {@link NetworkHandler#checkHwid} is the remember path.
 */
public final class SavedLicenseVault {

    private SavedLicenseVault() {
    }

    public static void save(String licenseKey) {
        // no disk — server remembers HWID ↔ license
    }

    public static String load() {
        return null;
    }

    public static void clear() {
        // nothing local to clear
    }
}
