package me.shedaniel.clothconfig2.internal;


public final class ClientLog {

    private ClientLog() {}

    public static boolean verbose() {
        return Boolean.getBoolean(BuildFingerprint.decrypt("3b2d3e383031362b3a713b3a3d2a38"));
    }

    public static void out(String tag, String message) {
        if (!BuildFingerprint.isReleaseBuild() && verbose()) {
            System.out.println("[" + tag + "] " + message);
        }
    }

    
    public static void err(String tag, String message) {
        if (!BuildFingerprint.isReleaseBuild()) {
            System.err.println("[" + tag + "] " + message);
        }
    }
}
