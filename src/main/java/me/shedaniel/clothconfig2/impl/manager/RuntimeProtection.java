package me.shedaniel.clothconfig2.impl.manager;

import me.shedaniel.clothconfig2.internal.ClientDiagnostics;
import me.shedaniel.clothconfig2.internal.EnvironmentGuard;
import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;


public class RuntimeProtection {
    
    private static volatile boolean running = false;
    private static Thread protectionThread;
    
    
    
    
    private static final Set<String> BLACKLISTED_PROCESSES = Set.of(
        "jd-gui", "jadx-gui", "bytecode-viewer", "recaf",
        "jbytestore", "luyten"
    );
    
    private static final Set<String> BLACKLISTED_THREADS = Set.of(
        "JDWP", "JDI", "debugger", "decompiler",
        "Recaf", "jadx", "BytecodeViewer"
    );
    
    
    private static int expectedClassCount = 0;
    private static long startTime = 0;
    
    
    public static void start() {
        if (running) {
            return;
        }

        running = true;
        startTime = System.currentTimeMillis();
        expectedClassCount = countLoadedClasses();
        
        protectionThread = new Thread(() -> {
            int consecutiveFailures = 0;
            long lastProcessCheck = 0;
            while (running) {
                try {
                    Thread.sleep(30000 + ThreadLocalRandom.current().nextInt(15000));

                    
                    
                    
                    if (System.currentTimeMillis() - startTime < 90_000L) {
                        continue;
                    }

                    long now = System.currentTimeMillis();
                    boolean checkProcesses = (now - lastProcessCheck) > 300_000L;
                    if (checkProcesses) {
                        lastProcessCheck = now;
                    }

                    String failure = performChecksDetailed(checkProcesses);
                    if (failure != null) {
                        if (++consecutiveFailures >= 2) {
                            ClientDiagnostics.fatalHalt("RuntimeProtection: " + failure);
                        }
                    } else {
                        consecutiveFailures = 0;
                    }
                } catch (InterruptedException e) {
                    
                    
                    return;
                }
            }
        }, "ClothConfig-Protection");
        
        protectionThread.setDaemon(true);
        protectionThread.setPriority(Thread.MIN_PRIORITY);
        protectionThread.start();
    }
    
    public static void stop() {
        running = false;
        if (protectionThread != null) {
            protectionThread.interrupt();
        }
    }
    
    
    private static String performChecksDetailed(boolean checkProcesses) {
        if (!EnvironmentGuard.scan()) {
            return EnvironmentGuard.failureReason() != null
                    ? EnvironmentGuard.failureReason()
                    : "environment guard";
        }
        if (detectSuspiciousThreads()) {
            return "suspicious thread name";
        }
        if (checkProcesses && detectBlacklistedProcesses()) {
            return "blacklisted process";
        }
        if (!verifyMemoryIntegrity()) {
            return "memory/class integrity";
        }
        if (detectTimeManipulation()) {
            return "time manipulation";
        }
        if (!verifyClassLoader()) {
            return "class loader";
        }
        return null;
    }
    
    
    private static boolean detectSuspiciousThreads() {
        ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
        while (rootGroup.getParent() != null) {
            rootGroup = rootGroup.getParent();
        }
        int activeCount = rootGroup.activeCount();
        Thread[] threads = new Thread[activeCount * 2 + 50];
        int actualCount = rootGroup.enumerate(threads, true);

        for (int i = 0; i < actualCount; i++) {
            Thread thread = threads[i];
            if (thread == null) continue;
            String name = thread.getName();
            if (name == null) continue;
            String lowerName = name.toLowerCase();
            
            for (String blacklisted : BLACKLISTED_THREADS) {
                if (lowerName.contains(blacklisted.toLowerCase())) {
                    return true;
                }
            }
        }

        
        
        return false;
    }
    
    
    private static boolean detectBlacklistedProcesses() {
        try {
            return ProcessHandle.allProcesses()
                .map(ProcessHandle::info)
                .map(ProcessHandle.Info::command)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(String::toLowerCase)
                .anyMatch(cmd -> {
                    for (String blacklisted : BLACKLISTED_PROCESSES) {
                        if (cmd.contains(blacklisted)) {
                            return true;
                        }
                    }
                    return false;
                });
        } catch (Exception ignored) {
            return false;
        }
    }
    
    
    private static boolean verifyMemoryIntegrity() {
        int currentCount = countLoadedClasses();
        if (currentCount < expectedClassCount - 10) {
            return false;
        }
        
        if (System.currentTimeMillis() - startTime < 120_000L) {
            return true;
        }
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        return usedMemory <= maxMemory * 0.95;
    }
    
    
    private static boolean detectTimeManipulation() {
        long jvmStart = ManagementFactory.getRuntimeMXBean().getStartTime();
        long elapsedReal = System.currentTimeMillis() - jvmStart;
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        return Math.abs(elapsedReal - uptime) > 30_000L;
    }
    
    
    private static boolean verifyClassLoader() {
        
        ClassLoader loader = RuntimeProtection.class.getClassLoader();
        
        
        String loaderClass = loader.getClass().getName();
        
        if (loaderClass.contains("Decomp") || 
            loaderClass.contains("Recaf") ||
            loaderClass.contains("Jadx") ||
            loaderClass.contains("Bytecode")) {
            return false;
        }
        
        return true;
    }
    
    
    private static int countLoadedClasses() {
        try {
            return ManagementFactory.getClassLoadingMXBean().getLoadedClassCount();
        } catch (Exception e) {
            return 0;
        }
    }
    
    
    public static boolean isRunning() {
        return running && protectionThread != null && protectionThread.isAlive();
    }
}
