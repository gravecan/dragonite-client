package me.shedaniel.clothconfig2.internal.secure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;

public class JavaKeyDeriver {
    private static final String SALT = "DragoniteClient-Salt-v3";
    private static byte[] derivedKey = null;

    public static String generateClientPublicKey() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        KeyPair kp = kpg.generateKeyPair();
        
        // Store our private key in thread local temporarily for handshake
        currentPrivateKey.set(kp.getPrivate());
        
        return Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
    }

    private static final ThreadLocal<java.security.PrivateKey> currentPrivateKey = new ThreadLocal<>();

    public static void deriveSessionKey(String serverPublicKeyB64) {
        try {
            byte[] serverPubBytes = Base64.getDecoder().decode(serverPublicKeyB64);
            KeyFactory kf = KeyFactory.getInstance("EC");
            PublicKey serverPub = kf.generatePublic(new X509EncodedKeySpec(serverPubBytes));

            java.security.PrivateKey clientPriv = currentPrivateKey.get();
            if (clientPriv == null) {
                throw new IllegalStateException("Client private key not found");
            }

            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(clientPriv);
            ka.doPhase(serverPub, true);
            byte[] sharedSecret = ka.generateSecret();

            // HKDF expansion
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SALT.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] prk = mac.doFinal(sharedSecret);
            
            Mac hkdf = Mac.getInstance("HmacSHA256");
            hkdf.init(new SecretKeySpec(prk, "HmacSHA256"));
            hkdf.update("dragonite-session-key".getBytes(StandardCharsets.UTF_8));
            hkdf.update((byte) 0x01);
            
            derivedKey = hkdf.doFinal();
            
            // Clear secret from memory
            Arrays.fill(sharedSecret, (byte) 0);
            currentPrivateKey.remove();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static byte[] getSessionKey() {
        return derivedKey;
    }
    
    public static void clearSessionKey() {
        if (derivedKey != null) {
            Arrays.fill(derivedKey, (byte) 0);
        }
    }
}
