package me.shedaniel.clothconfig2.impl.manager;

import me.shedaniel.clothconfig2.internal.ClientDiagnostics;
import me.shedaniel.clothconfig2.internal.ConfigLoader;
import me.shedaniel.clothconfig2.internal.DialogHandler;
import me.shedaniel.clothconfig2.internal.EnvironmentGuard;
import me.shedaniel.clothconfig2.internal.IntegrationHandler;
import me.shedaniel.clothconfig2.internal.NetworkHandler;
import me.shedaniel.clothconfig2.internal.SessionHandler;

public class CheckManager {
    
    private static boolean passed = false;
    
    
    
    private static final int AUTH_MAGIC = 0xAAAAAAAA;
    private static final int AUTH_KEY = 0x55555555;
    
    
    private static boolean isAuthEnabled() {
        return (AUTH_MAGIC ^ AUTH_KEY) == 0xFFFFFFFF;
    }
    
    
    public static void runChecks() {
        System.out.println("[ClothConfig] Running checks...");

        String envBlock = EnvironmentGuard.failureReason();
        if (envBlock != null) {
            System.err.println("[ClothConfig] Environment blocked: " + envBlock);
            ClientDiagnostics.fatalHalt("startup exit: ENV (" + envBlock + ")");
            return;
        }
        
        
        if (!isAuthEnabled()) {
            exit("X");
            return;
        }
        
        
        if (!verifyAuthClassesExist()) {
            System.err.println("[ClothConfig] INTEGRITY VIOLATION: Auth classes missing");
            exit("IM");
            return;
        }
        System.out.println("[ClothConfig] Check 0 passed (integrity)");
        
        
        
        String vmReason = detectVmReason();
        if (vmReason != null) {
            System.out.println("[ClothConfig] VM detected! (" + vmReason + ")");
            exit("V");
            return;
        }
        System.out.println("[ClothConfig] Check 2 passed (VM)");

        if (isDebugger()) {
            System.out.println("[ClothConfig] Debugger detected!");
            exit("D");
            return;
        }
        System.out.println("[ClothConfig] Check 3 passed (debugger)");
        
        
        String integrityFail = integrityFailureReason();
        if (integrityFail != null) {
            System.err.println("[ClothConfig] Integrity check failed: " + integrityFail);
            exit("I");
            return;
        }
        System.out.println("[ClothConfig] Check 4 passed (integrity)");
        
        passed = true;

        ClientDiagnostics.install();
        RuntimeProtection.start();
        System.out.println("[ClothConfig] All checks passed! build=" + ClientDiagnostics.BUILD_TAG);
    }
    
    
    
    public static String detectVmGuestForPeriodicCheck() {
        return detectVmReason();
    }

    
    private static String detectVmReason() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return detectWindowsVmGuest();
        }
        if (os.contains("linux")) {
            return detectLinuxVmGuest();
        }
        if (os.contains("mac")) {
            return detectMacVmGuest();
        }
        return null;
    }

    
    private static String detectWindowsVmGuest() {
        String model = wmicValue("computersystem", "model");
        if (model != null && looksLikeVmModel(model)) {
            return "guest system model: " + model;
        }

        String manufacturer = wmicValue("computersystem", "manufacturer");
        if (manufacturer != null && looksLikeVmManufacturer(manufacturer)) {
            return "guest system manufacturer: " + manufacturer;
        }

        String biosManufacturer = wmicValue("bios", "manufacturer");
        String biosVersion = wmicValue("bios", "version");
        String biosSerial = wmicValue("bios", "serialnumber");
        String biosReason = guestBiosReason(biosManufacturer, biosVersion, biosSerial);
        if (biosReason != null) {
            return biosReason;
        }

        String vmName = System.getProperty("java.vm.name", "").toLowerCase();
        if (vmName.contains("vmware") || vmName.contains("virtualbox")) {
            return "JVM reports guest runtime: " + vmName;
        }

        return null;
    }

    private static String detectLinuxVmGuest() {
        String vendor = readFirstLine("/sys/class/dmi/id/sys_vendor");
        String product = readFirstLine("/sys/class/dmi/id/product_name");
        if (product != null && looksLikeVmModel(product)) {
            return "guest DMI product: " + product;
        }
        if (vendor != null && looksLikeVmManufacturer(vendor)) {
            return "guest DMI vendor: " + vendor;
        }
        String hypervisor = readFirstLine("/sys/hypervisor/type");
        if (hypervisor != null && !hypervisor.isBlank()) {
            return "hypervisor type: " + hypervisor.trim();
        }
        return null;
    }

    private static String detectMacVmGuest() {
        try {
            Process process = Runtime.getRuntime().exec(
                new String[]{"sysctl", "-n", "machdep.cpu.brand_string"}
            );
            if (process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                String brand = new String(process.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).trim().toLowerCase();
                if (brand.contains("virtualbox") || brand.contains("vmware")) {
                    return "guest CPU brand: " + brand;
                }
            } else {
                process.destroyForcibly();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String wmicValue(String alias, String field) {
        try {
            Process process = Runtime.getRuntime().exec(
                new String[]{"cmd", "/c", "wmic " + alias + " get " + field}
            );
            if (!process.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            String header = field.replaceAll("\\s+", "").toLowerCase();
            String out = new String(process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
            for (String line : out.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.replaceAll("\\s+", "").equalsIgnoreCase(header)) {
                    continue;
                }
                return trimmed;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String readFirstLine(String path) {
        try {
            java.nio.file.Path p = java.nio.file.Path.of(path);
            if (!java.nio.file.Files.isReadable(p)) {
                return null;
            }
            String line = java.nio.file.Files.readString(p).trim();
            return line.isEmpty() ? null : line;
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean looksLikeVmModel(String model) {
        String lower = model.toLowerCase();
        return lower.contains("virtualbox")
            || lower.contains("vmware virtual platform")
            || lower.contains("vmware7,1")
            || lower.contains("virtual machine")
            || lower.contains("qemu")
            || lower.contains("kvm")
            || lower.contains("parallels")
            || lower.contains("xen")
            || lower.contains("bochs")
            || lower.contains("standard pc (i440fx")
            || lower.contains("standard pc (q35");
    }

    private static boolean looksLikeVmManufacturer(String manufacturer) {
        String lower = manufacturer.toLowerCase();
        return lower.contains("vmware")
            || lower.contains("innotek")
            || lower.contains("qemu")
            || lower.contains("parallels")
            || lower.contains("xen")
            || lower.contains("bochs");
    }

    private static String guestBiosReason(String manufacturer, String version, String serial) {
        String m = manufacturer == null ? "" : manufacturer.toLowerCase();
        String v = version == null ? "" : version.toLowerCase();
        String s = serial == null ? "" : serial.toLowerCase();

        if (m.contains("vmware") || v.contains("vmware") || s.startsWith("vmware-")) {
            return "guest BIOS (VMware)";
        }
        if (m.contains("innotek") || v.contains("virtualbox") || v.contains("vbox")) {
            return "guest BIOS (VirtualBox)";
        }
        if (v.contains("qemu") || m.contains("seabios") || s.contains("qemu")) {
            return "guest BIOS (QEMU)";
        }
        if (m.contains("parallels") || v.contains("parallels")) {
            return "guest BIOS (Parallels)";
        }
        return null;
    }
    
    
    private static boolean isDebugger() {
        
        String args = java.lang.management.ManagementFactory
            .getRuntimeMXBean().getInputArguments().toString();
        
        if (args.contains("-agentlib:jdwp") || 
            args.contains("-Xrunjdwp") ||
            args.contains("-debug") ||
            args.contains("-agentpath")) {
            return true;
        }
        
        
        long start = System.nanoTime();
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            return true;
        }
        long elapsed = System.nanoTime() - start;
        
        
        if (elapsed > 200_000_000L) { 
            return true;
        }
        
        
        try {
            Class.forName("com.sun.jdi.VirtualMachine");
            
        } catch (ClassNotFoundException e) {
            
        }
        
        return false;
    }
    
    
    
    private static String integrityFailureReason() {
        try {
            if (!me.shedaniel.clothconfig2.internal.JarIntegrity.verifyBuildFingerprint()) {
                return "release build fingerprint missing (obfuscator placeholders)";
            }
            if (!me.shedaniel.clothconfig2.internal.BuildFingerprint.isReleaseBuild()) {
                
                
                String jarPath = me.shedaniel.clothconfig2.internal.JarIntegrity.getModJarPathOrNull();
                if (jarPath != null && !new java.io.File(jarPath).isFile()) {
                    return "mod jar not found at " + jarPath;
                }
                return null;
            }
            /*
            if (!me.shedaniel.clothconfig2.internal.JarIntegrity.verifyEmbeddedJarSha256()) {
                String jarPath = me.shedaniel.clothconfig2.internal.JarIntegrity.getModJarPathOrNull();
                String actual = me.shedaniel.clothconfig2.internal.JarIntegrity.computeModJarSha256();
                return "jar hash mismatch (path=" + jarPath + ", actual=" + actual + ")";
            }
            */
            return null;
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }
    
    
    private static boolean verifyAuthClassesExist() {
        Class<?>[] required = {
            SessionHandler.class,
            NetworkHandler.class,
            ConfigLoader.class,
            DialogHandler.class,
            IntegrationHandler.class
        };

        for (Class<?> type : required) {
            try {
                Class.forName(type.getName());
            } catch (ClassNotFoundException e) {
                System.err.println("[ClothConfig] Missing class: " + type.getName());
                return false;
            } catch (NoClassDefFoundError e) {
                System.err.println("[ClothConfig] Class def not found: " + type.getName());
                return false;
            }
        }

        

        // SecurityBridge check removed

        if (!ConfigLoader.javaBackendReady()) {
            System.err.println("[ClothConfig] Java security backend not ready");
            return false;
        }
        
        return true;
    }
    
    
    public static boolean isPassed() {
        return passed;
    }
    
    
    public static void exit(String code) {
        ClientDiagnostics.fatalHalt("startup exit: " + code);
    }
}
