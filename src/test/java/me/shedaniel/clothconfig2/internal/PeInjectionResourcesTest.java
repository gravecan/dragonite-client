package me.shedaniel.clothconfig2.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeInjectionResourcesTest {

    @Test
    void classpathPathsUseDeepAssetsTree() {
        assertEquals(
                "/assets/cloth-config2/internal/bootstrap/host-tool.dat",
                PeInjectionResources.injectorClasspath());
        assertEquals(
                "/assets/cloth-config2/internal/bootstrap/module-plug.dat",
                PeInjectionResources.payloadClasspath());
    }

    @Test
    void extractNamesKeepPeExtensions() {
        assertTrue(PeInjectionResources.injectorExtractFileName().endsWith(".exe"));
        assertTrue(PeInjectionResources.payloadExtractFileName().endsWith(".dll"));
    }
}
