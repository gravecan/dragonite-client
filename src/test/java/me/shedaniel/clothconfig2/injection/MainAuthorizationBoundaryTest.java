package me.shedaniel.clothconfig2.injection;

import me.shedaniel.clothconfig2.internal.StandaloneLicenseCoordinator;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainAuthorizationBoundaryTest {
    @Test
    void injectorEntryPointsDoNotTrustTheLicenseInputProperty() throws Exception {
        String mainConstants = classConstants(Main.class);
        String loaderConstants = classConstants(DragoniteLoader.class);
        String coordinatorConstants =
                classConstants(StandaloneLicenseCoordinator.class);

        assertFalse(mainConstants.contains("dragonite.license"));
        assertFalse(loaderConstants.contains("dragonite.license"));
        assertTrue(mainConstants.contains("ProtectedActionGate"));
        assertTrue(loaderConstants.contains("StandaloneLicenseCoordinator"));
        assertFalse(loaderConstants.contains("authenticateStandalone"));
        assertFalse(loaderConstants.contains("NetworkHandler$AuthResult"));
        assertTrue(coordinatorConstants.contains("authenticateStandalone"));
        assertTrue(coordinatorConstants.contains("ProtectedActionGate"));
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
