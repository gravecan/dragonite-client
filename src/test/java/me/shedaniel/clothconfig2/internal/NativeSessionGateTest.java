package me.shedaniel.clothconfig2.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeSessionGateTest {
    @AfterEach
    void reset() {
        NativeSessionGate.disarm();
    }

    @Test
    void sealBindsTokenWordsEpochAndPurpose() {
        long a = 0x123456789ABCDEFL;
        long b = 0x0FEDCBA987654321L;
        long epoch = 0x13579BDF2468ACE0L;
        long seal = NativeSessionGate.deriveSeal(
                a, b, epoch, NativeSessionGate.PURPOSE_SESSION);

        assertTrue(NativeSessionGate.verifySeal(
                seal, a, b, epoch, NativeSessionGate.PURPOSE_SESSION));
        assertFalse(NativeSessionGate.verifySeal(
                seal, a ^ 1L, b, epoch, NativeSessionGate.PURPOSE_SESSION));
        assertFalse(NativeSessionGate.verifySeal(
                seal, a, b, epoch + 1L, NativeSessionGate.PURPOSE_SESSION));
        assertFalse(NativeSessionGate.verifySeal(
                seal, a, b, epoch, NativeSessionGate.PURPOSE_PROTECTED_ACTION));
        assertNotEquals(seal, NativeSessionGate.deriveSeal(
                a, b, epoch, NativeSessionGate.PURPOSE_PROTECTED_ACTION));
    }

    @Test
    void rejectsInvalidInputsAndDisarmedState() {
        assertFalse(NativeSessionGate.arm(null, "hwid", "evidence", 1L));
        assertFalse(NativeSessionGate.arm("token", "", "evidence", 1L));
        assertFalse(NativeSessionGate.arm("token", "hwid", null, 1L));
        assertFalse(NativeSessionGate.arm("token", "hwid", "evidence", 0L));
        assertFalse(NativeSessionGate.holds(
                1L, NativeSessionGate.PURPOSE_AUTH_LIVE));
    }
}
