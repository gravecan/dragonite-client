package me.shedaniel.clothconfig2.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeBridgeTest {
    @Test
    void healthResponseContractIsDeterministic() {
        assertEquals(
                NativeBridge.HEALTH_CHALLENGE ^ NativeBridge.HEALTH_MASK,
                NativeBridge.expectedHealthResponse(NativeBridge.HEALTH_CHALLENGE));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @EnabledIfSystemProperty(named = "dragonite.nativeBridgeTest", matches = "true")
    void packagedWindowsBridgeLoadsAndPassesHealthCheck() {
        assertTrue(NativeBridge.load(), NativeBridge::getStatus);
        assertTrue(NativeBridge.isLoaded());
        assertTrue(NativeBridge.isHealthy());
        assertEquals("dragonite-native-bridge/2", NativeBridge.getVersion());
    }
}
