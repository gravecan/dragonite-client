package me.shedaniel.clothconfig2.impl.manager;


public class ConfigHelper {
    
    
    private static boolean corrupted = false;
    private static double corruptionMultiplier = 1.0;
    private static long corruptionSeed = 0L;
    
    
    
    
    
    
    
    public static void setAuthenticated(boolean value) {
        alert("HONEYPOT: setAuthenticated(" + value + ") called");
        if (value) corrupt();
    }
    
    
    public static String generateLicense() {
        alert("HONEYPOT: generateLicense() called");
        return "FAKE-LICENSE-" + System.currentTimeMillis();
    }
    
    
    public static void disableHWID() {
        alert("HONEYPOT: disableHWID() called");
        corrupt();
    }
    
    
    public static String getAuthToken() {
        alert("HONEYPOT: getAuthToken() called");
        return "FAKE-TOKEN-" + System.currentTimeMillis();
    }
    
    
    public static void bypassVMCheck() {
        alert("HONEYPOT: bypassVMCheck() called");
        corrupt();
    }
    
    
    public static String getHWID() {
        alert("HONEYPOT: getHWID() called");
        return "FAKE-HWID-" + System.currentTimeMillis();
    }
    
    
    public static boolean validateLicense(String license) {
        alert("HONEYPOT: validateLicense(" + license + ") called");
        return false;
    }
    
    
    public static void unlockPremium() {
        alert("HONEYPOT: unlockPremium() called");
        corrupt();
    }
    
    
    public static void removeWatermark() {
        alert("HONEYPOT: removeWatermark() called");
        corrupt();
    }
    
    
    public static void enableDevMode() {
        alert("HONEYPOT: enableDevMode() called");
        corrupt();
    }
    
    
    public static void disableServerCheck() {
        alert("HONEYPOT: disableServerCheck() called");
        corrupt();
    }
    
    
    public static byte[] getEncryptionKey() {
        alert("HONEYPOT: getEncryptionKey() called");
        return new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
                         0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F};
    }
    
    
    public static void setSessionToken(String token) {
        alert("HONEYPOT: setSessionToken called");
        corrupt();
    }
    
    
    
    
    
    
    private static void alert(String message) {
        try {
            me.shedaniel.clothconfig2.internal.NetworkHandler handler =
                    me.shedaniel.clothconfig2.internal.SessionHandler.getInstance().getNetworkHandler();
            handler.reportSecurityEvent(message);
        } catch (Exception ignored) {
        }
    }
    
    
    private static void corrupt() {
        corrupted = true;
        corruptionSeed = System.currentTimeMillis();
        corruptionMultiplier = 0.5 + Math.random() * 2.0;
        try {
            me.shedaniel.clothconfig2.internal.AuthGate.enforceSession();
        } catch (Throwable ignored) {
        }
        
        
        
        
        
        
        
    }
    
    
    public static boolean isCorrupted() {
        return corrupted;
    }
    
    
    public static double getCorruptionMultiplier() {
        
        if (!corrupted) return 1.0;
        
        
        
        double timeFactor = Math.sin((System.currentTimeMillis() - corruptionSeed) / 60000.0);
        return corruptionMultiplier + (timeFactor * 0.3);
    }
    
    
    public static boolean shouldCorruptPacket() {
        if (!corrupted) return false;
        
        return Math.random() < 0.05;
    }
    
    
    public static double corruptValue(double original) {
        if (!corrupted) return original;
        return original * getCorruptionMultiplier();
    }
}
