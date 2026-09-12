package me.shedaniel.clothconfig2.internal;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;


public final class AuthTls {

    private AuthTls() {}

    public static HttpURLConnection open(URL url) throws Exception {
        String pHost = System.getProperty("https.proxyHost");
        String pH = System.getProperty("http.proxyHost");
        if ((pHost != null && !pHost.trim().isEmpty()) || (pH != null && !pH.trim().isEmpty())) {
            throw new CertificateException("Proxy detected. Please disable intercepting debuggers (Fiddler/Charles/Burp).");
        }
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (!(conn instanceof HttpsURLConnection https)) {
            return conn;
        }
        byte[][] pins = loadPinnedSpkiHashes();
        if (pins.length == 0) {
            if (!BuildFingerprint.isReleaseBuild()) {
                return conn;
            }
            throw new CertificateException("Missing embedded auth TLS pin");
        }
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[]{pinnedTrustManager(defaultTrustManager(), pins)}, null);
        https.setSSLSocketFactory(ctx.getSocketFactory());
        https.setHostnameVerifier((hostname, session) -> {
            String expected = BuildFingerprint.authHost();
            if (expected != null && !expected.startsWith("__OBF_")) {
                return expected.equalsIgnoreCase(hostname)
                        && HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session);
            }
            return false;
        });
        return https;
    }

    private static byte[][] loadPinnedSpkiHashes() {
        List<byte[]> pins = new ArrayList<>();
        if (BuildFingerprint.isReleaseBuild()) {
            addEmbeddedPin(pins);
        }
        return pins.toArray(new byte[0][]);
    }

    private static void addEmbeddedPin(List<byte[]> pins) {
        String hex = BuildFingerprint.authSpkiSha256Hex();
        if (hex == null || hex.startsWith("__OBF_")) {
            return;
        }
        for (String candidate : hex.split("[,;]")) {
            byte[] decoded = hexToBytes(candidate.replace(":", "").trim());
            if (decoded != null && decoded.length == 32) {
                pins.add(decoded);
            }
        }
    }

    private static X509TrustManager defaultTrustManager() throws Exception {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
        );
        factory.init((KeyStore) null);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager x509) {
                return x509;
            }
        }
        throw new CertificateException("No platform X.509 trust manager available");
    }

    private static X509TrustManager pinnedTrustManager(X509TrustManager platformTrust, byte[][] pins) {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                platformTrust.checkClientTrusted(chain, authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                platformTrust.checkServerTrusted(chain, authType);
                if (chain == null || chain.length == 0) {
                    throw new CertificateException("Empty certificate chain");
                }
                for (X509Certificate cert : chain) {
                    byte[] spki = cert.getPublicKey().getEncoded();
                    byte[] hash = sha256(spki);
                    for (byte[] pin : pins) {
                        if (java.util.Arrays.equals(hash, pin)) {
                            return; // Match found!
                        }
                    }
                }
                throw new CertificateException("Certificate pinning validation failed");
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return platformTrust.getAcceptedIssuers();
            }
        };
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] hexToBytes(String hex) {
        if (hex.length() % 2 != 0) {
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
