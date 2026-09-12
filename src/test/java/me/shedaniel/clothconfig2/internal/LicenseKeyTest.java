package me.shedaniel.clothconfig2.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LicenseKeyTest {
    @Test
    void canonicalizesOnlyTheSupportedLicenseShape() {
        assertEquals("ABCD-EFGH-IJKL-MNOP-QRST",
                LicenseKey.normalize("abcd-efgh-ijkl-mnop-qrst"));
        assertEquals("ABCD-EFGH-IJKL-MNOP-QRST",
                LicenseKey.normalize("ABCDEFGHIJKLMNOPQRST"));
        assertNull(LicenseKey.normalize(null));
        assertNull(LicenseKey.normalize(""));
        assertNull(LicenseKey.normalize("../../not-a-license"));
        assertNull(LicenseKey.normalize("ABCD-EFGH-IJKL-MNOP-QRST-EXTRA"));
    }
}
