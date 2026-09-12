package me.shedaniel.clothconfig2.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationDiagnosticsTest {
    @Test
    void tokenSanitizationIsBoundedAndDoesNotPreserveSecrets() {
        String token = AuthorizationDiagnostics.safeToken(
                "INVALID PROOF\r\nLICENSE-SECRET-1234567890-ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        assertTrue(token.length() <= 64);
        assertFalse(token.contains(" "));
        assertFalse(token.contains("\n"));
    }

    @Test
    void diagnosticsNeverWriteDisk() {
        String rawHwid = "unique-raw-hwid-never-log-02082026";
        String rawProof = "hmac2:" + java.util.Base64.getEncoder()
                .encodeToString("unique-proof-never-log".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        AuthorizationDiagnostics.recordProofAttempt(
                "nonce|timestamp|" + rawHwid + "|marker|jar",
                rawProof, "unique-jar-hash-never-log", rawHwid, new byte[32]);
        AuthorizationDiagnostics.recordStage("proof_generation", "ok");
        assertEquals("(disabled)", AuthorizationDiagnostics.logPath());
    }

    @Test
    void invalidProofGetsStableUserFacingMessage() {
        assertEquals("Server rejected the authentication proof",
                SessionHandler.safeStandaloneMessage("invalid_proof", "attacker controlled"));
    }
}
