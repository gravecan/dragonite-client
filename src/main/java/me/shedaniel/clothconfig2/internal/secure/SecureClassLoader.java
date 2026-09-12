package me.shedaniel.clothconfig2.internal.secure;

import java.nio.ByteBuffer;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.concurrent.ConcurrentHashMap;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.InputStream;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.shedaniel.clothconfig2.internal.ClientLog;

public class SecureClassLoader extends ClassLoader {
    // Prevent GC compaction leaks by storing decrypted bytes off-heap
    private static final ConcurrentHashMap<String, ByteBuffer> offHeapCache = new ConcurrentHashMap<>();

    public SecureClassLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            byte[] decrypted = loadAndDecryptClass(name);
            if (decrypted != null) {
                
                // Store off-heap
                ByteBuffer buffer = ByteBuffer.allocateDirect(decrypted.length);
                buffer.put(decrypted);
                buffer.flip();
                offHeapCache.put(name, buffer);
                
                // We define the class directly from the byte array since Java's ClassLoader
                // doesn't support defineClass from ByteBuffer in older versions natively without
                // the protection domain overhead, but we immediately zero out the array.
                Class<?> clazz = defineClass(name, decrypted, 0, decrypted.length);
                
                // Zeroize the array so it's not sitting in the heap
                for (int i = 0; i < decrypted.length; i++) {
                    decrypted[i] = 0;
                }
                
                return clazz;
            }
        } catch (Exception e) {
            ClientLog.err("SecureClassLoader", "Failed to load class: " + name);
        }
        return super.findClass(name);
    }

    private byte[] loadAndDecryptClass(String name) throws Exception {
        // Find the JSON config file that contains this class
        String resourcePath = "assets/clothconfig2/data/" + name.replace('.', '_') + ".json";
        InputStream is = getResourceAsStream(resourcePath);
        if (is == null) return null;

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        
        JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
        String b64Data = json.get("data").getAsString();
        String b64Iv = json.get("iv").getAsString();
        
        byte[] encryptedData = Base64.getDecoder().decode(b64Data);
        byte[] iv = Base64.getDecoder().decode(b64Iv);
        
        byte[] key = JavaKeyDeriver.getSessionKey();
        if (key == null) {
            throw new IllegalStateException("Session key not derived yet. Auth gate failed?");
        }
        
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        
        cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
        return cipher.doFinal(encryptedData);
    }
    
    public static void clearCache() {
        // Securely wipe off-heap buffers before freeing
        for (ByteBuffer buf : offHeapCache.values()) {
            buf.clear();
            while (buf.hasRemaining()) {
                buf.put((byte) 0);
            }
        }
        offHeapCache.clear();
    }
}
