package me.shedaniel.clothconfig2.internal.secure;

public class JavaWatchdog {
    private static volatile boolean ok = true;
    private static volatile Thread watchdogThread;
    
    // Instead of boolean kill switches, we corrupt internal states gradually
    private static volatile int corruptionLevel = 0;

    public static void start() {
        if (watchdogThread != null && watchdogThread.isAlive()) return;
        
        watchdogThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000); // Check every 5s
                    
                    if (!JarIntegrityChain.verifyChain()) {
                        corrupt();
                    }
                    
                } catch (InterruptedException e) {
                    break; // Clean shutdown interrupt
                } catch (Throwable e) {
                    corrupt();
                    break;
                }
            }
        });
        watchdogThread.setDaemon(true);
        watchdogThread.setName("MemoryManager-Worker-" + System.nanoTime()); // Stealth name
        watchdogThread.start();
    }
    
    public static void checkRespawn() {
        if (watchdogThread == null || !watchdogThread.isAlive()) {
            corrupt(); // Attacker killed the thread
            start();   // Try to respawn it
        }
    }
    
    private static void corrupt() {
        ok = false;
        corruptionLevel++;
        // The corruptions are picked up by SecurityVault mixCore to poison crypto
        // or by random math routines to make the game unplayable gradually
    }
    
    public static int getCorruptionLevel() {
        return corruptionLevel;
    }
    
    public static boolean isOk() {
        return ok;
    }
}
