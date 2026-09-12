package me.shedaniel.clothconfig2.internal.secure;

import java.nio.charset.StandardCharsets;

public class StringPool {
    public static String decryptString(byte[] cipherBytes, byte[] keyBytes) {
        if (cipherBytes == null || keyBytes == null || keyBytes.length == 0) return null;
        
        byte[] result = new byte[cipherBytes.length];
        for (int i = 0; i < cipherBytes.length; i++) {
            result[i] = (byte) (cipherBytes[i] ^ keyBytes[i % keyBytes.length]);
        }
        return new String(result, StandardCharsets.UTF_8);
    }
}
