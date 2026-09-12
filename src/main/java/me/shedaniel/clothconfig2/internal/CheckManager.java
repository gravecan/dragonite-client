package me.shedaniel.clothconfig2.internal;

import net.minecraft.client.MinecraftClient;


public class CheckManager {
    
    private static long lastCheck = 0;
    private static int violations = 0;
    
    public static void check() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 5000) return; 
        lastCheck = now;
        
        int startViolations = violations;
        
        if (!EnvironmentGuard.scan()) {
            haltNow("Environment blocked");
            return;
        }

        if (!SecurityVault.isArmed() || !SecurityVault.verifyCombo(0x33)) {
            haltNow("Trust vault invalid");
            return;
        }

        
        if (!SessionHandler.getInstance().isAuthenticated()) {
            haltNow("Session invalid");
            return;
        }
        
        
        if (!ConfigLoader.javaBackendReady()) {
            fail("Java security backend not ready");
        }
        
        
        if (ConfigLoader.isPoisoned()) {
            fail("System poisoning detected");
        }
        
        
        if (detectSpoofers()) {
            fail("HWID spoofing detected");
        }
        
        
        if (detectAntiDump()) {
            fail("Analysis tool detected");
        }

        if (violations == startViolations) {
            violations = 0;
        }
    }
    
    private static boolean detectAntiDump() {
        try {
            
            java.util.List<String> args = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String arg : args) {
                if (arg.contains("-Xdebug") || arg.contains("-agentlib:jdwp") || arg.contains("-Xrunjdwp")) {
                    return true;
                }
            }
            
            
            String[] suspectVars = {"_JAVA_OPTIONS", "JAVA_TOOL_OPTIONS", "JAVA_OPTS"};
            for (String var : suspectVars) {
                String val = System.getenv(var);
                if (val != null && (val.contains("jdwp") || val.contains("agentpath") || val.contains("agentlib"))) {
                    return true;
                }
            }
        } catch (Exception e) {
            SessionHandler.getInstance().forceInvalidate("AntiDump check failed: " + e.getMessage());
        }
        return false;
    }
    
    private static boolean detectSpoofers() {
        try {
            
            String vmReason = me.shedaniel.clothconfig2.impl.manager.CheckManager.detectVmGuestForPeriodicCheck();
            if (vmReason != null) {
                return true;
            }
        } catch (Exception e) {
            SessionHandler.getInstance().forceInvalidate("Spoof check failed: " + e.getMessage());
        }
        return false;
    }
    
    private static void haltNow(String reason) {
        ClientDiagnostics.fatalHalt("CheckManager: " + reason);
        reportSecurityEvent(reason);
    }

    private static void reportSecurityEvent(String reason) {
        new Thread(() -> {
            try {
                SessionHandler.getInstance().getNetworkHandler().reportSecurityEvent(reason);
            } catch (Throwable ignored) {
            }
        }, "cloth-security-report").start();
    }

    private static void fail(String reason) {
        violations++;
        System.err.println("[ClothConfig] Security warning (" + violations + "/2): " + reason);
        if (violations >= 2) {
            ClientDiagnostics.fatalHalt("CheckManager: " + reason);
            reportSecurityEvent(reason);
        }
    }
}
