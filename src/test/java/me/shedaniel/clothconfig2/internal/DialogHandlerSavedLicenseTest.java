package me.shedaniel.clothconfig2.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DialogHandlerSavedLicenseTest {
    @AfterEach
    void cleanup() {
        DialogHandler.clearSavedLicense();
        System.clearProperty("dragonite.license");
    }

    @Test
    void saveLicenseDoesNotCreateLocalShortcut() {
        DialogHandler.saveLicense("abcd-efgh-ijkl-mnop-qrst");
        assertNull(DialogHandler.loadSavedLicense());
    }

    @Test
    void temporaryPropertyBypassesGuiWithoutOpeningDialog() {
        System.setProperty("dragonite.license", "ZZZZ-ZZZZ-ZZZZ-ZZZZ-ZZZZ");

        assertEquals("ZZZZ-ZZZZ-ZZZZ-ZZZZ-ZZZZ", DialogHandler.showLicenseDialog());
        assertNull(System.getProperty("dragonite.license"));
    }
}
