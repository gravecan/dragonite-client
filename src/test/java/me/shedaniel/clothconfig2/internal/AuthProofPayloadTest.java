package me.shedaniel.clothconfig2.internal;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthProofPayloadTest {
    @Test
    void payloadBuilderRemainsPublicForRelocatedAuthCallers() throws Exception {
        var method = ConfigLoader.class.getDeclaredMethod("buildChallengeProofPayload",
                String.class, String.class, String.class, String.class);
        assertTrue(Modifier.isPublic(method.getModifiers()),
                "release name remapping may relocate NetworkHandler outside ConfigLoader's package");
    }

    @Test
    void nonceIsBoundIntoAuthenticationProofPayload() {
        String first = ConfigLoader.buildChallengeProofPayload("nonce-a", "100", "hwid", "license");
        String replay = ConfigLoader.buildChallengeProofPayload("nonce-b", "100", "hwid", "license");

        assertTrue(first.startsWith("nonce-a|100|hwid|license|"));
        assertNotEquals(first, replay);
    }
}
