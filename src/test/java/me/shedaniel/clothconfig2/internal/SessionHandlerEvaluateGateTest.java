package me.shedaniel.clothconfig2.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionHandlerEvaluateGateTest {

    @Test
    void requiresEveryLiveSessionInput() {
        assertTrue(SessionHandler.evaluateSessionLiveGate(
                true, true, true, true, true, true, true));
        for (int missing = 0; missing < 7; missing++) {
            boolean[] gates = {true, true, true, true, true, true, true};
            gates[missing] = false;
            assertFalse(SessionHandler.evaluateSessionLiveGate(
                    gates[0], gates[1], gates[2], gates[3],
                    gates[4], gates[5], gates[6]),
                    "missing input index " + missing);
        }
    }
}
