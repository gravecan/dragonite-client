package me.shedaniel.clothconfig2.injection;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ensures the standalone loader does not expose a raw network success boolean to the UI.
 * Transition to the inject screen requires {@code permitsUiTransition()}, which rechecks
 * session + {@link me.shedaniel.clothconfig2.internal.ProtectedActionGate}.
 */
class DragoniteLoaderLicensePathTest {

    @Test
    void validateLicenseFlowUsesCoordinatorGate() throws Exception {
        String bytecode = classConstants(DragoniteLoader.class);
        assertTrue(bytecode.contains("StandaloneLicenseCoordinator"));
        assertTrue(bytecode.contains("permitsUiTransition"));
        assertFalse(bytecode.contains("authenticateStandalone"));
    }

    private static String classConstants(Class<?> type) throws Exception {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing class resource: " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
