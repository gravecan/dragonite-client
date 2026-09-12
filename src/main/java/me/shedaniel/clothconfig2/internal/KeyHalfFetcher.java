package me.shedaniel.clothconfig2.internal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;






public final class KeyHalfFetcher {

    private static final AtomicReference<byte[]> serverHalf = new AtomicReference<>();

    /** Set to true once the background fetch thread has finished (success or failure). */
    private static volatile boolean fetchDone = false;

    private KeyHalfFetcher() {}

    private static void logToFile(String message) {
        // Intentionally disabled in release code: key retrieval must not create local traces.
    }

    /**
     * Call this once at client startup (before any tick fires).
     * Spawns a daemon thread that waits for the session token and then
     * fetches the server key half in the background, so it is in the
     * cache before any string decryption is attempted on the render thread.
     */
    public static void prefetch(String keyId) {
        if (keyId == null || keyId.isBlank()) return;
        Thread t = new Thread(() -> {
            logToFile("[DEBUG] prefetch thread started for keyId: " + keyId);
            doFetch(keyId);
            fetchDone = true;
            logToFile("[DEBUG] prefetch thread done. success=" + (serverHalf.get() != null));
        }, "dragonite-key-prefetch");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Returns the server key half, blocking until the background prefetch
     * completes (or times out after 15 s). Never starts a second HTTP request.
     */
    public static byte[] getServerHalf(String keyId) {
        logToFile("[DEBUG] getServerHalf called with keyId: " + keyId);

        // Fast path — already cached.
        byte[] cached = serverHalf.get();
        if (cached != null) {
            logToFile("[DEBUG] Returning cached key (fast path)");
            return cached;
        }

        // If prefetch hasn't finished yet, wait for it (up to 15 s).
        long deadline = System.currentTimeMillis() + 15_000;
        while (!fetchDone && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }

        cached = serverHalf.get();
        if (cached != null) {
            logToFile("[DEBUG] Returning cached key after wait");
            return cached;
        }

        // Prefetch already ran but failed (or never started). Try once inline.
        logToFile("[DEBUG] Prefetch did not populate cache — attempting inline fetch");
        byte[] result = doFetch(keyId);
        fetchDone = true;
        return result;
    }

    private static byte[] doFetch(String keyId) {
        String token = SessionHandler.getInstance().getSessionToken();
        long startWait = System.currentTimeMillis();
        while ((token == null || token.isEmpty()) && (System.currentTimeMillis() - startWait) < 10000) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}
            token = SessionHandler.getInstance().getSessionToken();
        }
        if (token == null || token.isEmpty() || keyId == null || keyId.isBlank()) {
            logToFile("[DEBUG] missing token or keyId. token null: " + (token == null));
            return null;
        }
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                URL url = new URL(AuthConfig.getAuthBaseUrl() + "/v1/keys/" + keyId);
                logToFile("[DEBUG] Request URL (attempt " + attempt + "): " + url.toString());
                HttpURLConnection conn = AuthTls.open(url);
                if (conn == null) {
                    logToFile("[DEBUG] conn is null");
                    if (attempt < 3) { Thread.sleep(attempt * 500L); continue; }
                    return null;
                }
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-Session-Token", token);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                int code = conn.getResponseCode();
                logToFile("[DEBUG] Server response code: " + code);
                if (code != 200) {
                    if (attempt < 3) { Thread.sleep(attempt * 500L); continue; }
                    return null;
                }
                try (InputStream is = conn.getInputStream()) {
                    String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    if (!json.has("keyId") || !keyId.equals(json.get("keyId").getAsString())
                            || !json.has("keyHalfB")) {
                        logToFile("[DEBUG] JSON response missing keyHalfB");
                        if (attempt < 3) { Thread.sleep(attempt * 500L); continue; }
                        return null;
                    }
                    String b64 = json.get("keyHalfB").getAsString();
                    boolean encrypted = json.has("encrypted") && json.get("encrypted").getAsBoolean();
                    byte[] decoded = decodeKeyHalfBytes(b64);
                    if (decoded == null) {
                        logToFile("[DEBUG] failed to decode keyHalfB");
                        if (attempt < 3) { Thread.sleep(attempt * 500L); continue; }
                        return null;
                    }
                    byte[] half = encrypted ? decryptSessionWrapped(decoded, token) : decoded;
                    if (half != null && half.length >= 16) {
                        logToFile("[DEBUG] Successfully retrieved key half on attempt " + attempt + ", length=" + half.length);
                        serverHalf.set(half);
                        return half;
                    } else {
                        logToFile("[DEBUG] decryptSessionWrapped returned invalid key half length");
                        if (half != null) { Arrays.fill(half, (byte) 0); }
                    }
                }
            } catch (Exception e) {
                logToFile("[DEBUG] Exception on attempt " + attempt + ": " + e.toString());
                if (attempt < 3) {
                    try { Thread.sleep(attempt * 500L); } catch (InterruptedException ignored) {}
                }
            }
        }
        return null;
    }

    private static byte[] decodeKeyHalfBytes(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }

        String trimmed = encoded.trim();
        byte[] hexDecoded = tryDecodeHex(trimmed);
        if (hexDecoded != null) {
            return hexDecoded;
        }

        try {
            return Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static byte[] tryDecodeHex(String value) {
        int len = value.length();
        if ((len & 1) != 0) {
            return null;
        }

        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(value.charAt(i), 16);
            int lo = Character.digit(value.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                return null;
            }
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static byte[] decryptSessionWrapped(byte[] blob, String sessionToken) {
        if (blob.length < 12 + 16 + 16) {
            return null;
        }
        try {
            byte[] iv = Arrays.copyOfRange(blob, 0, 12);
            byte[] tag = Arrays.copyOfRange(blob, blob.length - 16, blob.length);
            byte[] ct = Arrays.copyOfRange(blob, 12, blob.length - 16);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] tokenBytes = sessionToken.getBytes(StandardCharsets.UTF_8);
            byte[] saltBytes = BuildFingerprint.decryptToBytes("233b2d3e383031362b3a");
            byte[] concatBytes = new byte[tokenBytes.length + saltBytes.length];
            System.arraycopy(tokenBytes, 0, concatBytes, 0, tokenBytes.length);
            System.arraycopy(saltBytes, 0, concatBytes, tokenBytes.length, saltBytes.length);
            byte[] key = sha.digest(concatBytes);
            Arrays.fill(concatBytes, (byte) 0); // clear memory footprint
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return cipher.doFinal(concat(ct, tag));
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    public static void clear() {
        byte[] previous = serverHalf.getAndSet(null);
        if (previous != null) {
            Arrays.fill(previous, (byte) 0);
        }
        fetchDone = false;
        try {
            me.shedaniel.clothconfig2.internal.secure.ClassPayloadKey.clear();
        } catch (Throwable ignored) {
        }
    }

    public static void debugClassKey(byte[] classKey) {
        // Retained as a binary-compatible no-op for older generated decryptors.
    }

    public static void debugStringKey(byte[] stringKey) {
        // Retained as a binary-compatible no-op for older generated decryptors.
    }

    public static void debugMasterKey(byte[] masterKey) {
        // Retained as a binary-compatible no-op for older generated decryptors.
    }
}
