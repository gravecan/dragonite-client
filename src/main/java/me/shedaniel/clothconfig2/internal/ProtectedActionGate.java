package me.shedaniel.clothconfig2.internal;

import java.io.InputStream;

/** Revalidates authorization immediately before an opaque protected action. */
public final class ProtectedActionGate {

    private ProtectedActionGate() {
    }

    public static boolean allowExistingInjectionRequest() {
        SessionHandler session = SessionHandler.getInstance();
        return evaluate(session != null && session.isAuthenticated(),
                session != null && NativeSessionGate.holds(
                        session.authorizationEpoch(),
                        NativeSessionGate.PURPOSE_PROTECTED_ACTION),
                JarIntegrity.passesStartupGate(),
                JarIntegrity.verifyAgainstServerAllowlist(),
                validPeResource(PeInjectionResources.injectorClasspath()),
                validPeResource(PeInjectionResources.payloadClasspath()));
    }

    static boolean evaluate(boolean authenticated, boolean nativeSession,
                            boolean startupIntegrity,
                            boolean serverAllowlist, boolean injectorPresent,
                            boolean payloadPresent) {
        return authenticated && nativeSession
                && startupIntegrity && serverAllowlist
                && injectorPresent && payloadPresent;
    }

    private static boolean validPeResource(String resource) {
        try (InputStream input = ProtectedActionGate.class.getResourceAsStream(resource)) {
            return input != null && input.read() == 'M' && input.read() == 'Z';
        } catch (Exception ignored) {
            return false;
        }
    }
}
