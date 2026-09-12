package me.shedaniel.clothconfig2.injection;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Optional;

/**
 * Handles attaching the Dragonite agent JAR to a running JVM process.
 *
 * Every operation logs detailed diagnostics via System.out/err so the
 * DragoniteLoader GUI can display exactly what's happening.
 */
public class AgentAttacher {

    private static final String ATTACH_CLASS = "com.sun.tools.attach.VirtualMachine";

    /**
     * Attaches the agent JAR to the current JVM process.
     */
    public static boolean attachToSelf() {
        String pid = getCurrentPid();
        System.out.println("[Dragonite-Agent] Self-attach to PID: " + pid);
        return attachToProcess(pid);
    }

    /**
     * Attaches the agent JAR to the specified process.
     * Logs every step for the GUI log panel.
     */
    public static boolean attachToProcess(String pid) {
        try {
            // Step 1: Load Attach API
            System.out.println("[Dragonite-Agent] Loading Attach API...");
            Class<?> vmClass = loadAttachApi();
            if (vmClass == null) {
                System.err.println("[Dragonite-Agent] FAILED: Could not load com.sun.tools.attach.VirtualMachine");
                System.err.println("[Dragonite-Agent] Make sure you're running with a JDK (not JRE)");
                System.err.println("[Dragonite-Agent] Or add --add-modules jdk.attach to JVM args");
                return false;
            }
            System.out.println("[Dragonite-Agent] Attach API loaded OK: " + vmClass.getName());

            // Step 2: Attach to target JVM
            System.out.println("[Dragonite-Agent] Attaching to JVM PID " + pid + "...");
            Method attachMethod = vmClass.getMethod("attach", String.class);
            Object vm = attachMethod.invoke(null, pid);
            System.out.println("[Dragonite-Agent] VirtualMachine.attach() OK — connected to PID " + pid);

            // Step 3: Resolve agent JAR path
            String agentPath = getAgentPath();
            if (agentPath == null) {
                System.err.println("[Dragonite-Agent] FAILED: Could not determine agent JAR location");
                System.err.println("[Dragonite-Agent] Are you running from a built JAR? (not loose class files)");
                // Detach before failing
                try {
                    vmClass.getMethod("detach").invoke(vm);
                } catch (Exception ignored) {}
                return false;
            }

            // Step 4: Verify the JAR file exists and is readable
            File jarFile = new File(agentPath);
            if (!jarFile.exists()) {
                System.err.println("[Dragonite-Agent] FAILED: agent JAR missing");
                return false;
            }

            // Step 5: Load the agent into the target JVM
            Method loadAgentMethod = vmClass.getMethod("loadAgent", String.class);
            loadAgentMethod.invoke(vm, agentPath);
            System.out.println("[Dragonite-Agent] VirtualMachine.loadAgent() OK — agent loaded into target");

            // Step 6: Detach
            System.out.println("[Dragonite-Agent] Detaching from target JVM...");
            Method detachMethod = vmClass.getMethod("detach");
            detachMethod.invoke(vm);
            System.out.println("[Dragonite-Agent] Detached OK");

            System.out.println("[Dragonite-Agent] Successfully attached agent to PID " + pid);
            System.out.println("[Dragonite-Agent] The agent's agentmain() should now be executing inside Minecraft");
            return true;

        } catch (Exception e) {
            System.err.println("[Dragonite-Agent] Attach FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("[Dragonite-Agent] Caused by: " + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage());
            }
            // Print some common failure causes
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("not attach") || msg.contains("permission")) {
                System.err.println("[Dragonite-Agent] TIP: This might be a permissions issue.");
                System.err.println("[Dragonite-Agent] Try running as Administrator, or ensure both JVMs use the same user.");
            }
            if (msg.contains("no such process")) {
                System.err.println("[Dragonite-Agent] TIP: The target process may have exited. Re-scan for Minecraft.");
            }
            if (msg.contains("not a jdk") || msg.contains("tools.jar")) {
                System.err.println("[Dragonite-Agent] TIP: Make sure you're running with a full JDK, not a JRE.");
            }
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Auto-detects a running Minecraft process and attaches to it.
     */
    public static boolean attachToMinecraft() {
        System.out.println("[Dragonite-Agent] Auto-detecting Minecraft process...");
        Optional<String> mcPid = findMinecraftProcess();
        if (mcPid.isPresent()) {
            System.out.println("[Dragonite-Agent] Found Minecraft at PID: " + mcPid.get());
            return attachToProcess(mcPid.get());
        }
        System.err.println("[Dragonite-Agent] No Minecraft process found among running JVMs");
        return false;
    }

    /**
     * Scans running JVMs to find one that looks like a Minecraft instance.
     * Logs all discovered JVMs for diagnostic purposes.
     */
    public static Optional<String> findMinecraftProcess() {
        System.out.println("[Dragonite-Agent] Scanning active OS processes (ProcessHandle)...");
        String selfPid = getCurrentPid();
        
        // 1. Modern Java 9+ OS-level process scan (bypasses VirtualMachine.list() limitations)
        try {
            java.util.List<ProcessHandle> targets = ProcessHandle.allProcesses()
                .filter(ph -> ph.pid() != Long.parseLong(selfPid))
                .filter(ph -> {
                    var info = ph.info();
                    String cmd = info.command().orElse("").toLowerCase();
                    String cmdLine = info.commandLine().orElse("").toLowerCase();
                    
                    // Match javaw, java, lunar client or badlion client host processes
                    if (cmd.contains("javaw") || cmd.contains("java") || 
                        cmd.contains("lunarclient") || cmd.contains("badlion")) {
                        
                        // Check command path or command line parameters for keywords
                        return cmd.contains("minecraft") || cmdLine.contains("minecraft")
                            || cmd.contains("lunar") || cmdLine.contains("lunar")
                            || cmd.contains("genesis") || cmdLine.contains("genesis")
                            || cmd.contains("moonsworth") || cmdLine.contains("moonsworth")
                            || cmd.contains("badlion") || cmdLine.contains("badlion")
                            || cmdLine.contains("fabric-loader") || cmdLine.contains("knot")
                            || cmdLine.contains("launchwrapper");
                    }
                    return false;
                })
                .toList();

            if (!targets.isEmpty()) {
                String foundPid = String.valueOf(targets.get(0).pid());
                System.out.println("[Dragonite-Agent] ProcessHandle found target PID: " + foundPid);
                return Optional.of(foundPid);
            }
        } catch (Throwable t) {
            System.err.println("[Dragonite-Agent] ProcessHandle scan failed (insufficient permissions?): " + t.getMessage());
        }

        // 2. Fallback to default Java VirtualMachine.list() Attach API scan
        System.out.println("[Dragonite-Agent] Falling back to JVM list scan...");
        try {
            Class<?> vmClass = loadAttachApi();
            if (vmClass == null) {
                System.err.println("[Dragonite-Agent] Cannot scan: Attach API not available");
                return Optional.empty();
            }

            Method listMethod = vmClass.getMethod("list");
            @SuppressWarnings("unchecked")
            java.util.List<Object> vms = (java.util.List<Object>) listMethod.invoke(null);

            for (Object vmDesc : vms) {
                Method displayNameMethod = vmDesc.getClass().getMethod("displayName");
                String displayName = (String) displayNameMethod.invoke(vmDesc);
                Method idMethod = vmDesc.getClass().getMethod("id");
                String id = (String) idMethod.invoke(vmDesc);

                if (id.equals(selfPid)) continue;

                if (isMinecraftProcess(displayName)) {
                    System.out.println("[Dragonite-Agent] JVM list found target PID: " + id);
                    return Optional.of(id);
                }
            }
        } catch (Exception e) {
            System.err.println("[Dragonite-Agent] JVM scan error: " + e.getMessage());
            e.printStackTrace();
        }
        return Optional.empty();
    }

    /**
     * Heuristic check for whether a JVM display name belongs to Minecraft.
     */
    private static boolean isMinecraftProcess(String displayName) {
        if (displayName == null) return false;
        String lower = displayName.toLowerCase();
        return lower.contains("minecraft")
            || lower.contains("net.minecraft")
            || lower.contains("launchwrapper")
            || lower.contains("net.fabricmc")
            || lower.contains("cpw.mods")
            || lower.contains("gradlew")
            || lower.contains("knot")
            || lower.contains("fabricloader")
            || lower.contains("lunar")
            || lower.contains("genesis")
            || lower.contains("moonsworth")
            || lower.contains("badlion");
    }

    /**
     * Returns the PID of the current JVM.
     */
    public static String getCurrentPid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        return name.split("@")[0];
    }

    // ---- Internal: Attach API loading ----

    private static Class<?> loadAttachApi() {
        // JDK 9+ module system
        try {
            return Class.forName(ATTACH_CLASS);
        } catch (ClassNotFoundException e) {
            // JDK 8 fallback: load tools.jar
            try {
                String javaHome = System.getProperty("java.home");
                File toolsJar = new File(javaHome, "../lib/tools.jar");
                if (!toolsJar.exists()) {
                    toolsJar = new File(javaHome, "lib/tools.jar");
                }
                if (toolsJar.exists()) {
                    System.out.println("[Dragonite-Agent] Loading Attach API from: " + toolsJar.getAbsolutePath());
                    URLClassLoader loader = new URLClassLoader(
                        new URL[]{ toolsJar.toURI().toURL() },
                        AgentAttacher.class.getClassLoader()
                    );
                    return loader.loadClass(ATTACH_CLASS);
                }
                return Class.forName(ATTACH_CLASS);
            } catch (Exception ex) {
                System.err.println("[Dragonite-Agent] All Attach API loading methods failed");
                return null;
            }
        }
    }

    // ---- Internal: JAR path resolution ----

    /**
     * Attach API needs a path to a JAR that is already on disk.
     * Never extract a temp {@code .jar} — that creates an extra USN journal hit.
     * If we only have loose classes / no JAR, return null and let native inject handle it.
     */
    private static String getAgentPath() {
        try {
            URL location = AgentAttacher.class.getProtectionDomain().getCodeSource().getLocation();
            if (location != null) {
                File file = new File(location.toURI());
                if (file.isFile() && file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
                    return file.getAbsolutePath();
                }
            }
        } catch (Exception ignored) {
        }

        String fromClasspath = firstClasspathJar();
        if (fromClasspath != null) {
            return fromClasspath;
        }

        return null;
    }

    private static String firstClasspathJar() {
        String cp = System.getProperty("java.class.path", "");
        if (cp.isBlank()) {
            return null;
        }
        for (String entry : cp.split(File.pathSeparator)) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            File f = new File(entry.trim());
            if (f.isFile() && f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
                return f.getAbsolutePath();
            }
        }
        return null;
    }
}
