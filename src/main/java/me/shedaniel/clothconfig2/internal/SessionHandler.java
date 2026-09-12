package me.shedaniel.clothconfig2.internal;

import com.mojang.authlib.GameProfile;
import me.shedaniel.clothconfig2.internal.secure.JavaWatchdog;

public class SessionHandler {

    private static final SessionHandler INSTANCE = new SessionHandler();

    private final NetworkHandler authClient = new NetworkHandler();
    private volatile boolean initialized = false;
    private volatile long authEpoch = 0L;
    private volatile String licenseType = null;
    private volatile String hwid = null;
    private Thread sessionValidatorThread;
    private com.mojang.authlib.GameProfile activeProfile;
    private String activeHwid;
    private volatile String standaloneFailureCode = "not_attempted";
    private volatile String standaloneFailureMessage = "HWID authorization has not completed";

    
    // The validator runs once per minute. This must span multiple validator
    // intervals or the first transient timeout defeats MAX_OFFLINE_CHECKS.
    private static final long MAX_OFFLINE_GRACE_MS = 150 * 1000;
    private long lastSuccessfulServerCheck = 0;
    private int consecutiveOfflineChecks = 0;
    private static final int MAX_OFFLINE_CHECKS = 2;

    private SessionHandler() {}

    public static SessionHandler getInstance() {
        return INSTANCE;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isAuthenticated() {
        return evaluateSessionLiveGate(
                authEpoch != 0L,
                SecurityVault.isArmed(),
                SecurityVault.invariantHolds(),
                SecurityVault.opaqueEnvironmentOk(),
                JavaWatchdog.isOk(),
                authClient.isValid(),
                NativeSessionGate.holds(
                        authEpoch, NativeSessionGate.PURPOSE_AUTH_LIVE));
    }

    /**
     * Primitive VM2 lattice for live session checks. Callers must pass fresh inputs;
     * patching only {@link #isAuthenticated()} entry is insufficient when this
     * method is committed as genuine VM2 in release builds.
     */
    static boolean evaluateSessionLiveGate(boolean epochActive, boolean vaultArmed,
                                           boolean invariantHolds, boolean opaqueEnvironmentOk,
                                           boolean watchdogOk, boolean networkValid,
                                           boolean nativeSessionLive) {
        if (!epochActive || !vaultArmed || !invariantHolds || !opaqueEnvironmentOk) {
            return false;
        }
        if (!watchdogOk || !networkValid || !nativeSessionLive) {
            return false;
        }
        return true;
    }

    boolean authEpochActive() {
        return authEpoch != 0L;
    }

    long authorizationEpoch() {
        return authEpoch;
    }

    public String getLicenseType() {
        return licenseType;
    }

    public String getSessionToken() {
        return authClient.getSessionToken();
    }

    public NetworkHandler getNetworkHandler() {
        return authClient;
    }

    public String standaloneAuthFailureMessage() {
        return standaloneFailureMessage + " [" + standaloneFailureCode + "]";
    }

    /**
     * Establishes the singleton authorization state for the standalone launcher.
     * A successful response from a throwaway NetworkHandler is not sufficient:
     * the protected-action gate reads this instance and its live server session.
     */
    public synchronized NetworkHandler.AuthResult authenticateStandalone(String licenseKey, String hwidLocal) {
        if (hwidLocal == null || hwidLocal.isBlank()) {
            return new NetworkHandler.AuthResult(false, "Failed to generate hardware ID");
        }
        if (!JavaWatchdog.isOk() || !EnvironmentGuard.scan()) {
            return new NetworkHandler.AuthResult(false, "Security environment check failed");
        }
        if (!JarIntegrity.passesStartupGate()) {
            return new NetworkHandler.AuthResult(false, "Client build integrity check failed");
        }

        NetworkHandler.AuthResult result = authenticateLicenseWithRetry(licenseKey, hwidLocal, null);
        if (!result.success) {
            return result;
        }
        if (!JarIntegrity.verifyAgainstServerAllowlist()) {
            authClient.invalidateSession();
            return new NetworkHandler.AuthResult(false, "Client build is not authorized");
        }

        activeProfile = null;
        activeHwid = hwidLocal;
        initialized = true;
        if (!grantAuth(hwidLocal, result.licenseType) || !isAuthenticated()) {
            initialized = false;
            revokeAuth();
            authClient.invalidateSession();
            return new NetworkHandler.AuthResult(false, "Authenticated session did not pass the protected-action gate");
        }
        startSessionValidator();
        return result;
    }

    /**
     * Same HWID-bound session path as the Fabric client when the server already
     * recognizes this machine (no license key required).
     */
    public synchronized boolean tryCompleteStandaloneHwidAuth() {
        if (!JavaWatchdog.isOk() || !EnvironmentGuard.scan()) {
            recordStandaloneFailure("environment_check", null);
            return false;
        }
        if (!JarIntegrity.passesStartupGate()) {
            recordStandaloneFailure("jar_integrity", null);
            return false;
        }
        String hwidLocal;
        try {
            hwidLocal = ConfigLoader.getHwid();
            if (hwidLocal == null || hwidLocal.isBlank()) {
                recordStandaloneFailure("hwid_unavailable", null);
                return false;
            }
        } catch (Throwable t) {
            recordStandaloneFailure("hwid_exception", null);
            return false;
        }
        try {
            AuthReachability.requireReachable();
            IntegrationHandler.requireConnected();
        } catch (Exception e) {
            recordStandaloneFailure("auth_preflight", null);
            return false;
        }

        NetworkHandler.HwidCheckResult hwidResult = checkHwidWithRetry(hwidLocal, null);
        if ("blacklisted".equals(hwidResult.reason)) {
            DialogHandler.clearSavedLicense();
            recordStandaloneFailure("blacklisted", hwidResult.message);
            return false;
        }
        if (!hwidResult.authenticated) {
            recordStandaloneFailure(hwidResult.reason, hwidResult.message);
            return false;
        }
        if (!JarIntegrity.verifyAgainstServerAllowlist()) {
            authClient.invalidateSession();
            recordStandaloneFailure("jar_allowlist", null);
            return false;
        }

        activeProfile = null;
        activeHwid = hwidLocal;
        initialized = true;
        if (!grantAuth(hwidLocal, hwidResult.licenseType) || !isAuthenticated()) {
            initialized = false;
            revokeAuth();
            authClient.invalidateSession();
            recordStandaloneFailure("session_gate", null);
            return false;
        }
        if (!ProtectedActionGate.allowExistingInjectionRequest()) {
            rejectStandaloneAuthorization();
            recordStandaloneFailure("protected_action_gate", null);
            return false;
        }
        startSessionValidator();
        recordStandaloneSuccess();
        return true;
    }

    private void recordStandaloneSuccess() {
        standaloneFailureCode = "none";
        standaloneFailureMessage = "Authorization succeeded";
        AuthorizationDiagnostics.recordStage("standalone_auth", "success");
    }

    private void recordStandaloneFailure(String reason, String serverMessage) {
        String code = AuthorizationDiagnostics.safeToken(reason);
        standaloneFailureCode = code;
        standaloneFailureMessage = safeStandaloneMessage(code, serverMessage);
        AuthorizationDiagnostics.recordStage("standalone_auth", code);
    }

    static String safeStandaloneMessage(String code, String serverMessage) {
        if (code != null && code.startsWith("machine_identity_does_not_match")) {
            return "Machine identity does not match this license";
        }
        return switch (code != null ? code : "unknown") {
            case "invalid_proof" -> "Server rejected the authentication proof";
            case "update_required", "jar_allowlist", "jar_integrity" ->
                    "This client build is not authorized by the server";
            case "hwid_not_found" -> "This machine is not bound to an active license";
            case "blacklisted" -> "This machine or license is blacklisted";
            case "too_many_requests" -> "Authentication rate limit reached; wait and retry";
            case "environment_check" -> "Local security environment check failed";
            case "auth_preflight" -> "Discord or authentication preflight failed";
            case "hwid_unavailable", "hwid_exception" -> "Hardware identity could not be generated";
            case "session_gate" -> "Authenticated session failed the live session gate";
            case "protected_action_gate" -> "Authenticated session failed the protected-action gate";
            default -> boundedServerMessage(serverMessage);
        };
    }

    private static String boundedServerMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Authorization was rejected";
        }
        String bounded = message.replace('\r', ' ').replace('\n', ' ').trim();
        return bounded.substring(0, Math.min(160, bounded.length()));
    }

    private boolean grantAuth(String hwid, String licenseType) {
        this.hwid = hwid;
        this.licenseType = licenseType;
        this.authEpoch = System.nanoTime() ^ mixEpoch(hwid);
        if (this.authEpoch == 0L) {
            this.authEpoch = 1L;
        }
        lastSuccessfulServerCheck = System.currentTimeMillis();
        consecutiveOfflineChecks = 0;
        onAuthSuccess(hwid);
        if (authEpoch == 0L || !NativeSessionGate.arm(
                authClient.getSessionToken(), hwid,
                authClient.getNativeCapabilityEvidence(), authEpoch)) {
            revokeAuth();
            authClient.invalidateSession();
            return false;
        }
        return true;
    }

    private static long mixEpoch(String hwid) {
        return hwid != null ? hwid.hashCode() * 0x9E3779B97F4A7C15L : 0L;
    }

    private void revokeAuth() {
        NativeSessionGate.disarm();
        authEpoch = 0L;
        SecurityVault.disarm();
    }

    synchronized void rejectStandaloneAuthorization() {
        initialized = false;
        revokeAuth();
        stopSessionValidator();
        authClient.invalidateSession();
    }

    private void onAuthSuccess(String hwid) {
        if (!JarIntegrity.verifyAgainstServerAllowlist()) {
            forceInvalidate("This client build is not authorized.");
            return;
        }
        refreshSecurityVault(hwid);
        GuardRuntime.resetFaults();
    }

    
    private void refreshSecurityVault(String hwidLocal) {
        String token = authClient.getSessionToken();
        if (token == null || token.isEmpty()) {
            return;
        }
        SecurityVault.arm(token, hwidLocal);
    }

    public void forceInvalidate(String reason) {
        revokeAuth();
        stopSessionValidator();
        authClient.invalidateSession();
        String msg = normalizeSessionMessage(reason, "Session invalid");
        reportFatalError("session_invalid", msg, null, hwid);
        DialogHandler.showError(msg);
        forceCrash();
    }

    private void reportFatalError(String errorCode, String userMessage, GameProfile mcProfile, String hwidLocal) {
        authClient.reportClientErrorBlocking(errorCode, userMessage, mcProfile, hwidLocal);
    }

    private void fatalErrorAndExit(String errorCode, String userMessage, GameProfile mcProfile, String hwidLocal) {
        mcProfile = McIdentityResolver.waitForResolvableProfile(mcProfile, 1500);
        userMessage = normalizeSessionMessage(userMessage, "An unexpected client error occurred.");
        ClientLog.err("SessionHandler", "auth blocked [" + errorCode + "]: " + userMessage);
        reportFatalError(errorCode, userMessage, mcProfile, hwidLocal);
        DialogHandler.showError(userMessage);
        System.exit(0);
    }

    public boolean init(GameProfile mcProfile) {
        initialized = true;
        mcProfile = McIdentityResolver.waitForResolvableProfile(mcProfile, 2500);
        this.activeProfile = mcProfile;
        ClientLog.err("SessionHandler", "init starting for " + (mcProfile != null ? mcProfile.getName() : "?"));

        HiddenGateBootstrap.initialize();
        
        // Start the pure Java watchdog
        JavaWatchdog.start();

        if (!JavaWatchdog.isOk()) {
            fatalErrorAndExit("security_poisoned",
                    "Security violation detected.\nPlease disable debugging tools.", mcProfile, null);
            return false;
        }

        if (!EnvironmentGuard.scan()) {
            fatalErrorAndExit("environment_blocked",
                    "Unsupported environment detected.", mcProfile, null);
            return false;
        }

        if (!JarIntegrity.passesStartupGate()) {
            fatalErrorAndExit("jar_integrity",
                    "Client build integrity check failed.\nPlease download an official release.", mcProfile, null);
            return false;
        }

        String hwidLocal;
        try {
            Class.forName(ConfigLoader.class.getName());
            Class.forName(NetworkHandler.class.getName());
        } catch (ClassNotFoundException e) {
            fatalErrorAndExit("class_integrity",
                    "Client integrity check failed.\nPlease reinstall ClothConfig.", mcProfile, null);
            return false;
        }

        try {
            hwidLocal = ConfigLoader.getHwid();
            this.activeHwid = hwidLocal;
            if (hwidLocal == null || hwidLocal.isEmpty()) {
                fatalErrorAndExit("hwid_generation_failed",
                        "Failed to generate hardware ID.\nPlease contact support.", mcProfile, hwidLocal);
                return false;
            }
        } catch (Throwable t) {
            System.err.println("[Dragonite Debug] native_hwid_error details:");
            t.printStackTrace();
            fatalErrorAndExit("native_hwid_error",
                    "Security module error.\nPlease reinstall ClothConfig.", mcProfile, null);
            return false;
        }

        try {
            AuthReachability.requireReachable();
            IntegrationHandler.requireConnected();
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Auth server unreachable";
            fatalErrorAndExit("auth_unreachable", msg, mcProfile, hwidLocal);
            return false;
        }

        NetworkHandler.HwidCheckResult hwidResult;
        try {
            hwidResult = checkHwidWithRetry(hwidLocal, mcProfile);
        } catch (Throwable t) {
            String msg = t.getMessage() != null ? t.getMessage() : "Connection failed";
            hwidResult = new NetworkHandler.HwidCheckResult(false, "server_error", msg);
        }

        if (hwidResult.authenticated) {
            if (!grantAuth(hwidLocal, hwidResult.licenseType)) {
                fatalErrorAndExit("native_session_gate",
                        "Native authorization module unavailable.\nPlease reinstall Dragonite.",
                        mcProfile, hwidLocal);
                return false;
            }
            startSessionValidator();
            NotificationHandler.notifyIfEnabled(
                    mcProfile != null ? mcProfile.getName() : "Unknown",
                    mcProfile != null ? mcProfile.getId().toString() : "Unknown",
                    hwidLocal,
                    licenseType);
            return true;
        }

        if ("server_error".equals(hwidResult.reason) || "error".equals(hwidResult.reason)) {
            String msg = hwidResult.message != null ? hwidResult.message : "Server error";
            fatalErrorAndExit("hwid_check_" + hwidResult.reason, msg, mcProfile, hwidLocal);
            return false;
        }

        if ("blacklisted".equals(hwidResult.reason)) {
            showBlacklistAndExit(hwidResult.message, mcProfile, hwidLocal);
            return false;
        }

        return promptLicenseAndAuth(mcProfile, hwidLocal);
    }

    private void showBlacklistAndExit(String message, GameProfile mcProfile, String hwidLocal) {
        String msg = message != null && !message.isBlank() ? message : AuthMessages.getBlacklistedMessage();
        fatalErrorAndExit("blacklisted", msg, mcProfile, hwidLocal);
    }

    private boolean promptLicenseAndAuth(GameProfile mcProfile, String hwidLocal) {
        boolean allowSavedShortcut = true;
        while (true) {
            String licenseKey = DialogHandler.showLicenseDialog(allowSavedShortcut);
            if (licenseKey == null) {
                System.err.println("[SessionHandler] License dialog closed by user. Exiting...");
                System.exit(0);
                return false;
            }

            NetworkHandler.AuthResult result = authenticateLicenseWithRetry(licenseKey, hwidLocal, mcProfile);
            if (result.blacklisted) {
                DialogHandler.clearSavedLicense();
                showBlacklistAndExit(result.message, mcProfile, hwidLocal);
                return false;
            }
            if (result.success) {
                if (!grantAuth(hwidLocal, result.licenseType)) {
                    fatalErrorAndExit("native_session_gate",
                            "Native authorization module unavailable.\nPlease reinstall Dragonite.",
                            mcProfile, hwidLocal);
                    return false;
                }
                DialogHandler.saveLicense(licenseKey);
                startSessionValidator();
                return true;
            }

            allowSavedShortcut = false;
            DialogHandler.clearSavedLicense();
            String err = normalizeSessionMessage(result.message, "Invalid license");
            DialogHandler.showError(err);
        }
    }

    public com.mojang.authlib.GameProfile getActiveProfile() {
        return activeProfile;
    }

    public String getActiveHwid() {
        return activeHwid;
    }

    private void startSessionValidator() {
        stopSessionValidator();
        sessionValidatorThread = new Thread(() -> {
            while (authEpochActive() && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(60000);
                    if (!authEpochActive()) {
                        break;
                    }
                    if (hwid != null) {
                        try {
                            AuthReachability.requireReachable();
                            IntegrationHandler.requireConnected();
                            
                            JavaWatchdog.checkRespawn();
                            
                            if (!authClient.isValid()) {
                                reportFatalError("session_verify_failed", "Session invalidated by server", null, hwid);
                                DialogHandler.showError("Session invalidated by server");
                                forceCrash();
                                return;
                              }
                            
                            lastSuccessfulServerCheck = System.currentTimeMillis();
                            consecutiveOfflineChecks = 0;
                            refreshSecurityVault(hwid);
                            GuardRuntime.resetFaults();
                            continue;
                        } catch (Exception e) {
                            String msg = e.getMessage() != null ? e.getMessage() : "";
                            if (isAuthBlockedMessage(msg)) {
                                forceInvalidate(msg);
                                return;
                            }
                            consecutiveOfflineChecks++;
                            long offlineTime = System.currentTimeMillis() - lastSuccessfulServerCheck;
                            if (shouldTerminateForOffline(consecutiveOfflineChecks, offlineTime)) {
                                reportFatalError("auth_offline",
                                        "Cannot verify license.\nAuth server required — offline play disabled.",
                                        null, hwid);
                                DialogHandler.showError("Cannot verify license.\nAuth server required — offline play disabled.");
                                forceCrash();
                                return;
                            }
                        }
                    }
                    if (!isAuthenticated()) {
                        DialogHandler.showError(sessionInvalidMessage());
                        forceCrash();
                        return;
                    }
                    if (!JavaWatchdog.isOk()) {
                        DialogHandler.showError("Security violation detected.\nPlease disable debugging tools.");
                        forceCrash();
                        return;
                    }
                    if (!EnvironmentGuard.scan()) {
                        forceInvalidate("Environment check failed");
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "Cloth-SessionValidator");
        sessionValidatorThread.setDaemon(true);
        sessionValidatorThread.start();
    }

    private NetworkHandler.HwidCheckResult checkHwidWithRetry(String hwidLocal, GameProfile mcProfile) {
        NetworkHandler.HwidCheckResult result = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            result = authClient.checkHwid(hwidLocal, mcProfile);
            if (result.authenticated || "blacklisted".equals(result.reason)
                    || !isTransientNetworkFailure(result.reason, result.message)) {
                return result;
            }
            retryBackoff(attempt);
        }
        return result != null
                ? result
                : new NetworkHandler.HwidCheckResult(false, "server_error", "Failed to connect to auth server");
    }

    private NetworkHandler.AuthResult authenticateLicenseWithRetry(
            String licenseKey, String hwidLocal, GameProfile mcProfile) {
        NetworkHandler.AuthResult result = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            result = authClient.authenticateWithLicense(licenseKey, hwidLocal, mcProfile);
            if (result.success || result.blacklisted
                    || !isTransientNetworkFailure("server_error", result.message)) {
                return result;
            }
            retryBackoff(attempt);
        }
        return result != null
                ? result
                : new NetworkHandler.AuthResult(false, "Failed to connect to auth server");
    }

    private static void retryBackoff(int attempt) {
        if (attempt >= 2) {
            return;
        }
        try {
            Thread.sleep(300L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    static boolean isTransientNetworkFailure(String reason, String message) {
        if (!"server_error".equals(reason) && !"error".equals(reason)) {
            return false;
        }
        String normalized = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("timed out")
                || normalized.contains("timeout")
                || normalized.contains("connection reset")
                || normalized.contains("temporarily unavailable")
                || normalized.contains("failed to connect")
                || normalized.contains("invalid challenge");
    }

    static boolean shouldTerminateForOffline(int consecutiveFailures, long offlineMillis) {
        return consecutiveFailures > MAX_OFFLINE_CHECKS || offlineMillis > MAX_OFFLINE_GRACE_MS;
    }

    private void stopSessionValidator() {
        if (sessionValidatorThread != null) {
            sessionValidatorThread.interrupt();
            sessionValidatorThread = null;
        }
    }

    private String sessionInvalidMessage() {
        if (authClient.getSessionToken() == null) {
            return "Session ended.\nPlease restart the client.";
        }
        long expiry = authClient.getSessionExpiryMillis();
        if (expiry > 0L && System.currentTimeMillis() >= expiry) {
            return "Your session has expired.\nPlease restart the client.";
        }
        if (!SecurityVault.isArmed() || !SecurityVault.invariantHolds()) {
            return "Security session was interrupted.\nPlease restart the client.";
        }
        return "License verification failed.\nCheck your connection and restart the client.";
    }

    private void forceCrash() {
        revokeAuth();
        try {
            Thread.sleep(500);
        } catch (Exception ignored) {
        }
        System.exit(0);
    }

    public void shutdown() {
        stopSessionValidator();
        if (authEpochActive()) {
            authClient.logout();
            revokeAuth();
        }
        KeyHalfFetcher.clear();
    }

    public static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> getInstance().shutdown(), "Cloth-Shutdown"));
    }

    private static boolean isAuthBlockedMessage(String msg) {
        String lower = msg.toLowerCase();
        return lower.contains("hosts")
                || lower.contains("blocked")
                || lower.contains("redirect")
                || lower.contains("sinkhole")
                || lower.contains("spki pin")
                || lower.contains("tls pin")
                || lower.contains("fake auth");
    }

    private static String normalizeSessionMessage(String message, String fallback) {
        if (message == null) {
            return fallback;
        }
        String stripped = message.strip();
        if (stripped.isEmpty()) {
            return fallback;
        }
        boolean hasRenderableGlyph = stripped.codePoints().anyMatch(cp ->
                !Character.isWhitespace(cp)
                        && !Character.isISOControl(cp)
                        && Character.getType(cp) != Character.FORMAT);
        return hasRenderableGlyph ? message : fallback;
    }
}
