package me.shedaniel.clothconfig2.internal.secure;

import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import me.shedaniel.clothconfig2.internal.BuildFingerprint;
import me.shedaniel.clothconfig2.internal.ClientLog;

public class JavaAuthSigner {

    public static String getSpkiFingerprint() {
        String spki = BuildFingerprint.ecdsaPublicSpkiB64();
        if (spki == null || spki.startsWith("__OBF_") || spki.isBlank()) {
            return "not-embedded";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(spki.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "error";
        }
    }

    public static String signPayload(String payload, String license) {
        if (BuildFingerprint.isReleaseBuild()) {
            return signLicenseBoundHmac(payload, license, "hmac:");
        }
        return signPayloadEcdsa(payload, license, "ecdsa:");
    }

    public static String signNonceBoundPayload(String payload, String license) {
        if (BuildFingerprint.isReleaseBuild()) {
            return signLicenseBoundHmac(payload, license, "hmac2:");
        }
        return signPayloadEcdsa(payload, license, "ecdsa2:");
    }

    private static String signLicenseBoundHmac(String payload, String license, String prefix) {
        if (BuildFingerprint.isReleaseBuild() && payload != null && payload.endsWith("|")) {
            ClientLog.err("JavaAuthSigner", "signLicenseBoundHmac: missing jar digest for auth proof");
            return null;
        }
        byte[] proofKey = JavaNativeDerivation.deriveAuthProofKey(license);
        if (proofKey == null) {
            ClientLog.err("JavaAuthSigner", "signLicenseBoundHmac: could not derive proof key");
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(proofKey, "HmacSHA256"));
            mac.update(payload.getBytes(StandardCharsets.UTF_8));
            return prefix + Base64.getEncoder().encodeToString(mac.doFinal());
        } catch (Throwable t) {
            ClientLog.err("JavaAuthSigner", "signLicenseBoundHmac: " + t.getClass().getSimpleName());
            return null;
        } finally {
            Arrays.fill(proofKey, (byte) 0);
        }
    }

    private static String signPayloadEcdsa(String payload, String license, String prefix) {
        if (BuildFingerprint.isReleaseBuild() && payload != null && payload.endsWith("|")) {
            ClientLog.err("JavaAuthSigner", "signPayload: missing jar digest for auth proof");
            return null;
        }

        byte[] rawKey = rawKeyFromBuildFingerprint();
        if (rawKey == null) {
            if (BuildFingerprint.isReleaseBuild()) {
                return signLicenseBoundHmac(payload, license, prefix.startsWith("ecdsa2") ? "hmac2:" : "hmac:");
            }
            ClientLog.err("JavaAuthSigner", "signPayload: no raw ECDSA key (rebuild with Build-Release.bat)");
            return null;
        }
        try {
            byte[] dBytes = Arrays.copyOfRange(rawKey, 64, 96);
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec ecParameters = parameters.getParameterSpec(ECParameterSpec.class);
            ECPrivateKeySpec privSpec = new ECPrivateKeySpec(new BigInteger(1, dBytes), ecParameters);
            PrivateKey privateKey = KeyFactory.getInstance("EC").generatePrivate(privSpec);

            Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format");
            sig.initSign(privateKey);
            sig.update(payload.getBytes(StandardCharsets.UTF_8));
            String proof = prefix + Base64.getEncoder().encodeToString(sig.sign());

            if (!localEcdsaSelfCheck(payload, proof)) {
                ClientLog.err("JavaAuthSigner", "signPayload: local verify failed - set VPS "
                        + "ECDSA_PUBLIC_KEY_SPKI_B64 to master_ecdsa_spki.b64 from the release build");
            }
            return proof;
        } catch (Throwable t) {
            ClientLog.err("JavaAuthSigner", "signPayload: " + t.getClass().getSimpleName()
                    + (t.getMessage() != null ? " - " + t.getMessage() : ""));
            return null;
        }
    }

    private static boolean localEcdsaSelfCheck(String payload, String proofWithPrefix) {
        String spkiB64 = BuildFingerprint.ecdsaPublicSpkiB64();
        if (spkiB64 == null || spkiB64.startsWith("__OBF_") || spkiB64.isBlank()) {
            return true; // dev mode
        }
        if (proofWithPrefix == null
                || !(proofWithPrefix.startsWith("ecdsa:") || proofWithPrefix.startsWith("ecdsa2:"))) {
            return false;
        }
        try {
            byte[] spki = Base64.getDecoder().decode(spkiB64.trim());
            PublicKey pub = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(spki));
            Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
            verifier.initVerify(pub);
            verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            int separator = proofWithPrefix.indexOf(':');
            return verifier.verify(Base64.getDecoder().decode(proofWithPrefix.substring(separator + 1)));
        } catch (Throwable t) {
            ClientLog.err("JavaAuthSigner", "localEcdsaSelfCheck: " + t.getClass().getSimpleName());
            return false;
        }
    }

    private static byte[] rawKeyFromBuildFingerprint() {
        String hex = BuildFingerprint.ecdsaSigningRawHex();
        if (hex == null || hex.startsWith("__") || hex.contains("SERVER_ONLY")) {
            return null;
        }
        byte[] raw = hexToBytes(hex);
        return raw != null && raw.length >= 96 ? raw : null;
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || (hex.length() & 1) != 0) {
            return null;
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                return null;
            }
            out[i] = (byte) ((hi << 4) + lo);
        }
        return out;
    }
}
