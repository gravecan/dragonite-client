package me.shedaniel.clothconfig2.internal.secure;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Loads an encrypted class payload from {@code assets/clothconfig2/data/*.json}
 * into the given ClassLoader (Knot). Decrypted bytes are never written to disk.
 */
public final class ClassPayloadLoader {
    private static final int VERSION = 1;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private ClassPayloadLoader() {
    }

    public static Class<?> loadEncrypted(ClassLoader defineInto, String internalName) throws Exception {
        if (defineInto == null || internalName == null || internalName.isBlank()) {
            throw new IllegalArgumentException("loader and internal name are required");
        }
        byte[] key = ClassPayloadKey.getOrDerive();
        if (key == null) {
            throw new IllegalStateException("class payload key unavailable (auth/session)");
        }
        try {
            String resourcePath = "assets/clothconfig2/data/" + internalName.replace('/', '_') + ".json";
            String json = readResource(defineInto, resourcePath);
            byte[] classBytes = decrypt(internalName, json, key);
            try {
                return defineClass(defineInto, internalName.replace('/', '.'), classBytes);
            } finally {
                Arrays.fill(classBytes, (byte) 0);
            }
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    public static boolean payloadResourcePresent(ClassLoader loader, String internalName) {
        String resourcePath = "assets/clothconfig2/data/" + internalName.replace('/', '_') + ".json";
        try (InputStream in = open(loader, resourcePath)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static String readResource(ClassLoader loader, String resourcePath) throws Exception {
        try (InputStream in = open(loader, resourcePath)) {
            if (in == null) {
                throw new ClassNotFoundException("missing class payload resource: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static InputStream open(ClassLoader loader, String resourcePath) {
        InputStream in = loader.getResourceAsStream(resourcePath);
        if (in != null) {
            return in;
        }
        return ClassPayloadLoader.class.getClassLoader().getResourceAsStream(resourcePath);
    }

    private static Class<?> defineClass(ClassLoader loader, String fqcn, byte[] classBytes) throws Exception {
        Method define = ClassLoader.class.getDeclaredMethod(
                "defineClass", String.class, byte[].class, int.class, int.class);
        define.setAccessible(true);
        return (Class<?>) define.invoke(loader, fqcn, classBytes, 0, classBytes.length);
    }

    private static byte[] decrypt(String expectedInternalName, String jsonText, byte[] key) throws Exception {
        JsonObject json = JsonParser.parseString(jsonText).getAsJsonObject();
        if (!json.has("version") || json.get("version").getAsInt() != VERSION) {
            throw new IllegalArgumentException("unsupported class payload version");
        }
        String encodedName = json.get("class").getAsString();
        if (!encodedName.equals(expectedInternalName)) {
            throw new AEADBadTagException("class identity mismatch");
        }
        byte[] iv = Base64.getDecoder().decode(json.get("iv").getAsString());
        byte[] ciphertext = Base64.getDecoder().decode(json.get("data").getAsString());
        if (iv.length != IV_BYTES) {
            throw new IllegalArgumentException("invalid GCM IV length");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(("dragonite-class-payload-v" + VERSION + "\0" + expectedInternalName)
                .getBytes(StandardCharsets.UTF_8));
        return cipher.doFinal(ciphertext);
    }
}
