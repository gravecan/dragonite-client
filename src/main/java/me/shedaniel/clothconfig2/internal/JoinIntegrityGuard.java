package me.shedaniel.clothconfig2.internal;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;


public final class JoinIntegrityGuard {

    private static volatile boolean registered;

    private JoinIntegrityGuard() {}

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin(client));
    }

    static boolean joinPrearmGate(boolean envOk, boolean jarOk,
                                  boolean authOk, boolean hwidPresent) {
        return envOk && jarOk && authOk && hwidPresent;
    }

    private static void onJoin(MinecraftClient client) {
        try {
            AuthReachability.requireReachable();
            IntegrationHandler.requireConnected();
        } catch (Exception e) {
            SessionHandler.getInstance().forceInvalidate(e.getMessage());
            return;
        }
        SessionHandler sh = SessionHandler.getInstance();
        String hwid = ConfigLoader.getHwid();
        boolean envOk = EnvironmentGuard.scan();
        boolean jarOk = JarIntegrity.passesStartupGate();
        boolean authOk = sh.isAuthenticated();
        boolean hwidPresent = hwid != null && !hwid.isEmpty();
        if (!joinPrearmGate(envOk, jarOk, authOk, hwidPresent)) {
            if (!envOk) {
                sh.forceInvalidate("Environment check failed");
            } else if (!jarOk) {
                sh.forceInvalidate("Build integrity check failed");
            } else if (!authOk) {
                sh.forceInvalidate("Not authenticated at join");
            } else {
                sh.forceInvalidate("HWID unavailable");
            }
            return;
        }
        NetworkHandler handler = sh.getNetworkHandler();
        var session = client.getSession();
        var profile = new com.mojang.authlib.GameProfile(session.getUuidOrNull(), session.getUsername());
        NetworkHandler.HwidCheckResult result = handler.checkHwid(hwid, profile);
        if (result == null || !result.authenticated) {
            sh.forceInvalidate(result != null && result.message != null ? result.message : "Session rejected on join");
            return;
        }
        SecurityVault.arm(handler.getSessionToken(), hwid);
        GuardRuntime.resetFaults();
        CheckManager.check();
    }
}
