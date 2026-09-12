package me.shedaniel.clothconfig2.internal;

import java.util.Locale;
import java.util.regex.Pattern;

/** Canonical representation of the license format accepted by this client. */
final class LicenseKey {
    private static final Pattern COMPACT = Pattern.compile("[A-Z0-9]{20}");

    private LicenseKey() {
    }

    /**
     * Returns the canonical XXXX-XXXX-XXXX-XXXX-XXXX key, or {@code null} when the
     * submitted value is malformed. Server-side validation remains
     * authoritative for existence, expiry, binding, and revocation.
     */
    static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String compact = value.trim().replace("-", "").toUpperCase(Locale.ROOT);
        if (!COMPACT.matcher(compact).matches()) {
            return null;
        }
        return compact.substring(0, 4) + "-" + compact.substring(4, 8) + "-"
                + compact.substring(8, 12) + "-" + compact.substring(12, 16)
                + "-" + compact.substring(16, 20);
    }
}
