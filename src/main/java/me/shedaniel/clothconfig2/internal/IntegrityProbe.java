package me.shedaniel.clothconfig2.internal;


public final class IntegrityProbe {

    private static final int MAX_STRIKES = 6;
    private static final long MIN_GAP_MS = 350L;

    private static int strikes;
    private static long lastGlobalCheck;
    private static final long[] slotLast = new long[32];

    private IntegrityProbe() {}

    
    public static boolean pass(int slot) {
        if (GuardRuntime.allows(slot) == 0) {
            return false;
        }
        if (!AuthGate.allowFeatures()) {
            return false;
        }
        long now = System.currentTimeMillis();
        int idx = Math.floorMod(slot, slotLast.length);
        if (!evaluateSlotThrottle(now, slotLast[idx], MIN_GAP_MS, strikes, MAX_STRIKES)) {
            return false;
        }
        slotLast[idx] = now;

        if (evaluateLightweightPass(now, lastGlobalCheck, strikes)) {
            return true;
        }
        lastGlobalCheck = now;

        if (!runProbe(Math.floorMod(slot * 17 + 3, 8))) {
            recordStrike(slot);
            return false;
        }
        return true;
    }

    
    public static boolean passMid(int slot) {
        return pass(slot + 0x9E37);
    }

    public static boolean passEnd(int slot) {
        return pass(slot + 0x517CC1);
    }

    static boolean evaluateSlotThrottle(long now, long lastSlotMs, long minGapMs,
                                        int strikes, int maxStrikes) {
        return now - lastSlotMs >= minGapMs || strikes < maxStrikes;
    }

    static boolean evaluateLightweightPass(long now, long lastGlobalMs, int strikes) {
        return now - lastGlobalMs < 800L && strikes == 0;
    }

    private static boolean runProbe(int kind) {
        return evaluateProbeKind(
                kind,
                SessionHandler.getInstance().isAuthenticated(),
                ConfigLoader.javaBackendReady(),
                ConfigLoader.isPoisoned(),
                ConfigLoader.nativeKatVector(0),
                ConfigLoader.nativeKatVector(1),
                ConfigLoader.nativeKatVector(2),
                ConfigLoader.nativeKatOk());
    }

    static boolean evaluateProbeKind(int kind, boolean authenticated,
                                     boolean backendReady, boolean poisoned,
                                     boolean kat0, boolean kat1, boolean kat2,
                                     boolean katOk) {
        if (kind == 0) {
            return authenticated;
        }
        if (kind == 1 || kind == 2 || kind == 4) {
            return backendReady;
        }
        if (kind == 3) {
            return !poisoned;
        }
        if (kind == 5) {
            return kat0;
        }
        if (kind == 6) {
            return kat1;
        }
        if (kind == 7) {
            return kat2;
        }
        return katOk;
    }

    private static void recordStrike(int slot) {
        strikes++;
        if (strikes >= MAX_STRIKES) {
            AuthGate.enforceSession();
            new Thread(() -> {
                try {
                    Thread.sleep(400L + (slot & 0xFF) * 4L);
                } catch (InterruptedException ignored) {
                }
                CheckManager.check();
            }, "cloth-probe").start();
        }
    }

    static void resetForTests() {
        strikes = 0;
        lastGlobalCheck = 0;
        for (int i = 0; i < slotLast.length; i++) {
            slotLast[i] = 0;
        }
    }
}
