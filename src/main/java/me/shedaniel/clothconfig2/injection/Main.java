package me.shedaniel.clothconfig2.injection;

import javax.swing.SwingUtilities;
import java.util.Optional;

/**
 * Standalone entry point for running the injector.
 *
 * If double-clicked (no arguments and not headless), it launches a custom 
 * inner-Java CMD console window that displays real-time log outputs without 
 * invoking Windows cmd.exe.
 *
 * If run with parameters (e.g. target PID) or headless, it executes directly
 * in the active command-line terminal.
 *
 * Injection strategy:
 *   1. Find Minecraft process
 *   2. If the target is Lunar Client AND native binaries are embedded → native DLL injection
 *      (bypasses -XX:+DisableAttachMechanism and InjGen detection)
 *   3. Otherwise → standard Java Attach API
 */
public class Main {

    /**
     * Runs the full inject flow (find Minecraft, native or attach). Used by the loader GUI
     * and by the optional console window — does not open any extra UI.
     */
    public static boolean executeInjection() {
        Optional<String> mcPid = AgentAttacher.findMinecraftProcess();
        if (mcPid.isEmpty()) {
            System.err.println("[Dragonite-Agent] ERROR: No Minecraft process detected!");
            return false;
        }
        System.out.println("[Dragonite-Agent] Found Minecraft at PID: " + mcPid.get());
        return smartInject(mcPid.get());
    }

    public static void main(String[] args) {
        // Enforce same security guards as the mod version
        me.shedaniel.clothconfig2.internal.secure.JavaWatchdog.start();
        if (!me.shedaniel.clothconfig2.internal.secure.JavaWatchdog.isOk()) {
            System.err.println("[Dragonite-Agent] Security violation: Debugger/poisoning tools detected!");
            System.exit(0);
            return;
        }

        if (!me.shedaniel.clothconfig2.internal.EnvironmentGuard.scan()) {
            System.err.println("[Dragonite-Agent] Security violation: VM or untrusted sandbox detected!");
            System.exit(0);
            return;
        }

        if (!requireAuthenticatedSession()) {
            System.err.println("[Dragonite-Agent] License verification failed. Action blocked.");
            System.exit(1);
            return;
        }

        if (args.length == 0 && !java.awt.GraphicsEnvironment.isHeadless()) {
            // Launch the custom console window
            SwingUtilities.invokeLater(() -> {
                DragoniteConsoleWindow window = new DragoniteConsoleWindow();
                window.setVisible(true);
                
                // Run the injection logic in a background thread so we don't freeze the GUI
                new Thread(() -> {
                    System.out.println("[Dragonite-Agent] Starting injector console...");
                    System.out.println("[Dragonite-Agent] Searching for Minecraft process...");

                    boolean success = executeInjection();

                    if (success) {
                        System.out.println("[Dragonite-Agent] Injection successful!");
                        for (int i = 5; i > 0; i--) {
                            System.out.println("[Dragonite-Agent] Closing in " + i + "...");
                            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                        }
                        System.out.println("[Dragonite-Agent] Closed!");
                        window.restoreStreams();
                        window.dispose();
                        System.exit(0);
                    } else {
                        System.err.println("");
                        System.err.println("[Dragonite-Agent] Injection FAILED.");
                        System.err.println("[Dragonite-Agent] Check the errors above for details.");
                        System.err.println("[Dragonite-Agent] Window will stay open so you can read the log.");
                    }
                }, "Dragonite-Console-Injector").start();
            });
            return;
        }

        // --- CLI Fallback (Command line / headless executions) ---
        System.out.println("[Dragonite-Agent] Starting injector...");
        boolean success;

        if (args.length > 0) {
            String pid = args[0];
            System.out.println("[Dragonite-Agent] Target PID: " + pid);
            success = smartInjectPid(pid);
        } else {
            System.out.println("[Dragonite-Agent] Searching for Minecraft process...");
            Optional<String> mcPid = AgentAttacher.findMinecraftProcess();
            mcPid.ifPresentOrElse(
                pid -> System.out.println("[Dragonite-Agent] Found Minecraft at PID: " + pid),
                ()  -> System.out.println("[Dragonite-Agent] No Minecraft process found, trying self-attach...")
            );
            success = smartInject(mcPid.orElse(null));
        }

        if (success) {
            System.out.println("[Dragonite-Agent] Injection successful!");
        } else {
            System.err.println("[Dragonite-Agent] Injection failed.");
            System.exit(1);
        }
    }

    /**
     * Smart injection: picks native DLL path for Lunar, Attach API for everything else.
     */
    private static boolean smartInject(String pid) {
        if (pid == null) {
            System.err.println("[Dragonite-Agent] ERROR: No Minecraft PID provided.");
            return false;
        }
        return smartInjectPid(pid);
    }

    private static boolean smartInjectPid(String pid) {
        // Re-check immediately before the protected action so an expired,
        // revoked, or replaced session cannot be reused.
        if (!me.shedaniel.clothconfig2.internal.ProtectedActionGate.allowExistingInjectionRequest()) {
            System.err.println("[Dragonite-Agent] Authorization or artifact integrity is no longer valid.");
            return false;
        }

        // Check if native path is available and target is Lunar
        if (NativeInjector.isAvailable()) {
            boolean isLunar = NativeInjector.isLunarProcess(pid);
            if (isLunar) {
                System.out.println("[Dragonite-Agent] Lunar Client detected — using native DLL injection");
                System.out.println("[Dragonite-Agent] (bypasses DisableAttachMechanism + InjGen)");
                return NativeInjector.inject(pid);
            } else {
                System.out.println("[Dragonite-Agent] Non-Lunar target — trying native first, fallback to Attach API");
                // Try native first (works everywhere), fall back to Attach API
                boolean nativeOk = NativeInjector.inject(pid);
                if (nativeOk) return true;
                System.out.println("[Dragonite-Agent] Native path failed, falling back to Attach API...");
            }
        } else {
            System.out.println("[Dragonite-Agent] Native binaries not embedded — using Attach API only");
        }

        // Standard Attach API path
        return AgentAttacher.attachToProcess(pid);
    }

    private static boolean requireAuthenticatedSession() {
        me.shedaniel.clothconfig2.internal.SessionHandler session =
                me.shedaniel.clothconfig2.internal.SessionHandler.getInstance();
        try {
            return session.isInitialized() && session.isAuthenticated();
        } catch (Throwable error) {
            System.err.println("[Dragonite-Agent] License initialization failed: "
                    + error.getClass().getSimpleName());
            return false;
        }
    }
}
