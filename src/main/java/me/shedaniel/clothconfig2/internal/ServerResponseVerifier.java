package me.shedaniel.clothconfig2.internal;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Verifies responses signed by the auth server's VPS-only Ed25519 key. */
final class ServerResponseVerifier {
    private ServerResponseVerifier() {
    }

    static boolean verify(String method, String path, int statusCode, boolean granted,
                          String sessionToken, String expiresAt, String requestNonce,
                          String requestJarHash, String signatureB64) {
        if (signatureB64 == null || signatureB64.isBlank()) {
            return false;
        }
        try {
            String spkiB64 = BuildFingerprint.authResponseEd25519PublicSpkiB64();
            PublicKey publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(spkiB64)));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            String signed = method + "|" + path + "|" + statusCode + "|"
                    + (granted ? "1" : "0") + "|"
                    + (sessionToken != null ? sessionToken : "") + "|"
                    + (expiresAt != null ? expiresAt : "") + "|"
                    + (requestNonce != null ? requestNonce : "") + "|"
                    + (requestJarHash != null ? requestJarHash.toLowerCase(java.util.Locale.ROOT) : "");
            verifier.update(signed.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signatureB64));
        } catch (Exception ignored) {
            return false;
        }
    }
}
