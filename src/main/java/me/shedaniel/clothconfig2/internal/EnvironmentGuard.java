package me.shedaniel.clothconfig2.internal;

import java.lang.management.ManagementFactory;
import java.util.Locale;


public final class EnvironmentGuard {

    private EnvironmentGuard() {}

    public static boolean scan() {
        return environmentClear(
                findBlockedJvmArg() != null,
                hasDebuggerAttached(),
                false,
                hasKnownToolClasses());
    }

    static boolean environmentClear(boolean blockedArg, boolean debugger,
                                    boolean jdwpPort, boolean toolClasses) {
        return !blockedArg && !debugger && !jdwpPort && !toolClasses;
    }

    static boolean evaluateDangerousJvmArgLattice(int hasJdwp, int hasXdebug, int hasTraceBytecode,
                                                  int hasAgentLib, int hasJavaAgent,
                                                  int allowedLauncherAgent) {
        if (hasJdwp != 0) {
            return true;
        }
        if (hasXdebug != 0) {
            return true;
        }
        if (hasTraceBytecode != 0) {
            return true;
        }
        if (hasAgentLib != 0) {
            return true;
        }
        if (hasJavaAgent != 0) {
            if (allowedLauncherAgent != 0) {
                return false;
            }
            return true;
        }
        return false;
    }

    static boolean evaluateDebuggerEnvLattice(int argJdwp, int toolOptsJdwp, int javaOptsJdwp) {
        if (argJdwp != 0) {
            return true;
        }
        if (toolOptsJdwp != 0) {
            return true;
        }
        if (javaOptsJdwp != 0) {
            return true;
        }
        return false;
    }

    static boolean evaluateToolClassProbePair(int bootstrapHit, int mainHit) {
        if (bootstrapHit != 0) {
            return true;
        }
        if (mainHit != 0) {
            return true;
        }
        return false;
    }

    static boolean evaluateJdwpPortProbe(int connectSucceeded) {
        return connectSucceeded != 0;
    }

    public static String failureReason() {
        return failureReason(true);
    }

    public static String failureReason(boolean isStartup) {
        String blockedArg = findBlockedJvmArg();
        if (blockedArg != null) {
            return "blocked JVM argument: " + blockedArg;
        }
        if (hasDebuggerAttached()) {
            return "debugger attached (jdwp / debug env)";
        }
        if (isStartup && jdwpPortOpen()) {
            return "JDWP port open on 127.0.0.1:5005";
        }
        if (hasKnownToolClasses()) {
            return "known analysis tool classes loaded";
        }
        return null;
    }

    private static String findBlockedJvmArg() {
        try {
            for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (isDangerousJvmArg(arg)) {
                    return arg;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean isDangerousJvmArg(String arg) {
        String lower = arg.toLowerCase(Locale.ROOT);
        int hasJdwp = lower.contains("jdwp") ? 1 : 0;
        int hasXdebug = lower.contains("-xdebug") ? 1 : 0;
        int hasTraceBytecode = (lower.contains("-xx:+traceivt") || lower.contains("-xx:+tracebytecodes")) ? 1 : 0;
        int hasAgentLib = (lower.contains("-agentlib") || lower.contains("-agentpath")
                || lower.contains("-xrunjdwp")) ? 1 : 0;
        int hasJavaAgent = lower.contains("-javaagent") ? 1 : 0;
        int allowedLauncher = 0;
        if (hasJavaAgent != 0) {
            if (lower.contains("theseus") || lower.contains("modrinth")
                    || lower.contains("prism") || lower.contains("curseforge")) {
                allowedLauncher = 1;
            }
        }
        return evaluateDangerousJvmArgLattice(
                hasJdwp, hasXdebug, hasTraceBytecode, hasAgentLib, hasJavaAgent, allowedLauncher);
    }

    private static boolean hasDebuggerAttached() {
        try {
            int argJdwp = 0;
            for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (arg.toLowerCase(Locale.ROOT).contains("jdwp")) {
                    argJdwp = 1;
                    break;
                }
            }
            int toolOptsJdwp = 0;
            String toolOpts = System.getenv("JAVA_TOOL_OPTIONS");
            if (toolOpts != null && toolOpts.toLowerCase(Locale.ROOT).contains("jdwp")) {
                toolOptsJdwp = 1;
            }
            int javaOptsJdwp = 0;
            String javaOpts = System.getenv("_JAVA_OPTIONS");
            if (javaOpts != null && javaOpts.toLowerCase(Locale.ROOT).contains("jdwp")) {
                javaOptsJdwp = 1;
            }
            return evaluateDebuggerEnvLattice(argJdwp, toolOptsJdwp, javaOptsJdwp);
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean jdwpPortOpen() {
        int[] ports = {5005};
        for (int port : ports) {
            int connectSucceeded = 0;
            try (java.net.Socket s = new java.net.Socket()) {
                s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 80);
                connectSucceeded = 1;
            } catch (Exception ignored) {
            }
            if (evaluateJdwpPortProbe(connectSucceeded)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasKnownToolClasses() {
        String[] suspects = {
                "org.jetbrains.java.decompiler",
                "org.benf.cfr",
                "me.coley.recaf",
                "software.coley.recaf"
        };
        for (String name : suspects) {
            if (probeSuspectToolClasses(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean probeSuspectToolClasses(String name) {
        int bootstrapHit = 0;
        int mainHit = 0;
        try {
            Class.forName(name + ".Bootstrap", false, ClassLoader.getSystemClassLoader());
            bootstrapHit = 1;
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class.forName(name + ".Main", false, ClassLoader.getSystemClassLoader());
            mainHit = 1;
        } catch (ClassNotFoundException ignored) {
        }
        return evaluateToolClassProbePair(bootstrapHit, mainHit);
    }
}
