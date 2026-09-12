package me.shedaniel.clothconfig2.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DialogHandlerLicensePropertyTest {
    @AfterEach
    void clearProperty() {
        System.clearProperty("dragonite.license");
    }

    @Test
    void consumesAndClearsTemporaryLicenseProperty() {
        System.setProperty("dragonite.license", "abcd-efgh-ijkl-mnop-qrst");

        assertEquals("ABCD-EFGH-IJKL-MNOP-QRST", DialogHandler.showLicenseDialog());
        assertNull(System.getProperty("dragonite.license"));
    }
}
