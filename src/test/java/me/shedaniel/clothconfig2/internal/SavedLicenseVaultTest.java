package me.shedaniel.clothconfig2.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class SavedLicenseVaultTest {
    private static final String KEY = "ABCD-EFGH-IJKL-MNOP-QRST";

    @Test
    void doesNotPersistLicenseLocally() {
        SavedLicenseVault.save(KEY);
        assertNull(SavedLicenseVault.load());
    }

    @Test
    void clearIsNoOp() {
        SavedLicenseVault.save(KEY);
        SavedLicenseVault.clear();
        assertNull(SavedLicenseVault.load());
    }
}
