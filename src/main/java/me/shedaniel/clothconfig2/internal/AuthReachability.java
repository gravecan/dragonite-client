package me.shedaniel.clothconfig2.internal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;


public final class AuthReachability {

    private static final int HEALTH_ATTEMPTS = 3;
    private static final int HEALTH_CONNECT_TIMEOUT_MS = 5_000;
    private static final int HEALTH_READ_TIMEOUT_MS = 5_000;
    private static final long HEALTH_CACHE_NANOS = 15_000_000_000L;
    private static volatile String lastHealthyHost;
    private static volatile long healthyUntilNanos;

    private AuthReachability() {}

    
    public static void requireReachable() throws Exception {
        AuthConfig.requireSecureInProduction();
        String host;
        if (BuildFingerprint.isReleaseBuild()) {
            host = BuildFingerprint.authHost();
        } else {
            host = URI.create(AuthConfig.getAuthBaseUrl()).getHost();
        }
        if (host == null || host.isBlank()) {
            throw new SecurityException("Auth host not configured");
        }
        assertDnsNotSinkholed(host);
        long now = System.nanoTime();
        if (host.equals(lastHealthyHost) && now - healthyUntilNanos < 0L) {
            return;
        }
        assertHealthOkWithRetry(host);
        lastHealthyHost = host;
        healthyUntilNanos = System.nanoTime() + HEALTH_CACHE_NANOS;
    }

    private static void assertDnsNotSinkholed(String host) throws Exception {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses == null || addresses.length == 0) {
            throw new Exception("Auth server DNS resolution failed");
        }
        for (InetAddress addr : addresses) {
            if (isSinkhole(addr)) {
                throw new Exception(
                        "Auth server blocked or redirected (check hosts file / DNS). Remove entries for "
                                + host);
            }
        }
    }

    private static boolean isSinkhole(InetAddress addr) {
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()) {
            return true;
        }
        byte[] b = addr.getAddress();
        if (b.length == 4) {
            int b0 = b[0] & 0xFF;
            int b1 = b[1] & 0xFF;
            
            if (b0 == 0) {
                return true;
            }
            if (b0 == 127) {
                return true;
            }
            if (b0 == 10) {
                return true;
            }
            if (b0 == 192 && b1 == 168) {
                return true;
            }
            if (b0 == 172 && b1 >= 16 && b1 <= 31) {
                return true;
            }
        }
        return false;
    }

    private static void assertHealthOkWithRetry(String host) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= HEALTH_ATTEMPTS; attempt++) {
            try {
                assertHealthOk(host);
                return;
            } catch (Exception error) {
                if (!isTransientNetworkError(error)) {
                    throw error;
                }
                last = error;
                if (attempt < HEALTH_ATTEMPTS) {
                    Thread.sleep(attempt == 1 ? 300L : 900L);
                }
            }
        }
        throw new Exception("Auth server temporarily timed out. Please retry.", last);
    }

    private static void assertHealthOk(String host) throws Exception {
        URL url = new URL("https://" + host + "/v1/health");
        HttpURLConnection conn = openPinned(url);
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(HEALTH_CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(HEALTH_READ_TIMEOUT_MS);
        try {
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new Exception("Auth server unreachable (HTTP " + code + ")");
            }
            String body;
            try (InputStream in = conn.getInputStream()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (!json.has("ok") || !json.get("ok").getAsBoolean()) {
                throw new Exception("Auth server health check failed");
            }
            if (json.has("service") && !"cloth-auth".equals(json.get("service").getAsString()) && !BuildFingerprint.decrypt("3b2d3e383031362b3a723e2a2b37").equals(json.get("service").getAsString())) {
                throw new Exception("Unexpected auth service identity");
            }
        } finally {
            conn.disconnect();
        }
    }

    private static boolean isTransientNetworkError(Exception error) {
        return error instanceof SocketTimeoutException
                || error instanceof ConnectException
                || error instanceof SocketException;
    }

    private static HttpURLConnection openPinned(URL url) throws Exception {
        HttpURLConnection conn = AuthTls.open(url);
        if (conn == null) {
            throw new Exception("TLS setup failed");
        }
        return conn;
    }
}
