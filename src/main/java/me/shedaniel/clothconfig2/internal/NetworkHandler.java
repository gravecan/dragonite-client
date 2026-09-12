package me.shedaniel.clothconfig2.internal;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.mojang.authlib.GameProfile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.HttpsURLConnection;

public class NetworkHandler {

    private static final char[] HWID_AUTH_PROOF = {'_','_','H','W','I','D','_','A','U','T','H','_','_'};
    private static final Gson GSON = new Gson();

    private String sessionToken;
    private String licenseKey;
    private long sessionExpiry;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread heartbeatThread;
    private int heartbeatFailures;
    private long sessionExpiryMonotonicNanos;
    private boolean sessionHasExpiry;
    private volatile SignedSessionCapability pendingSignedCapability;
    private volatile SignedSessionCapability activeSignedCapability;
    
    public HwidCheckResult checkHwid(String hwid, GameProfile mcProfile) {
        ClientLog.out("NetworkHandler", "checkHwid()");
        mcProfile = McIdentityResolver.waitForResolvableProfile(mcProfile, 3500);
        try {
            AuthConfig.requireSecureInProduction();
            AuthReachability.requireReachable();
            if (!ConfigLoader.javaBackendReady()) {
                ClientLog.err("NetworkHandler", "ConfigLoader not loaded");
                return new HwidCheckResult(false, "hwid_not_found", "Security module not loaded");
            }
            
            String mcUsername = mcProfile != null ? mcProfile.getName() : "Unknown";
            String mcUuid = mcProfile != null ? mcProfile.getId().toString() : "Unknown";
            String publicIp = fetchPublicIp();
            
            IntegrationHandler.DiscordUser discordUser = IntegrationHandler.getUser();
            String discordId = discordUser != null ? discordUser.id : null;
            String discordUsername = discordUser != null ? discordUser.getFullUsername() : null;
            
            JsonObject request = new JsonObject();
            request.addProperty("hwid", hwid);
            request.addProperty("mcUsername", mcUsername);
            request.addProperty("mcUuid", mcUuid);
            request.addProperty("osVersion", System.getProperty("os.name") + " " + System.getProperty("os.version"));
            request.addProperty("ip", publicIp);
            if (discordId != null) request.addProperty("discordId", discordId);
            if (discordUsername != null) request.addProperty("discordUsername", discordUsername);
            String machineGuid = ConfigLoader.getMachineGuid();
            if (machineGuid != null && !machineGuid.isEmpty()) {
                request.addProperty("machineGuid", machineGuid);
            }
            request.addProperty("jarHash", ConfigLoader.resolveJarDigestHexForAuth());
            request.addProperty("windowsName", System.getProperty("user.name"));
            request.addProperty("pcName", getComputerName());
            addBuildTag(request);

            ChallengeResponse challenge = getChallenge();
            if (challenge == null) {
                return new HwidCheckResult(false, "server_error", "Invalid challenge from auth server");
            }
            String ts = String.valueOf(challenge.timestamp);
            request.addProperty("nonce", challenge.nonce);
            request.addProperty("timestamp", ts);
            String proof = ConfigLoader.signChallengeNative(
                    challenge.nonce, challenge.timestamp, hwid, new String(HWID_AUTH_PROOF));
            if (proof == null || proof.isBlank()) {
                AuthorizationDiagnostics.recordStage("proof_generation", "empty_proof");
                return new HwidCheckResult(false, "server_error", "Could not sign authentication challenge");
            }
            request.addProperty("proof", proof);
            byte[] diagnosticIkm = BuildFingerprint.nativeIkmOrNull();
            try {
                AuthorizationDiagnostics.recordProofAttempt(
                        ConfigLoader.buildChallengeProofPayload(
                                challenge.nonce, ts, hwid, new String(HWID_AUTH_PROOF)),
                        proof, ConfigLoader.resolveJarDigestHexForAuth(), hwid, diagnosticIkm);
            } finally {
                if (diagnosticIkm != null) {
                    java.util.Arrays.fill(diagnosticIkm, (byte) 0);
                }
            }
            
            HwidAuthResponse response = postHwidAuth(request);
            ClientLog.out("NetworkHandler", "hwid auth response: " + (response != null ? "ok" : "null"));
            
            if (response == null) {
                return new HwidCheckResult(false, "server_error", "Failed to connect to auth server");
            }
            
            if (response.authenticated) {
                if (!applySessionFromServer(response.sessionToken, response.expiresAt)) {
                    return new HwidCheckResult(false, "server_error", "Invalid session issued by auth server");
                }
                applyJarAllowlist(response.allowedJarHashes);
                if (!running.get()) {
                    startHeartbeat();
                }
                return new HwidCheckResult(true, null, null, response.sessionToken, response.expiresAt,
                        response.licenseType);
            }
            
            if (response.blacklisted || "blacklisted".equals(response.reason)) {
                String msg = response.message != null ? response.message : AuthMessages.getBlacklistedMessage();
                return new HwidCheckResult(false, "blacklisted", msg);
            }
            
            String failureCode = firstNonBlank(response.reason, response.error,
                    "authentication_rejected");
            String failureMessage = firstNonBlank(response.message, response.error,
                    "Authentication rejected");
            AuthorizationDiagnostics.recordStage("server_response", failureCode);
            return new HwidCheckResult(false, failureCode, failureMessage);
            
        } catch (Exception e) {
            AuthorizationDiagnostics.recordStage("hwid_request", e.getClass().getSimpleName());
            ClientLog.err("NetworkHandler", "checkHwid: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            String msg = e.getMessage() != null ? e.getMessage() : "Connection failed";
            return new HwidCheckResult(false, "server_error", msg);
        }
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }
    
    public AuthResult authenticateWithLicense(String licenseKey, String hwid, GameProfile mcProfile) {
        mcProfile = McIdentityResolver.waitForResolvableProfile(mcProfile, 3500);
        try {
            AuthConfig.requireSecureInProduction();
            AuthReachability.requireReachable();
            String licenseForAuth = LicenseKey.normalize(licenseKey);
            if (licenseForAuth == null) {
                return new AuthResult(false, "Invalid license key format");
            }
            String windowsName = System.getProperty("user.name");
            String pcName = getComputerName();
            String mcUsername = mcProfile != null ? mcProfile.getName() : "Unknown";
            String mcUuid = mcProfile != null ? mcProfile.getId().toString() : "Unknown";
            String osVersion = System.getProperty("os.name") + " " + System.getProperty("os.version");
            String publicIp = fetchPublicIp();
            
            IntegrationHandler.DiscordUser discordUser = IntegrationHandler.getUser();
            String discordId = discordUser != null ? discordUser.id : null;
            String discordUsername = discordUser != null ? discordUser.getFullUsername() : null;
            
            ChallengeResponse challenge = getChallenge();
            if (challenge == null) {
                return new AuthResult(false, "Failed to connect to auth server");
            }

            String ts = String.valueOf(challenge.timestamp);
            String jarHex = ConfigLoader.resolveJarDigestHexForAuth();
            if (jarHex == null) {
                jarHex = "";
            }
            JsonObject request = new JsonObject();
            request.addProperty("username", mcUsername);
            request.addProperty("hwid", hwid);
            request.addProperty("nonce", challenge.nonce);
            request.addProperty("timestamp", ts);
            request.addProperty("license", licenseForAuth);
            request.addProperty("windowsName", windowsName);
            request.addProperty("pcName", pcName);
            request.addProperty("mcUsername", mcUsername);
            request.addProperty("mcUuid", mcUuid);
            request.addProperty("osVersion", osVersion);
            request.addProperty("ip", publicIp);
            if (discordId != null) request.addProperty("discordId", discordId);
            if (discordUsername != null) request.addProperty("discordUsername", discordUsername);
            String machineGuid = ConfigLoader.getMachineGuid();
            if (machineGuid != null && !machineGuid.isEmpty()) {
                request.addProperty("machineGuid", machineGuid);
            }
            request.addProperty("jarHash", jarHex);
            addBuildTag(request);
            String proof = ConfigLoader.signChallengeNative(
                    challenge.nonce, challenge.timestamp, hwid, licenseForAuth);
            if (proof == null || proof.isBlank()) {
                return new AuthResult(false, "Could not sign authentication challenge");
            }
            request.addProperty("proof", proof);
            
            AuthResponse response = postLicenseAuth(request);
            
            if (response == null) {
                return new AuthResult(false, "Failed to connect to auth server");
            }

            if (!response.success) {
                String serverMsg = firstNonBlank(response.error, response.message);
                if (serverMsg != null && serverMsg.contains("Invalid client build")) {
                    ClientLog.err("NetworkHandler", "Add to VPS EXPECTED_JAR_HASHES: "
                            + ConfigLoader.resolveJarDigestHexForAuth());
                }
                ClientLog.err("NetworkHandler", "license auth failed: " + (serverMsg != null ? serverMsg : "(no message)")
                        + (response.blacklisted ? " [blacklisted]" : "")
                        + (response.attemptsRemaining != null ? " attemptsLeft=" + response.attemptsRemaining : ""));
            }

            if (response.blacklisted) {
                String msg = response.error != null ? response.error : AuthMessages.getBlacklistedMessage();
                return new AuthResult(false, msg, true);
            }
            
            if (!response.success) {
                String msg = firstNonBlank(response.error, response.message);
                if (msg == null) {
                    msg = "Authentication failed";
                }
                return new AuthResult(false, msg, false, response.attemptsRemaining);
            }

            if (!applySessionFromServer(response.sessionToken, response.expiresAt)) {
                return new AuthResult(false, "Invalid session issued by auth server");
            }
            this.licenseKey = licenseForAuth;
            applyJarAllowlist(response.allowedJarHashes);
            startHeartbeat();
            
            return new AuthResult(true, "Authenticated successfully", response.sessionToken, response.expiresAt,
                    response.licenseType);
            
        } catch (Exception e) {
            return new AuthResult(false, "Auth error: " + e.getMessage());
        }
    }

    private String fetchPublicIp() {
        try {
            URL url = new URL("https://checkip.amazonaws.com");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                return reader.readLine().trim();
            }
        } catch (Exception e) {
            return "0.0.0.0";
        }
    }

    public AuthResult authenticate(String licenseKey, GameProfile mcProfile) {
        try {
            if (!ConfigLoader.javaBackendReady()) {
                return new AuthResult(false, "Security module not loaded");
            }
            
            String hwid = ConfigLoader.getHwid();
            if (hwid == null || hwid.isEmpty()) {
                return new AuthResult(false, "Failed to get hardware ID");
            }
            
            String windowsName = System.getProperty("user.name");
            String pcName = getComputerName();
            
            String mcUsername = mcProfile != null ? mcProfile.getName() : "Unknown";
            String mcUuid = mcProfile != null ? mcProfile.getId().toString() : "Unknown";
            
            ChallengeResponse challenge = getChallenge();
            if (challenge == null) {
                return new AuthResult(false, "Failed to connect to auth server");
            }
            
            JsonObject request = new JsonObject();
            request.addProperty("username", mcUsername);
            request.addProperty("hwid", hwid);
            request.addProperty("nonce", challenge.nonce);
            request.addProperty("timestamp", challenge.timestamp);
            request.addProperty("license", licenseKey);
            request.addProperty("windowsName", windowsName);
            request.addProperty("pcName", pcName);
            request.addProperty("mcUsername", mcUsername);
            request.addProperty("mcUuid", mcUuid);
            request.addProperty("osVersion", System.getProperty("os.name") + " " + System.getProperty("os.version"));
            String machineGuid = ConfigLoader.getMachineGuid();
            if (machineGuid != null && !machineGuid.isEmpty()) {
                request.addProperty("machineGuid", machineGuid);
            }
            request.addProperty("jarHash", ConfigLoader.resolveJarDigestHexForAuth());
            addBuildTag(request);
            String proof = ConfigLoader.signChallengeNative(challenge.nonce, challenge.timestamp, hwid, licenseKey);
            if (proof == null || proof.isBlank()) {
                return new AuthResult(false, "Could not sign authentication challenge");
            }
            request.addProperty("proof", proof);

            AuthResponse response = post("/v1/auth", request, AuthResponse.class);
            
            if (response == null) {
                return new AuthResult(false, "Failed to connect to auth server");
            }
            
            if (!response.success) {
                return new AuthResult(false, response.error);
            }

            if (!applySessionFromServer(response.sessionToken, response.expiresAt)) {
                return new AuthResult(false, "Invalid session issued by auth server");
            }
            this.licenseKey = licenseKey;
            startHeartbeat();
            
            return new AuthResult(true, "Authenticated successfully", response.sessionToken, response.expiresAt,
                    response.licenseType);
            
        } catch (Exception e) {
            return new AuthResult(false, "Auth error: " + e.getMessage());
        }
    }
    
    public void logout() {
        if (sessionToken != null) {
            try {
                JsonObject request = new JsonObject();
                request.addProperty("sessionToken", sessionToken);
                post("/v1/logout", request, Void.class);
            } catch (Exception ignored) {
            }
        }
        
        stopHeartbeat();
        sessionToken = null;
        licenseKey = null;
        pendingSignedCapability = null;
        activeSignedCapability = null;
    }
    
    public boolean isValid() {
        if (sessionToken == null || sessionToken.isEmpty()) {
            return false;
        }
        return sessionHasExpiry
                && System.currentTimeMillis() < sessionExpiry
                && System.nanoTime() < sessionExpiryMonotonicNanos
                && getNativeCapabilityEvidence() != null;
    }

    public long getSessionExpiryMillis() {
        return sessionExpiry;
    }

    public void invalidateSession() {
        sessionToken = null;
        sessionExpiry = 0L;
        sessionExpiryMonotonicNanos = 0L;
        sessionHasExpiry = false;
        pendingSignedCapability = null;
        activeSignedCapability = null;
        stopHeartbeat();
    }
    
    public String getLicenseKey() {
        return licenseKey;
    }
    
    public String getSessionToken() {
        return sessionToken;
    }

    public boolean hasLiveSession() {
        return sessionToken != null && !sessionToken.isEmpty()
                && (!sessionHasExpiry || System.currentTimeMillis() < sessionExpiry);
    }

    /** Authenticated JSON POST helper for lightweight features such as shared configs. */
    public JsonObject postAuthed(String endpoint, JsonObject body) throws IOException {
        if (body == null) body = new JsonObject();
        if (!body.has("sessionToken") && sessionToken != null) {
            body.addProperty("sessionToken", sessionToken);
        }
        return post(endpoint, body, JsonObject.class);
    }

    String getNativeCapabilityEvidence() {
        SignedSessionCapability capability = activeSignedCapability;
        String currentJarHash = ConfigLoader.resolveJarDigestHexForAuth();
        return capability != null
                && capability.matches(sessionToken, sessionExpiry, currentJarHash)
                ? capability.material() : null;
    }

    public void reportSecurityEvent(String reason) {
        if (sessionToken == null || sessionToken.isEmpty() || reason == null || reason.isEmpty()) {
            return;
        }
        try {
            JsonObject request = new JsonObject();
            request.addProperty("sessionToken", sessionToken);
            request.addProperty("reason", reason);
            request.addProperty("errorCode", "security_report");
            request.addProperty("errorMessage", reason);
            try {
                net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
                if (mc != null && mc.getSession() != null) {
                    request.addProperty("mcUsername", mc.getSession().getUsername());
                }
            } catch (Throwable ignored) {
            }
            post("/v1/security-report", request, Void.class);
        } catch (Exception ignored) {
        }
    }
    
    public void reportClientError(String errorCode, String errorMessage, GameProfile mcProfile, String hwid) {
        if (errorCode == null || errorCode.isEmpty()) {
            return;
        }
        CompletableFuture.runAsync(() -> reportClientErrorBlocking(errorCode, errorMessage, mcProfile, hwid));
    }

    public void reportClientErrorBlocking(String errorCode, String errorMessage, GameProfile mcProfile, String hwid) {
        if (errorCode == null || errorCode.isEmpty()) {
            return;
        }
        if (sessionToken == null || sessionToken.isEmpty()) {
            ClientLog.err("NetworkHandler", "client-error retained locally before authentication: " + errorCode);
            return;
        }
        try {
            JsonObject request = buildClientErrorPayload(errorCode, errorMessage, mcProfile, hwid);
            String detail = nativeDiagnosticDetail();
            if (detail != null && !detail.isEmpty()) {
                request.addProperty("detail", detail);
            }
            CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            return postAuthJson("/v1/client-error", request, JsonObject.class);
                        } catch (Exception e) {
                            ClientLog.err("NetworkHandler", "client-error report failed: " + e.getMessage());
                            return null;
                        }
                    })
                    .get(8, TimeUnit.SECONDS);
        } catch (Exception e) {
            ClientLog.err("NetworkHandler", "client-error report timeout: " + e.getMessage());
        }
    }

    private static String nativeDiagnosticDetail() {
        return "nativeLoaded=false, pureJava=true";
    }

    private JsonObject buildClientErrorPayload(String errorCode, String errorMessage,
            GameProfile mcProfile, String hwid) {
        mcProfile = McIdentityResolver.resolve(mcProfile);
        mcProfile = McIdentityResolver.waitForResolvableProfile(mcProfile, 3500);
        JsonObject request = new JsonObject();
        request.addProperty("errorCode", errorCode);
        request.addProperty("errorMessage", errorMessage != null ? errorMessage : "");
        if (sessionToken != null && !sessionToken.isEmpty()) {
            request.addProperty("sessionToken", sessionToken);
        }
        String mcUsername = mcProfile != null ? mcProfile.getName() : "Unknown";
        String mcUuid = mcProfile != null ? mcProfile.getId().toString() : "Unknown";
        request.addProperty("mcUsername", mcUsername);
        request.addProperty("mcUuid", mcUuid);
        request.addProperty("osVersion", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        request.addProperty("windowsName", System.getProperty("user.name"));
        request.addProperty("pcName", getComputerName());
        try {
            String publicIp = fetchPublicIp();
            if (publicIp != null && !publicIp.isEmpty()) {
                request.addProperty("ip", publicIp);
            }
        } catch (Throwable ignored) {
        }
        IntegrationHandler.DiscordUser discordUser = IntegrationHandler.getUser();
        if (discordUser != null) {
            if (discordUser.id != null) {
                request.addProperty("discordId", discordUser.id);
            }
            request.addProperty("discordUsername", discordUser.getFullUsername());
        }
        if (hwid != null && !hwid.isEmpty()) {
            request.addProperty("hwid", hwid);
        }
        String machineGuid = ConfigLoader.getMachineGuid();
        if (machineGuid != null && !machineGuid.isEmpty()) {
            request.addProperty("machineGuid", machineGuid);
        }
        addBuildTag(request);
        return request;
    }
    
    private static void addBuildTag(JsonObject request) {
        String jarSha = ConfigLoader.resolveJarDigestHexForAuth();
        if (!jarSha.isEmpty()) {
            request.addProperty("jarHash", jarSha);
        }
    }

    private ChallengeResponse getChallenge() {
        try {
            URL url = new URL(AuthConfig.getAuthBaseUrl() + "/v1/challenge");
            HttpURLConnection conn = openAuthConnection(url);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            if (conn.getResponseCode() != 200) {
                return null;
            }
            
            try (InputStream is = conn.getInputStream()) {
                String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                ChallengeResponse challenge = GSON.fromJson(response, ChallengeResponse.class);
                return challenge != null && AuthSessionPolicy.validChallenge(challenge.nonce, challenge.timestamp)
                        ? challenge : null;
            }
        } catch (Exception e) {
            return null;
        }
    }
    
    private static HttpURLConnection openAuthConnection(URL url) throws IOException {
        try {
            HttpURLConnection conn = AuthTls.open(url);
            if (conn == null) {
                throw new IOException("Auth TLS setup failed");
            }
            return conn;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Auth TLS failed: " + e.getMessage(), e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private HwidAuthResponse postHwidAuth(JsonObject body) throws IOException {
        return postAuthJson("/v1/auth/hwid", body, HwidAuthResponse.class);
    }

    private AuthResponse postLicenseAuth(JsonObject body) throws IOException {
        return postAuthJson("/v1/auth", body, AuthResponse.class);
    }

    private void requireVerifiedServerResponse(HttpURLConnection conn, String endpoint, int responseCode,
                                               boolean granted, String sessionToken, String expiresAt,
                                               String requestNonce, String requestJarHash,
                                               String responseSignature) throws IOException {
        String signature = responseSignature;
        if (signature == null || signature.isBlank()) {
            signature = conn.getHeaderField("X-Dragonite-Response-Signature");
        }
        if (!ServerResponseVerifier.verify("POST", endpoint, responseCode, granted, sessionToken, expiresAt,
                requestNonce, requestJarHash, signature)) {
            throw new IOException("Authentication response signature verification failed");
        }
        if (granted && ("/v1/auth".equals(endpoint)
                || "/v1/auth/hwid".equals(endpoint))) {
            SignedSessionCapability capability = SignedSessionCapability.create(
                    signature, sessionToken, expiresAt, requestNonce, requestJarHash);
            if (capability == null) {
                throw new IOException(
                        "Authentication response capability is incomplete or expired");
            }
            pendingSignedCapability = capability;
        }
    }

    private <T> T postAuthJson(String endpoint, JsonObject body, Class<T> responseType) throws IOException {
        URL url = new URL(AuthConfig.getAuthBaseUrl() + endpoint);
        HttpURLConnection conn = openAuthConnection(url);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        String json = GSON.toJson(body);
        String requestNonce = body.has("nonce") && !body.get("nonce").isJsonNull()
                ? body.get("nonce").getAsString() : "";
        String requestJarHash = body.has("jarHash") && !body.get("jarHash").isJsonNull()
                ? body.get("jarHash").getAsString() : "";
        String signature = signRequestBody(json, body);
        if (signature != null) {
            conn.setRequestProperty("X-Cloth-Signature", signature);
        }

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        InputStream bodyStream = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String responseBody = "";
        if (bodyStream != null) {
            responseBody = new String(bodyStream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }

        if (responseBody.isEmpty()) {
            if (responseCode >= 400) {
                throw new IOException("HTTP " + responseCode);
            }
            return null;
        }

        try {
            T parsed = GSON.fromJson(responseBody, responseType);
            if (parsed != null) {
                if ((responseType == AuthResponse.class && ((AuthResponse) parsed).success)
                        || (responseType == HwidAuthResponse.class && ((HwidAuthResponse) parsed).authenticated)) {
                    if (parsed instanceof AuthResponse auth) {
                        requireVerifiedServerResponse(conn, endpoint, responseCode, true,
                                auth.sessionToken, auth.expiresAt, requestNonce, requestJarHash, auth.responseSignature);
                    } else if (parsed instanceof HwidAuthResponse hwid) {
                        requireVerifiedServerResponse(conn, endpoint, responseCode, true,
                                hwid.sessionToken, hwid.expiresAt, requestNonce, requestJarHash, hwid.responseSignature);
                    }
                }
                return parsed;
            }
        } catch (Exception ignored) {
        }

        if (responseCode >= 400) {
            if (responseType == HeartbeatResponse.class && !responseBody.isEmpty()) {
                return parseJsonResponse(responseBody, responseType);
            }
            throw new IOException(extractApiError(responseBody, responseCode));
        }
        return null;
    }

    private <T> T post(String endpoint, JsonObject body, Class<T> responseType) throws IOException {
        URL url = new URL(AuthConfig.getAuthBaseUrl() + endpoint);
        HttpURLConnection conn = openAuthConnection(url);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        
        String json = GSON.toJson(body);
        String requestNonce = body.has("nonce") && !body.get("nonce").isJsonNull()
                ? body.get("nonce").getAsString() : "";
        String requestJarHash = body.has("jarHash") && !body.get("jarHash").isJsonNull()
                ? body.get("jarHash").getAsString() : "";
        String signature = signRequestBody(json, body);
        if (signature != null) {
            conn.setRequestProperty("X-Cloth-Signature", signature);
        }

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
        
        int responseCode = conn.getResponseCode();
        InputStream bodyStream = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String responseBody = "";
        if (bodyStream != null) {
            responseBody = new String(bodyStream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }

        if (responseCode >= 400) {
            if (responseType == HeartbeatResponse.class && !responseBody.isEmpty()) {
                return parseJsonResponse(responseBody, responseType);
            }
            throw new IOException(extractApiError(responseBody, responseCode));
        }

        if (responseType == Void.class) {
            return null;
        }

        T parsed = parseJsonResponse(responseBody, responseType);
        if (responseType == HeartbeatResponse.class && ((HeartbeatResponse) parsed).ok) {
            HeartbeatResponse heartbeat = (HeartbeatResponse) parsed;
            requireVerifiedServerResponse(conn, endpoint, responseCode, true,
                    sessionTokenForResponse(body), heartbeat.expiresAt,
                    requestNonce, requestJarHash, heartbeat.responseSignature);
        }
        return parsed;
    }

    private static String sessionTokenForResponse(JsonObject requestBody) {
        if (requestBody != null && requestBody.has("sessionToken") && !requestBody.get("sessionToken").isJsonNull()) {
            return requestBody.get("sessionToken").getAsString();
        }
        return "";
    }

    private static <T> T parseJsonResponse(String body, Class<T> responseType) throws IOException {
        if (body == null || body.isEmpty()) {
            throw new IOException("Empty response from auth server");
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(body);
        } catch (Exception e) {
            if (body.length() <= 120 && !body.contains("<")) {
                throw new IOException(body);
            }
            throw new IOException("Invalid response from auth server (not JSON)");
        }
        if (!root.isJsonObject()) {
            if (root.isJsonPrimitive()) {
                throw new IOException(root.getAsString());
            }
            throw new IOException("Unexpected auth server response format");
        }
        return GSON.fromJson(root, responseType);
    }

    private static String extractApiError(String body, int responseCode) {
        if (body == null || body.isEmpty()) {
            if (responseCode == 502 || responseCode == 503) {
                return "Auth server is offline (HTTP " + responseCode + "). Try again in a minute.";
            }
            return "HTTP " + responseCode;
        }
        try {
            JsonElement root = JsonParser.parseString(body);
            if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("error") && !obj.get("error").isJsonNull()) {
                    return obj.get("error").getAsString();
                }
                if (obj.has("message") && !obj.get("message").isJsonNull()) {
                    return obj.get("message").getAsString();
                }
            }
            if (root.isJsonPrimitive()) {
                return root.getAsString();
            }
        } catch (Exception ignored) {
        }
        if (body.contains("Bad Gateway") || body.contains("nginx")) {
            return "Auth server is offline (HTTP " + responseCode + "). Check VPS: pm2 restart cloth-auth";
        }
        if (body.length() <= 200 && !body.contains("<")) {
            return body;
        }
        return "HTTP " + responseCode;
    }
    
    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatFailures = 0;
        running.set(true);
        
        heartbeatThread = new Thread(() -> {
            while (running.get() && sessionToken != null) {
                try {
                    Thread.sleep(60000); 
                    
                    if (!running.get()) break;
                    
                    JsonObject request = new JsonObject();
                    request.addProperty("sessionToken", sessionToken);
                    String hwid = ConfigLoader.getHwid();
                    if (hwid != null) {
                        request.addProperty("hwid", hwid);
                    }
                    long heartbeatSlot = System.currentTimeMillis() / 60_000L;
                    request.addProperty("heartbeatSlot", heartbeatSlot);
                    String hbProof = signHeartbeatProof(sessionToken, heartbeatSlot);
                    if (hbProof != null) {
                        request.addProperty("heartbeatProof", hbProof);
                    }
                    addBuildTag(request);

                    HeartbeatResponse response = post("/v1/heartbeat", request, HeartbeatResponse.class);
                    if (response == null || !response.ok) {
                        if (response != null && response.revoked) {
                            ClientLog.err("NetworkHandler", "heartbeat revoked by server: "
                                    + (response.error != null ? response.error : "Session revoked"));
                            invalidateSession();
                            return;
                        }
                        heartbeatFailures++;
                        String err = response != null && response.error != null ? response.error : "Heartbeat rejected";
                        ClientLog.err("NetworkHandler", "heartbeat failed (" + heartbeatFailures + "): " + err);
                        continue;
                    }
                    heartbeatFailures = 0;
                    if (!mergeSessionExpiry(response.expiresAt)) {
                        ClientLog.err("NetworkHandler", "heartbeat returned invalid session expiry");
                        invalidateSession();
                        return;
                    }
                    
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    heartbeatFailures++;
                    ClientLog.err("NetworkHandler", "heartbeat error (" + heartbeatFailures + "): "
                            + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
                }
            }
        }, "Cloth-Heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }
    
    private void stopHeartbeat() {
        running.set(false);
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
            heartbeatThread = null;
        }
    }
    
    private String getComputerName() {
        String name = System.getenv("COMPUTERNAME");
        if (name == null) {
            name = System.getenv("HOSTNAME");
        }
        if (name == null) {
            try {
                name = java.net.InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                name = "Unknown";
            }
        }
        return name;
    }
    
    private static String signHeartbeatProof(String sessionToken, long slot) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(sessionToken.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(String.valueOf(slot).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    private static void applyJarAllowlist(String[] allowedJarHashes) {
        JarIntegrity.setServerAllowedHashes(allowedJarHashes);
    }

    private String signRequestBody(String json, JsonObject body) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            byte[] keyMaterial;
            
            if (body.has("sessionToken") && !body.get("sessionToken").isJsonNull()) {
                String token = body.get("sessionToken").getAsString();
                if (token == null || token.isEmpty()) {
                    return null;
                }
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] tokenBytes = token.getBytes(StandardCharsets.UTF_8);
                byte[] saltBytes = BuildFingerprint.decryptToBytes("233b2d3e383031362b3a");
                byte[] concatBytes = new byte[tokenBytes.length + saltBytes.length];
                System.arraycopy(tokenBytes, 0, concatBytes, 0, tokenBytes.length);
                System.arraycopy(saltBytes, 0, concatBytes, tokenBytes.length, saltBytes.length);
                keyMaterial = digest.digest(concatBytes);
                for (int i = 0; i < concatBytes.length; i++) {
                    concatBytes[i] = 0;
                }
            } else {
                String hwid = body.has("hwid") ? body.get("hwid").getAsString() : ConfigLoader.getHwid();
                if (hwid != null && !hwid.isEmpty()) {
                    keyMaterial = hwid.getBytes(StandardCharsets.UTF_8);
                } else if (body.has("mcUuid")) {
                    String mcUuid = body.get("mcUuid").getAsString();
                    if (mcUuid == null || mcUuid.isEmpty()) {
                        return null;
                    }
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    String material;
                    if (body.has("machineGuid") && !body.get("machineGuid").isJsonNull()) {
                        String machineGuid = body.get("machineGuid").getAsString();
                        if (machineGuid != null && !machineGuid.isEmpty()) {
                            material = mcUuid + "|" + machineGuid.trim() + "|cloth-error";
                        } else {
                            material = mcUuid + "|cloth-error";
                        }
                    } else {
                        material = mcUuid + "|cloth-error";
                    }
                    keyMaterial = digest.digest(material.getBytes(StandardCharsets.UTF_8));
                } else {
                    return null;
                }
            }
            mac.init(new javax.crypto.spec.SecretKeySpec(keyMaterial, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(json.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private boolean applySessionFromServer(String token, String expiresAtIso) {
        long nowMillis = System.currentTimeMillis();
        long nowNanos = System.nanoTime();
        AuthSessionPolicy.Deadline deadline = AuthSessionPolicy.validate(token, expiresAtIso, nowMillis, nowNanos);
        SignedSessionCapability pending = pendingSignedCapability;
        invalidateSession();
        String currentJarHash = ConfigLoader.resolveJarDigestHexForAuth();
        if (deadline == null || pending == null
                || !pending.matches(token, deadline.wallClockMillis(), currentJarHash)) {
            return false;
        }
        this.sessionToken = token;
        this.sessionExpiry = deadline.wallClockMillis();
        this.sessionExpiryMonotonicNanos = deadline.monotonicNanos();
        this.sessionHasExpiry = true;
        this.activeSignedCapability = pending;
        return true;
    }

    private boolean mergeSessionExpiry(String expiresAtIso) {
        AuthSessionPolicy.Deadline deadline = AuthSessionPolicy.validate(
                sessionToken, expiresAtIso, System.currentTimeMillis(), System.nanoTime());
        if (deadline == null) {
            return false;
        }
        this.sessionExpiry = deadline.wallClockMillis();
        this.sessionExpiryMonotonicNanos = deadline.monotonicNanos();
        this.sessionHasExpiry = true;
        return true;
    }
    
    public static class ChallengeResponse {
        @SerializedName("nonce") public String nonce;
        @SerializedName("timestamp") public long timestamp;
        @SerializedName("expiresIn") public int expiresIn;
    }
    
    public static class AuthResponse {
        @SerializedName("success") public boolean success;
        @SerializedName("blacklisted") public boolean blacklisted;
        @SerializedName("error") public String error;
        @SerializedName("message") public String message;
        @SerializedName("attemptsRemaining") public Integer attemptsRemaining;
        @SerializedName("sessionToken") public String sessionToken;
        @SerializedName("expiresAt") public String expiresAt;
        @SerializedName("licenseType") public String licenseType;
        @SerializedName("gracePeriod") public int gracePeriod;
        @SerializedName("allowedJarHashes") public String[] allowedJarHashes;
        @SerializedName("updateRequired") public boolean updateRequired;
        @SerializedName("updateUrl") public String updateUrl;
        @SerializedName("requiredVersion") public String requiredVersion;
        @SerializedName("responseSignature") public String responseSignature;
    }
    
    public static class HeartbeatResponse {
        @SerializedName("ok") public boolean ok;
        @SerializedName("error") public String error;
        @SerializedName("expiresAt") public String expiresAt;
        @SerializedName("revoked") public boolean revoked;
        @SerializedName("updateRequired") public boolean updateRequired;
        @SerializedName("updateUrl") public String updateUrl;
        @SerializedName("requiredVersion") public String requiredVersion;
        @SerializedName("responseSignature") public String responseSignature;
    }
    
    public static class HwidAuthResponse {
        @SerializedName("authenticated") public boolean authenticated;
        @SerializedName("blacklisted") public boolean blacklisted;
        @SerializedName("reason") public String reason;
        @SerializedName("message") public String message;
        @SerializedName("error") public String error;
        @SerializedName("sessionToken") public String sessionToken;
        @SerializedName("expiresAt") public String expiresAt;
        @SerializedName("licenseType") public String licenseType;
        @SerializedName("gracePeriod") public int gracePeriod;
        @SerializedName("allowedJarHashes") public String[] allowedJarHashes;
        @SerializedName("updateRequired") public boolean updateRequired;
        @SerializedName("updateUrl") public String updateUrl;
        @SerializedName("requiredVersion") public String requiredVersion;
        @SerializedName("responseSignature") public String responseSignature;
    }
    
    public static class HwidCheckResult {
        public final boolean authenticated;
        public final String reason;
        public final String message;
        public String sessionToken;
        public String expiresAt;
        public String licenseType;
        public String[] allowedJarHashes;
        
        public HwidCheckResult(boolean authenticated, String reason, String message) {
            this.authenticated = authenticated;
            this.reason = reason;
            this.message = message;
        }
        
        public HwidCheckResult(boolean authenticated, String reason, String message,
                               String sessionToken, String expiresAt, String licenseType) {
            this(authenticated, reason, message, sessionToken, expiresAt, licenseType, null);
        }

        public HwidCheckResult(boolean authenticated, String reason, String message,
                               String sessionToken, String expiresAt, String licenseType,
                               String[] allowedJarHashes) {
            this.authenticated = authenticated;
            this.reason = reason;
            this.message = message;
            this.sessionToken = sessionToken;
            this.expiresAt = expiresAt;
            this.licenseType = licenseType;
            this.allowedJarHashes = allowedJarHashes;
        }
    }

    public static class AuthResult {
        public final boolean success;
        public final String message;
        public final boolean blacklisted;
        public final Integer attemptsRemaining;
        public String sessionToken;
        public String expiresAt;
        public String licenseType;
        public String[] allowedJarHashes;

        public AuthResult(boolean success, String message) {
            this.success = success;
            this.message = normalizeAuthMessage(message, "Authentication failed");
            this.blacklisted = false;
            this.attemptsRemaining = null;
        }

        public AuthResult(boolean success, String message, boolean blacklisted) {
            this.success = success;
            this.message = normalizeAuthMessage(message, blacklisted ? AuthMessages.getBlacklistedMessage() : "Authentication failed");
            this.blacklisted = blacklisted;
            this.attemptsRemaining = null;
        }

        public AuthResult(boolean success, String message, boolean blacklisted, Integer attemptsRemaining) {
            this.success = success;
            this.message = normalizeAuthMessage(message, blacklisted ? AuthMessages.getBlacklistedMessage() : "Authentication failed");
            this.blacklisted = blacklisted;
            this.attemptsRemaining = attemptsRemaining;
        }

        public AuthResult(boolean success, String message, String sessionToken, String expiresAt) {
            this(success, message, sessionToken, expiresAt, null, null);
        }

        public AuthResult(boolean success, String message, String sessionToken, String expiresAt,
                          String licenseType) {
            this(success, message, sessionToken, expiresAt, licenseType, null);
        }

        public AuthResult(boolean success, String message, String sessionToken, String expiresAt,
                          String licenseType, String[] allowedJarHashes) {
            this.success = success;
            this.message = normalizeAuthMessage(message, "Authenticated successfully");
            this.blacklisted = false;
            this.attemptsRemaining = null;
            this.sessionToken = sessionToken;
            this.expiresAt = expiresAt;
            this.licenseType = licenseType;
            this.allowedJarHashes = allowedJarHashes;
        }

        private static String normalizeAuthMessage(String message, String fallback) {
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
}
