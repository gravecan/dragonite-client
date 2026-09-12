package me.shedaniel.clothconfig2.internal;

/**
 * Keeps standalone-launcher authorization out of the Swing entry point.
 *
 * <p>The UI outcome is not itself an authorization grant. A successful
 * transition is accepted only while the singleton server session and the
 * independent protected-action gate are both still valid.</p>
 */
public final class StandaloneLicenseCoordinator {
    private static final int DENIED = 0x2B17C4;
    private static final int GRANTED = 0x6E49A1;
    private static final int LAUNCHER_SHELL = 0x41A0DE;
    private static final java.util.concurrent.atomic.AtomicBoolean HWID_DEFER_ATTEMPTED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private StandaloneLicenseCoordinator() {
    }

    public static AuthorizationOutcome authenticate(String licenseKey) {
        if (licenseKey == null || licenseKey.isBlank()) {
            return AuthorizationOutcome.denied("Please enter a key!", false, null);
        }

        String hwid = ConfigLoader.getHwid();
        if (hwid == null || hwid.isBlank()) {
            return AuthorizationOutcome.denied(
                    "Failed to generate hardware ID!", false, null);
        }

        try {
            AuthReachability.requireReachable();
        } catch (Exception unavailable) {
            return AuthorizationOutcome.denied(
                    "Authentication server is offline.", false, null);
        }

        SessionHandler session = SessionHandler.getInstance();
        NetworkHandler.AuthResult result =
                session.authenticateStandalone(licenseKey, hwid);
        if (!result.success) {
            String message = result.blacklisted
                    ? "License or machine is blacklisted!"
                    : result.message;
            if (result.blacklisted) {
                DialogHandler.clearSavedLicense();
            }
            if (message == null || message.isBlank()) {
                message = "Invalid license key";
            }
            return AuthorizationOutcome.denied(
                    message, result.blacklisted, result.attemptsRemaining);
        }

        if (!session.isAuthenticated()
                || !ProtectedActionGate.allowExistingInjectionRequest()) {
            session.rejectStandaloneAuthorization();
            return AuthorizationOutcome.denied(
                    "Authenticated session did not pass the protected-action gate",
                    false,
                    null);
        }
        DialogHandler.saveLicense(licenseKey);
        return AuthorizationOutcome.granted();
    }

    /** HWID-bound login (in-game parity) or interactive license with retry on failure. */
    public static AuthorizationOutcome authenticateForLauncher() {
        SessionHandler session = SessionHandler.getInstance();
        if (session.isAuthenticated()
                && ProtectedActionGate.allowExistingInjectionRequest()) {
            return AuthorizationOutcome.granted();
        }
        // Do not HWID-auth before Minecraft is running — session name/UUID are unknown and
        // jar-hash failures would spam Discord on injector open.
        return AuthorizationOutcome.launcherShell();
    }

    /**
     * Called when the injector sees a Minecraft process. Tries HWID auto-login once
     * so alerts include the in-game profile when possible.
     */
    public static boolean tryHwidAutoLoginWhenGameRunning() {
        if (!HWID_DEFER_ATTEMPTED.compareAndSet(false, true)) {
            return SessionHandler.getInstance().isAuthenticated();
        }
        SessionHandler session = SessionHandler.getInstance();
        if (session.isAuthenticated()) {
            return true;
        }
        McIdentityResolver.waitForResolvableProfile(null, 8000);
        return session.tryCompleteStandaloneHwidAuth();
    }

    /** License key path or a deferred HWID retry right before inject. */
    public static AuthorizationOutcome ensureAuthorizedForInject(String licenseKey) {
        SessionHandler session = SessionHandler.getInstance();
        if (session.isAuthenticated()
                && ProtectedActionGate.allowExistingInjectionRequest()) {
            return AuthorizationOutcome.granted();
        }
        McIdentityResolver.waitForResolvableProfile(null, 8000);
        if (session.tryCompleteStandaloneHwidAuth()
                && session.isAuthenticated()
                && ProtectedActionGate.allowExistingInjectionRequest()) {
            return AuthorizationOutcome.granted();
        }
        if (licenseKey == null || licenseKey.isBlank()) {
            return AuthorizationOutcome.denied(
                    session.standaloneAuthFailureMessage()
                            + " — diagnostic: " + AuthorizationDiagnostics.logPath(),
                    false,
                    null);
        }
        return authenticate(licenseKey);
    }

    /** Interactive license entry when HWID auto-login is not available at launcher start. */
    public static AuthorizationOutcome authenticateWithLicenseDialog() {
        boolean allowSavedShortcut = true;
        while (true) {
            String licenseKey = DialogHandler.showLicenseDialog(allowSavedShortcut);
            if (licenseKey == null || licenseKey.isBlank()) {
                return AuthorizationOutcome.denied("No license key provided", false, null);
            }
            AuthorizationOutcome outcome = authenticate(licenseKey);
            if (outcome.permitsUiTransition() || outcome.blacklisted()) {
                return outcome;
            }
            allowSavedShortcut = false;
            DialogHandler.clearSavedLicense();
            DialogHandler.showError(outcome.message() != null
                    ? outcome.message() : "Invalid license key");
        }
    }

    public static final class AuthorizationOutcome {
        private final int state;
        private final String message;
        private final boolean blacklisted;
        private final Integer attemptsRemaining;

        private AuthorizationOutcome(int state,
                                     String message,
                                     boolean blacklisted,
                                     Integer attemptsRemaining) {
            this.state = state;
            this.message = message;
            this.blacklisted = blacklisted;
            this.attemptsRemaining = attemptsRemaining;
        }

        private static AuthorizationOutcome launcherShell() {
            return new AuthorizationOutcome(LAUNCHER_SHELL, null, false, null);
        }

        private static AuthorizationOutcome granted() {
            return new AuthorizationOutcome(GRANTED, null, false, null);
        }

        private static AuthorizationOutcome denied(String message,
                                                   boolean blacklisted,
                                                   Integer attemptsRemaining) {
            return new AuthorizationOutcome(
                    DENIED, message, blacklisted, attemptsRemaining);
        }

        /**
         * Rechecks live authorization at the exact UI transition instead of
         * exposing the raw network response's success boolean to the GUI.
         */
        public boolean permitsUiTransition() {
            if (state == LAUNCHER_SHELL) {
                return true;
            }
            if (state != GRANTED || message != null || blacklisted) {
                return false;
            }
            SessionHandler session = SessionHandler.getInstance();
            return session.isAuthenticated()
                    && ProtectedActionGate.allowExistingInjectionRequest();
        }

        public String message() {
            return message;
        }

        public boolean blacklisted() {
            return blacklisted;
        }

        public Integer attemptsRemaining() {
            return attemptsRemaining;
        }
    }
}
