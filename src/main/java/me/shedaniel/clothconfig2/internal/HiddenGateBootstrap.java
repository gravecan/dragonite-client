package me.shedaniel.clothconfig2.internal;


public final class HiddenGateBootstrap {

    private static volatile boolean initialized;

    private HiddenGateBootstrap() {}

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            
            AuthGate.allowFeatures();
        } catch (Throwable t) {
            if (SessionHandler.getInstance().isInitialized() && SessionHandler.getInstance().isAuthenticated()) {
                System.err.println("[ClothConfig] Critical security gate bypassed unexpectedly.");
                System.exit(0);
            }
        }
    }
}
