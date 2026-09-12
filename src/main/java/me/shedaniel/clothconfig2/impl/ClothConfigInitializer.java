package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.manager.CheckManager;
import me.shedaniel.clothconfig2.internal.BuildFingerprint;
import me.shedaniel.clothconfig2.internal.KeyHalfFetcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import me.shedaniel.clothconfig2.internal.EnvironmentGuard;
import me.shedaniel.clothconfig2.internal.JoinIntegrityGuard;
import me.shedaniel.clothconfig2.internal.MixinRuntimeProbe;
import me.shedaniel.clothconfig2.internal.SessionHandler;

public class ClothConfigInitializer implements ClientModInitializer {
    
    
    private static final boolean REQUIRE_AUTH = true;
    private boolean protectedClientInitialized;
    
    static {
        System.setProperty("java.awt.headless", "false");
        // silenceLogs();
    }
    
    @Override
    public void onInitializeClient() {
        // Start fetching the server key half in the background immediately
        // so it is cached before any string decryption fires on the render thread.
        KeyHalfFetcher.prefetch(BuildFingerprint.getDrmKeyId());
        
        
        if (REQUIRE_AUTH) {
            ClientTickEvents.START_CLIENT_TICK.register(client -> {
                InjectedClientAssets.ensureClientTextures(client);
                if (!SessionHandler.getInstance().isInitialized()) {
                    var session = client.getSession();
                    if (session == null || session.getUsername() == null || session.getUsername().isBlank()) {
                        return;
                    }
                    var profile = new com.mojang.authlib.GameProfile(session.getUuidOrNull(), session.getUsername());
                    if (!SessionHandler.getInstance().init(profile)) {
                        CheckManager.exit("A");
                        return;
                    }
                }
                // No screen forcing here — license screen is shown lazily in openConfigGui()
                if (SessionHandler.getInstance().isAuthenticated()) {
                    initializeProtectedClient();
                }
            });
            return;
        }

        initializeProtectedClient();
    }

    private void initializeProtectedClient() {
        if (protectedClientInitialized) {
            return;
        }
        protectedClientInitialized = true;

        // Re-trigger key half fetch now that we know the session token is available.
        // The background prefetch started at onInitializeClient() time (before auth),
        // so it may have timed out waiting for the token. Fetch synchronously here
        // to guarantee the server half is in the cache before any string decryption fires.
        String drmKeyId = BuildFingerprint.getDrmKeyId();
        if (drmKeyId != null && !drmKeyId.isBlank()) {
            KeyHalfFetcher.clear();
            // getServerHalf blocks until the fetch completes (or times out after 15s)
            KeyHalfFetcher.getServerHalf(drmKeyId);
        }
        SessionHandler.registerShutdownHook();
        JoinIntegrityGuard.register();

        String envBlock = EnvironmentGuard.failureReason();
        if (envBlock != null) {
            System.err.println("[ClothConfig] Environment blocked: " + envBlock);
            CheckManager.exit("ENV");
            return;
        }

        CheckManager.runChecks();

        if (!CheckManager.isPassed()) {
            return;
        }

        try {
            HudConfigInit.init();
            HudConfigInit.getManager();
            me.shedaniel.clothconfig2.internal.secure.ProtectedModuleRegistrar.registerPostAuthModules();
            MixinRuntimeProbe.logDiagnostics("protected-client-init");
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static void silenceLogs() {
        if (Boolean.getBoolean(me.shedaniel.clothconfig2.internal.BuildFingerprint.decrypt("3b2d3e383031362b3a713b3a3d2a38"))) {
            return;
        }
        try {
            java.io.PrintStream originalOut = System.out;
            java.io.PrintStream originalErr = System.err;

            System.setOut(new java.io.PrintStream(new java.io.OutputStream() {
                private final StringBuilder line = new StringBuilder();
                @Override
                public void write(int b) throws java.io.IOException {
                    if (b == '\n') {
                        String s = line.toString();
                        line.setLength(0);
                        if (!isOurLog(s)) {
                            originalOut.println(s);
                        }
                    } else if (b != '\r') {
                        line.append((char) b);
                    }
                }
            }));

            System.setErr(new java.io.PrintStream(new java.io.OutputStream() {
                private final StringBuilder line = new StringBuilder();
                @Override
                public void write(int b) throws java.io.IOException {
                    if (b == '\n') {
                        String s = line.toString();
                        line.setLength(0);
                        if (!isOurLog(s)) {
                            originalErr.println(s);
                        }
                    } else if (b != '\r') {
                        line.append((char) b);
                    }
                }
            }));
        } catch (Throwable ignored) {}
    }

    private static String _d(int[] data, int key) {
        char[] chars = new char[data.length];
        for (int i = (1 - 1); i < data.length; i++) {
            chars[i] = (char) (data[i] ^ key);
        }
        return new String(chars);
    }

    private static boolean isOurLog(String s) {
        int k = (45 * 2);
        return s.contains(_d(new int[]{1, 25, 54, 53, 46, 50, 25, 53, 52, 60, 51, 61, 7}, k)) 
            || s.contains(_d(new int[]{1, 9, 63, 57, 47, 40, 51, 46, 35, 24, 40, 51, 62, 61, 63, 7}, k)) 
            || s.contains(_d(new int[]{1, 19, 52, 46, 63, 61, 40, 59, 46, 51, 53, 52, 18, 59, 52, 62, 54, 63, 40, 7}, k)) 
            || s.contains(_d(new int[]{1, 30, 51, 59, 54, 53, 61, 18, 59, 52, 62, 54, 63, 40, 7}, k)) 
            || s.contains(_d(new int[]{1, 20, 63, 46, 45, 53, 40, 49, 18, 59, 52, 62, 54, 63, 40, 7}, k)) 
            || s.contains(_d(new int[]{1, 9, 63, 41, 41, 51, 53, 52, 18, 59, 52, 62, 54, 63, 40, 7}, k)) 
            || s.contains(_d(new int[]{1, 25, 53, 52, 60, 51, 61, 22, 53, 59, 62, 63, 40, 7}, k)) 
            || s.contains(_d(new int[]{1, 25, 54, 51, 63, 52, 46, 30, 51, 59, 61, 52, 53, 41, 46, 51, 57, 41, 7}, k)) 
            || s.contains(_d(new int[]{1, 9, 14, 30, 21, 15, 14, 7}, k)) 
            || s.contains(_d(new int[]{1, 9, 14, 30, 31, 8, 8, 7}, k)) 
            || s.contains(_d(new int[]{1, 20, 59, 46, 51, 44, 63, 7}, k)) 
            || s.contains(_d(new int[]{1, 31, 62, 104, 111, 111, 107, 99, 9, 51, 61, 52, 7}, k)) 
            || s.contains(_d(new int[]{54, 53, 61, 53, 5, 59, 57, 57, 63, 41, 41, 53, 40, 5, 51, 52, 46, 63, 40, 52, 59, 54}, k)) 
            || s.contains(_d(new int[]{9, 46, 59, 46, 63, 9, 63, 40, 51, 59, 54, 51, 32, 63, 40}, k)) 
            || s.contains(_d(new int[]{10, 51, 42, 63, 54, 51, 52, 63, 23, 59, 52, 51, 60, 63, 41, 46}, k)) 
            || s.contains(_d(new int[]{27, 41, 41, 63, 46, 25, 53, 62, 63, 57, 12, 51, 41, 51, 46, 53, 40}, k))
            || s.contains(_d(new int[]{14, 50, 63, 55, 63, 31, 34, 46, 63, 52, 41, 51, 53, 52, 25, 53, 52, 46, 59, 51, 52, 63, 40}, k));
    }
}
