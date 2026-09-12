package me.shedaniel.clothconfig2.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectedActionGateTest {
    @Test
    void requiresEveryAuthorizationAndArtifactGate() {
        assertTrue(ProtectedActionGate.evaluate(true, true, true, true, true, true));
        for (int missing = 0; missing < 6; missing++) {
            boolean[] gates = {true, true, true, true, true, true};
            gates[missing] = false;
            assertFalse(ProtectedActionGate.evaluate(
                    gates[0], gates[1], gates[2], gates[3], gates[4], gates[5]));
        }
    }
}
