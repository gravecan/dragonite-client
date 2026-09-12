package me.shedaniel.clothconfig2.internal;


public final class GuardRuntime {

    private static final int MAX_FAULTS = 8;
    private static int faults;

    private GuardRuntime() {}

    
    public static int allows(int slot) {
        int tag = SecurityVault.derive(slot) ^ SecurityVault.derive(slot + 5);
        int result = evaluateAllowsCore(
                SecurityVault.isArmed(),
                ConfigLoader.javaBackendReady(),
                ConfigLoader.nativeKatVector(Math.floorMod(slot, 3)),
                SecurityVault.verifyCombo(slot),
                tag,
                SessionHandler.getInstance().isAuthenticated());
        if (result == 0) {
            fault(slot);
        }
        return result;
    }

    static int evaluateAllowsCore(boolean armed, boolean backendReady, boolean katOk,
                                  boolean comboOk, int tag, boolean authenticated) {
        if (!armed || !backendReady || !katOk || !comboOk || tag == 0 || !authenticated) {
            return 0;
        }
        return tag;
    }

    public static boolean permits(int slot) {
        return allows(slot) != 0;
    }

    
    public static void katFault(int slot) {
        fault(slot);
    }

    private static void fault(int slot) {
        faults++;
        if (faults >= MAX_FAULTS) {
            AuthGate.enforceSession();
            new Thread(() -> {
                try {
                    Thread.sleep(300L + (slot & 0x7F) * 5L);
                } catch (InterruptedException ignored) {
                }
                CheckManager.check();
            }, "cloth-guard").start();
        }
    }

    static void resetFaults() {
        faults = 0;
    }
}
