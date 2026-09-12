package me.shedaniel.clothconfig2.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionNetworkResilienceTest {
    @Test
    void classifiesOnlyTemporaryTransportFailuresAsRetryable() {
        assertTrue(SessionHandler.isTransientNetworkFailure("server_error", "Read timed out"));
        assertTrue(SessionHandler.isTransientNetworkFailure("server_error", "Connection reset"));
        assertTrue(SessionHandler.isTransientNetworkFailure("server_error", "Invalid challenge from auth server"));

        assertFalse(SessionHandler.isTransientNetworkFailure("blacklisted", "Read timed out"));
        assertFalse(SessionHandler.isTransientNetworkFailure(
                "server_error", "Authentication response signature verification failed"));
        assertFalse(SessionHandler.isTransientNetworkFailure("server_error", "Invalid session issued"));
    }

    @Test
    void oneOrTwoMinuteIntervalFailuresStayInsideTheIntendedGraceWindow() {
        assertFalse(SessionHandler.shouldTerminateForOffline(1, 60_000L));
        assertFalse(SessionHandler.shouldTerminateForOffline(2, 120_000L));
        assertTrue(SessionHandler.shouldTerminateForOffline(3, 180_000L));
        assertTrue(SessionHandler.shouldTerminateForOffline(1, 151_000L));
    }
}
