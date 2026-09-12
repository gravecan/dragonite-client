package me.shedaniel.clothconfig2.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regression for the 2026-07 Discord patch: an early {@code return;} in the Swing
 * license handler must not be treated as authorization. The coordinator fails closed
 * without a live session and protected-action gate.
 */
class StandaloneLicenseCoordinatorTest {

    @Test
    void emptyKeyNeverPermitsUiTransition() {
        StandaloneLicenseCoordinator.AuthorizationOutcome outcome =
                StandaloneLicenseCoordinator.authenticate("");
        assertNotNull(outcome);
        assertFalse(outcome.permitsUiTransition());
    }

    @Test
    void blankKeyNeverPermitsUiTransition() {
        StandaloneLicenseCoordinator.AuthorizationOutcome outcome =
                StandaloneLicenseCoordinator.authenticate("   ");
        assertFalse(outcome.permitsUiTransition());
    }
}
