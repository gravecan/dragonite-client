/**
 * Dragonite auth server.
 *
 * This intentionally keeps enforcement server-side. The protected client should
 * not need a long-lived shared secret to authenticate.
 */

// PM2 injects env on first start; without override, edits to .env are ignored on restart.
require('dotenv').config({ override: true });

const crypto = require('crypto');
const express = require('express');
const https = require('https');
const fs = require('fs');
const path = require('path');
const { ReleaseRegistry } = require('./release-registry');
const { authResponseSigningPayload } = require('./auth-response-signing');
const {
    sanitizeWebhookUserField,
    sanitizeWebhookContent,
    sensitiveFingerprint,
    AlertDeduplicator
} = require('./notification-security');
const { proofFormatAllowed } = require('./auth-proof-policy');
const {
    verifyLicenseBoundHmac2,
    verifyHwidNonceBoundHmac2,
    diagnoseLicenseBoundHmac2
} = require('./auth-proof-hmac');
const { sessionIsExpired, licenseIsExpired } = require('./session-security');
const { isReleaseProvisioningMode } = require('./release-provisioning');
const { authorizeKeyAccess } = require('./key-access-policy');

const LICENSES_FILE = path.join(__dirname, 'licenses.json');
const HWIDS_FILE    = path.join(__dirname, 'hwids.txt');
const KEY_HALVES_FILE = path.join(__dirname, 'key_halves.json');
const RELEASE_REGISTRY_FILE = path.join(__dirname, 'release_registry.json');
const MANUAL_JAR_HASHES_FILE = path.join(__dirname, 'manual_jar_hashes.json');
const EVENT_IDS_FILE = path.join(__dirname, 'event_ids.json');
const BLACKLIST_FILE = path.join(__dirname, 'blacklist.json');
const AUTH_FAILURES_FILE = path.join(__dirname, 'auth_failures.json');

let hwidsFileMtimeMs = 0;
const approvedHwidsCache = new Set();

function loadApprovedHwids() {
    try {
        if (!fs.existsSync(HWIDS_FILE)) {
            approvedHwidsCache.clear();
            hwidsFileMtimeMs = 0;
            return approvedHwidsCache;
        }
        const stat = fs.statSync(HWIDS_FILE);
        if (stat.mtimeMs <= hwidsFileMtimeMs) {
            return approvedHwidsCache;
        }
        const lines = fs.readFileSync(HWIDS_FILE, 'utf8').split(/\r?\n/);
        approvedHwidsCache.clear();
        for (const line of lines) {
            const trimmed = line.trim();
            if (!trimmed || trimmed.startsWith('#')) continue;
            approvedHwidsCache.add(trimmed.toLowerCase());
        }
        hwidsFileMtimeMs = stat.mtimeMs;
        return approvedHwidsCache;
    } catch (e) {
        console.error('[Auth] Failed to load hwids.txt:', e.message);
        return approvedHwidsCache;
    }
}

// Load licenses from disk on startup so they survive restarts.
function normalizeLicenseRecord(data) {
    if (!data || typeof data !== 'object') return data;
    if (!data.licenseType && data.type) {
        const t = String(data.type).toLowerCase();
        if (t.includes('premium')) data.licenseType = 'lifetime_premium';
        else if (t.includes('monthly')) data.licenseType = 'monthly';
        else if (t.includes('custom')) data.licenseType = 'custom';
        else data.licenseType = 'lifetime_normal';
    }
    if (data.hwidChanges == null) data.hwidChanges = 0;
    if (data.active == null) data.active = false;
    if (!Array.isArray(data.allowedJarHashes)) data.allowedJarHashes = [];
    if (!Array.isArray(data.pastIps)) data.pastIps = [];
    if (!data.stats || typeof data.stats !== 'object') data.stats = { hwidResets: 0, clears: 0 };
    if (!Array.isArray(data.licenseIdHistory)) data.licenseIdHistory = [];
    if (!Array.isArray(data.adminHistory)) data.adminHistory = [];
    
    // Initialize past history arrays
    if (!Array.isArray(data.pastHwids)) data.pastHwids = [];
    if (!Array.isArray(data.pastMcUsernames)) data.pastMcUsernames = [];
    if (!Array.isArray(data.pastMcUuids)) data.pastMcUuids = [];
    if (!Array.isArray(data.pastWindowsNames)) data.pastWindowsNames = [];
    if (!Array.isArray(data.pastPcNames)) data.pastPcNames = [];
    if (!Array.isArray(data.pastDiscordIds)) data.pastDiscordIds = [];
    if (!Array.isArray(data.pastDiscordUsernames)) data.pastDiscordUsernames = [];

    // Auto-fill owner fields for existing licenses
    if (data.hwid && !data.ownerHwid) {
        data.ownerHwid = data.hwid;
        data.ownerWindowsName = data.windowsName || null;
        data.ownerPcName = data.pcName || null;
        data.ownerMcUsername = data.mcUsername || null;
        data.ownerMcUuid = data.mcUuid || null;
        data.ownerDiscordId = data.discordId || null;
        data.ownerDiscordUsername = data.discordUsername || null;
        data.ownerIp = data.ip || data.lastIp || null;
    }
    return data;
}

let licensesFileMtimeMs = 0;

function loadLicensesFromDisk(clearExisting) {
    try {
        if (!fs.existsSync(LICENSES_FILE)) {
            if (clearExisting) licenses.clear();
            licensesFileMtimeMs = 0;
            return;
        }
        const stat = fs.statSync(LICENSES_FILE);
        if (!clearExisting && stat.mtimeMs <= licensesFileMtimeMs) {
            return;
        }
        const raw = fs.readFileSync(LICENSES_FILE, 'utf8');
        const parsed = JSON.parse(raw);
        if (clearExisting) {
            licenses.clear();
        }
        if (parsed && typeof parsed === 'object') {
            const currentKeys = new Set(Object.keys(parsed));
            for (const [k, v] of Object.entries(parsed)) {
                licenses.set(k, normalizeLicenseRecord(v));
            }
            if (!clearExisting) {
                for (const k of licenses.keys()) {
                    if (!currentKeys.has(k)) {
                        licenses.delete(k);
                    }
                }
            }
        }
        licensesFileMtimeMs = stat.mtimeMs;
    } catch (e) {
        console.error('[Auth] Failed to load licenses from disk:', e.message);
    }
}

function loadLicenses() {
    loadLicensesFromDisk(true);
}

/** Pick up keys created by the Discord bot without restarting auth-server */
function refreshLicensesIfFileChanged() {
    loadLicensesFromDisk(false);
}

function writeJsonAtomic(filePath, obj) {
    const tmp = `${filePath}.tmp`;
    fs.writeFileSync(tmp, JSON.stringify(obj, null, 2), 'utf8');
    fs.renameSync(tmp, filePath);
}

function saveLicenses() {
    try {
        const obj = {};
        for (const [k, v] of licenses) obj[k] = v;
        writeJsonAtomic(LICENSES_FILE, obj);
        licensesFileMtimeMs = fs.statSync(LICENSES_FILE).mtimeMs;
        return true;
    } catch (e) {
        console.error('[Auth] Failed to save licenses to disk:', e.message);
        return false;
    }
}

// Split-key system: Server stores half of each decryption key
// Key = HWID_half + Server_half (combined at runtime)
function loadKeyHalves() {
    try {
        if (fs.existsSync(KEY_HALVES_FILE)) {
            const raw = fs.readFileSync(KEY_HALVES_FILE, 'utf8');
            const parsed = JSON.parse(raw);
            if (parsed && typeof parsed === 'object') {
                for (const [k, v] of Object.entries(parsed)) {
                    keyHalves.set(k, v);
                }
            }
        }
    } catch (e) {
        console.error('[Auth] Failed to load key halves from disk:', e.message);
    }
}

function saveKeyHalves() {
    try {
        const obj = {};
        for (const [k, v] of keyHalves) obj[k] = v;
        writeJsonAtomic(KEY_HALVES_FILE, obj);
    } catch (e) {
        console.error('[Auth] Failed to save key halves to disk:', e.message);
    }
}

// Event ID tracking for honeytrap detection
function loadEventIds() {
    try {
        if (fs.existsSync(EVENT_IDS_FILE)) {
            const raw = fs.readFileSync(EVENT_IDS_FILE, 'utf8');
            const parsed = JSON.parse(raw);
            if (parsed && typeof parsed === 'object') {
                for (const [k, v] of Object.entries(parsed)) {
                    eventIds.set(k, v);
                }
            }
        }
    } catch (e) {
        console.error('[Auth] Failed to load event IDs:', e.message);
    }
}

function saveEventIds() {
    try {
        const obj = {};
        for (const [k, v] of eventIds) obj[k] = v;
        writeJsonAtomic(EVENT_IDS_FILE, obj);
    } catch (e) {
        console.error('[Auth] Failed to save event IDs:', e.message);
    }
}

let blacklistFileMtimeMs = 0;

// Blacklist for revoked licenses/HWIDs/IPs (shared with discord-bot via blacklist.json)
function loadBlacklistFromDisk(clearExisting) {
    try {
        if (!fs.existsSync(BLACKLIST_FILE)) {
            if (clearExisting) blacklist.clear();
            blacklistFileMtimeMs = 0;
            return;
        }
        const stat = fs.statSync(BLACKLIST_FILE);
        if (!clearExisting && stat.mtimeMs <= blacklistFileMtimeMs) {
            return;
        }
        const raw = fs.readFileSync(BLACKLIST_FILE, 'utf8');
        const parsed = JSON.parse(raw);
        blacklist.clear();
        if (parsed && typeof parsed === 'object') {
            for (const [k, v] of Object.entries(parsed)) {
                blacklist.set(k, v);
            }
        }
        blacklistFileMtimeMs = stat.mtimeMs;
    } catch (e) {
        console.error('[Auth] Failed to load blacklist:', e.message);
    }
}

function loadBlacklist() {
    loadBlacklistFromDisk(true);
}

/** Pick up /blacklist add|remove from the Discord bot without restarting auth-server */
function refreshBlacklistIfFileChanged() {
    loadBlacklistFromDisk(false);
}

function saveBlacklist() {
    try {
        const obj = {};
        for (const [k, v] of blacklist) obj[k] = v;
        writeJsonAtomic(BLACKLIST_FILE, obj);
    } catch (e) {
        console.error('[Auth] Failed to save blacklist:', e.message);
    }
}

const licenses = new Map(); // licenseKey -> { username, hwid, hwidChanges, expiresAt, active, allowedJarHashes[] }
const keyHalves = new Map(); // keyId -> { keyHalfB, createdAt, assignedTo } - Server's half of decryption keys
const registeredJarHashes = new Set(); // Finalized JARs registered with their split key in release_registry.json
const releaseRegistry = new ReleaseRegistry(RELEASE_REGISTRY_FILE);
const eventIds = new Map(); // eventId -> { hwid, license, ip, username, firstSeen, lastSeen }
const blacklist = new Map(); // license/hwid/ip -> { reason, createdAt }
const sessions = new Map(); // sessionToken -> { username, hwid, license, expiresAt, lastHeartbeat, jarHash }
const sessionStore = require('./session-store');
// A session token is bearer authority. Keeping it on disk means a read of the
// VPS data directory can remain useful until the session expires. Production
// defaults to in-memory sessions; a server restart deliberately requires a
// fresh login. Set PERSIST_SESSIONS=true only when that trade-off is required.
const PERSIST_SESSIONS = process.env.PERSIST_SESSIONS === 'true';

function putSession(token, session) {
    sessions.set(token, session);
    if (PERSIST_SESSIONS) sessionStore.upsert(token, session);
}

function dropSession(token) {
    sessions.delete(token);
    if (PERSIST_SESSIONS) sessionStore.remove(token);
}

if (PERSIST_SESSIONS) sessionStore.loadInto(sessions);

const nonces = new Map(); // nonce -> { timestamp, used }
const rateLimitBuckets = new Map(); // key -> { count, resetAt }
/** HWID hash -> { count, lastAt } — invalid license attempts before auto-blacklist */
const licenseFailureStrikes = new Map();

// Load persisted licenses immediately (before CONFIG so Map exists)
// saveLicenses is called after every write to licenses

const CONFIG = {
    PORT: Number(process.env.PORT || 8000),
    SERVER_SECRET: process.env.SERVER_SECRET || crypto.randomBytes(32).toString('hex'),
    ADMIN_KEY: process.env.ADMIN_KEY || null, // REQUIRED - no default for security
    RELEASE_REGISTRATION_TOKEN: process.env.RELEASE_REGISTRATION_TOKEN || null,
    SESSION_DURATION_HOURS: Number(process.env.SESSION_DURATION_HOURS || 24),
    HEARTBEAT_INTERVAL_MINUTES: Number(process.env.HEARTBEAT_INTERVAL_MINUTES || 10),
    GRACE_PERIOD_HOURS: Number(process.env.GRACE_PERIOD_HOURS || 48),
    MAX_HWID_CHANGES: Number(process.env.MAX_HWID_CHANGES || 1),
    DISCORD_WEBHOOK: process.env.DISCORD_WEBHOOK || '',
    DISCORD_CLIENT_ID: process.env.DISCORD_CLIENT_ID || '',
    DISCORD_CLIENT_SECRET: process.env.DISCORD_CLIENT_SECRET || '',
    DISCORD_REDIRECT_URI: process.env.DISCORD_REDIRECT_URI
        || `https://${process.env.DOMAIN || 'assets-delivery.site'}/v1/auth/discord/callback`,
    DOMAIN: process.env.DOMAIN || 'assets-delivery.site',
    EXPECTED_JAR_HASHES: new Set(
        (process.env.EXPECTED_JAR_HASHES || '')
            .split(',')
            .map((value) => value.trim())
            .filter(Boolean)
    ),
    RATE_LIMIT_WINDOW_MS: Number(process.env.RATE_LIMIT_WINDOW_MS || 60_000),
    CHALLENGE_RATE_LIMIT: Number(process.env.CHALLENGE_RATE_LIMIT || 60),
    AUTH_RATE_LIMIT: Number(process.env.AUTH_RATE_LIMIT || 20),
    VERIFY_RATE_LIMIT: Number(process.env.VERIFY_RATE_LIMIT || 120),
    HEARTBEAT_RATE_LIMIT: Number(process.env.HEARTBEAT_RATE_LIMIT || 90),
    KEY_RETRIEVAL_RATE_LIMIT: Number(process.env.KEY_RETRIEVAL_RATE_LIMIT || 30),
    MAX_LICENSE_FAILURES: Number(process.env.MAX_LICENSE_FAILURES || 5),
    LICENSE_FAILURE_WINDOW_MS: Number(process.env.LICENSE_FAILURE_WINDOW_MS || 15 * 60_000),
    APPEAL_DISCORD_URL: process.env.APPEAL_DISCORD_URL || 'https://discord.gg/dragoniteclient',
    SESSION_IP_BIND_MODE: (process.env.SESSION_IP_BIND_MODE || 'warn').toLowerCase(),
    ECDSA_PUBLIC_KEY_SPKI_B64: process.env.ECDSA_PUBLIC_KEY_SPKI_B64 || 'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEbqdASTEgw9sFCXsZmotW3qHNAYkaZd20VfcnxeAPmfXis/czHnhyMd5ckUl32FGG056gCmMdvezeJz+yjTfhyg==',
    AUTH_RESPONSE_ED25519_PRIVATE_PKCS8_B64: process.env.AUTH_RESPONSE_ED25519_PRIVATE_PKCS8_B64 || '',
    CLIENT_UPDATE_URL: process.env.CLIENT_UPDATE_URL || `https://${process.env.DOMAIN || 'assets-delivery.site'}`,
    CURRENT_RELEASE_VERSION: process.env.CURRENT_RELEASE_VERSION || ''
};

const oauthStates = new Map(); // state -> expiresAt ms

const IS_PRODUCTION = process.env.NODE_ENV === 'production' || process.env.DRAGONITE_PRODUCTION === 'true';
// A private key distributed in a client is not an authentication secret. The
// legacy client-proof path is therefore disabled by default. It may only be
// enabled temporarily for compatibility during a controlled migration.
const REQUIRE_AUTH_PROOF = IS_PRODUCTION || process.env.REQUIRE_AUTH_PROOF === 'true';
const ALLOW_LEGACY_AUTH_PROOF = !IS_PRODUCTION && process.env.ALLOW_LEGACY_AUTH_PROOF === 'true';
const NATIVE_IKM_HEX = (process.env.NATIVE_IKM_HEX || '').trim().toLowerCase();

if (IS_PRODUCTION) {
    if (process.env.REQUIRE_AUTH_PROOF === 'false' || process.env.ALLOW_UNVERIFIED_PROOFS === 'true') {
        console.error('[Auth] FATAL: REQUIRE_AUTH_PROOF=false and ALLOW_UNVERIFIED_PROOFS are forbidden in production');
        process.exit(1);
    }
    if (!/^[a-f0-9]{64}$/.test(NATIVE_IKM_HEX)) {
        console.error('[Auth] FATAL: NATIVE_IKM_HEX (64 hex chars, same as native/master_ikm.hex from the release build) is required in production');
        process.exit(1);
    }
}
/** Debug switch: skip auto-blacklist so failed logins don't permaban while fixing keys/build. */
const DISABLE_AUTO_BLACKLIST = process.env.DISABLE_AUTO_BLACKLIST === 'true';
// Event IDs are legacy telemetry. They are disabled until their checks are
// moved after proof verification and backed by a server-issued identifier.
const ENABLE_EVENT_ID_HONEYTRAP = process.env.ENABLE_EVENT_ID_HONEYTRAP === 'true';
/** License field in ECDSA payload for HWID auto-login (client does not know bound license key yet). */
const HWID_AUTH_PROOF_MARKER = '__HWID_AUTH__';

const app = express();
// Trust forwarding headers only when the TCP peer is a configured reverse proxy.
app.set('trust proxy', false);
const TRUSTED_PROXY_IPS = new Set(
    (process.env.TRUSTED_PROXY_IPS || '127.0.0.1,::1')
        .split(',')
        .map((value) => value.trim().replace(/^::ffff:/i, ''))
        .filter(Boolean)
);

function isLoopbackIp(ip) {
    if (!ip) return true;
    const n = ip.startsWith('::ffff:') ? ip.slice(7) : ip;
    return n === '127.0.0.1' || n === '::1' || n.startsWith('127.');
}

function requestCameDirectlyFromLoopback(req) {
    const peer = String(req.socket?.remoteAddress || '').trim().replace(/^::ffff:/i, '');
    return isLoopbackIp(peer);
}

function requireWebsiteSync(req, res) {
    const configured = String(process.env.WEBSITE_SYNC_TOKEN || '').trim();
    if (!configured) {
        res.status(503).json({ success: false, error: 'Website sync token is not configured' });
        return false;
    }
    if (!requestCameDirectlyFromLoopback(req)) {
        res.status(403).json({ success: false, error: 'Internal endpoint' });
        return false;
    }
    const supplied = resolveAdminKey(req);
    if (!timingSafeEqualString(String(supplied || ''), configured)) {
        res.status(401).json({ success: false, error: 'Unauthorized' });
        return false;
    }
    return true;
}

/** Real client IP behind nginx — not ::ffff:127.0.0.1 from local proxy. */
function requestCameFromTrustedProxy(req) {
    const peer = String(req.socket?.remoteAddress || '').trim().replace(/^::ffff:/i, '');
    return TRUSTED_PROXY_IPS.has(peer);
}

function clientIp(req) {
    let ip = String(req.socket?.remoteAddress || '').trim();
    if (ip.startsWith('::ffff:')) ip = ip.slice(7);

    const xff = requestCameFromTrustedProxy(req) ? req.headers['x-forwarded-for'] : null;
    if (xff) {
        const forwarded = String(xff).split(',')[0].trim();
        if (forwarded && !isLoopbackIp(forwarded)) {
            ip = forwarded.startsWith('::ffff:') ? forwarded.slice(7) : forwarded;
        }
    }
    const xri = requestCameFromTrustedProxy(req) ? req.headers['x-real-ip'] : null;
    if (xri && isLoopbackIp(ip)) {
        const real = String(xri).trim();
        if (real && !isLoopbackIp(real)) {
            ip = real.startsWith('::ffff:') ? real.slice(7) : real;
        }
    }
    return ip || 'unknown';
}

app.use(express.json({
    limit: '32kb',
    verify: (req, res, buf) => {
        if (buf && buf.length) {
            req.rawBody = buf.toString('utf8');
        }
    }
}));

function releaseProvisioningMode() {
    return isReleaseProvisioningMode({
        isProduction: IS_PRODUCTION,
        enforceJarHash: process.env.ENFORCE_JAR_HASH !== 'false',
        hasAllowedJarHash: jarHashEnforcementEnabled()
    });
}

// A fresh production deployment must be able to receive its first finalized
// release without briefly accepting arbitrary client JARs.  The release
// registrar remains available; every client-facing route fails closed until a
// hash is registered atomically with its split-key entry.
app.use((req, res, next) => {
    if (!releaseProvisioningMode()) return next();
    if (req.path === '/v1/release/register' || req.path === '/v1/release/verify' || req.path === '/v1/health') {
        return next();
    }
    return res.status(503).json({
        success: false,
        error: 'Release provisioning required before client authentication is available.',
        reason: 'release_provisioning_required'
    });
});

function timingSafeEqualString(a, b) {
    if (typeof a !== 'string' || typeof b !== 'string') return false;
    const ba = Buffer.from(a);
    const bb = Buffer.from(b);
    if (ba.length !== bb.length) return false;
    return crypto.timingSafeEqual(ba, bb);
}

function ecdsaSpkiB64Clean() {
    return String(CONFIG.ECDSA_PUBLIC_KEY_SPKI_B64 || '').replace(/\s+/g, '');
}

function ecdsaSpkiFingerprint() {
    const clean = ecdsaSpkiB64Clean();
    if (!clean) return 'missing';
    return crypto.createHash('sha256').update(clean, 'utf8').digest('hex').slice(0, 16);
}

function proofTimestampString(ts) {
    if (ts == null || ts === '') return '';
    if (typeof ts === 'string') return ts.trim();
    if (typeof ts === 'number' && Number.isFinite(ts)) return String(Math.trunc(ts));
    return String(ts);
}

function verifyEcdsaProof(payload, proofB64) {
    const spkiB64 = ecdsaSpkiB64Clean();
    if (!spkiB64 || !proofB64) return false;
    try {
        const key = crypto.createPublicKey({
            key: Buffer.from(spkiB64, 'base64'),
            format: 'der',
            type: 'spki'
        });
        if (key.asymmetricKeyType && key.asymmetricKeyType !== 'ec') {
            console.error('[Auth] ECDSA_PUBLIC_KEY_SPKI_B64 is not an EC key (type=%s)', key.asymmetricKeyType);
            return false;
        }
        return crypto.verify(
            'SHA256', 
            Buffer.from(payload, 'utf8'), 
            { key: key, dsaEncoding: 'ieee-p1363' }, 
            Buffer.from(proofB64, 'base64')
        );
    } catch (e) {
        if (process.env.AUTH_DEBUG_PROOF === 'true') {
            console.error('[Auth] verifyEcdsaProof error:', e.message);
        }
        return false;
    }
}

function authProofPayload(timestamp, hwid, license, jarHash) {
    const ts = proofTimestampString(timestamp);
    const jh = jarHash ? normalizeJarHash(jarHash) : '';
    return `${ts}|${hwid}|${license}|${jh || ''}`;
}

function tryVerifyAuthProofEcdsa(timestamp, hwid, licenseCandidates, jarCandidates, sig) {
    for (const lic of licenseCandidates) {
        for (const jh of jarCandidates) {
            const payload = authProofPayload(timestamp, hwid, lic, jh || null);
            if (verifyEcdsaProof(payload, sig)) {
                return { ok: true, payload };
            }
        }
    }
    if (jarHashEnforcementEnabled()) {
        for (const lic of licenseCandidates) {
            for (const h of CONFIG.EXPECTED_JAR_HASHES) {
                const payload = authProofPayload(timestamp, hwid, lic, h);
                if (verifyEcdsaProof(payload, sig)) {
                    return { ok: true, payload, jarFallback: h };
                }
            }
        }
    }
    return { ok: false };
}

function verifyAuthProof(nonce, timestamp, hwid, license, proof, jarHash) {
    if (!IS_PRODUCTION
            && (process.env.REQUIRE_AUTH_PROOF === 'false' || process.env.ALLOW_UNVERIFIED_PROOFS === 'true')) {
        if (!proof) return true;
    }
    if (!proof) return !REQUIRE_AUTH_PROOF;
    if (!nonce || !hwid || !license) return false;
    if (!proofFormatAllowed(proof, {
        production: IS_PRODUCTION,
        allowLegacy: ALLOW_LEGACY_AUTH_PROOF
    })) return false;

    const licenseCandidates = new Set();
    const rawLic = String(license);
    licenseCandidates.add(rawLic);
    const normLic = normalizeLicenseKey(rawLic);
    if (normLic) licenseCandidates.add(normLic);
    const resolvedKey = resolveLicenseKey(rawLic);
    if (resolvedKey) {
        licenseCandidates.add(resolvedKey);
        licenseCandidates.add(normalizeLicenseKey(resolvedKey));
    }

    const jarCandidates = new Set();
    const normJar = normalizeJarHash(jarHash);
    jarCandidates.add(normJar || '');
    jarCandidates.add(''); // client may omit jarHash field while payload ends with |

    if (typeof proof === 'string' && proof.startsWith('hmac2:')) {
        const sig = proof.slice('hmac2:'.length);
        const ikmHex = NATIVE_IKM_HEX;
        if (!ikmHex) return false;
        for (const lic of licenseCandidates) {
            for (const jh of jarCandidates) {
                const ts = proofTimestampString(timestamp);
                const payload = `${nonce}|${ts}|${hwid}|${lic}|${jh || ''}`;
                if (verifyLicenseBoundHmac2(payload, sig, ikmHex, lic)) {
                    return true;
                }
            }
        }
        return false;
    }

    if (typeof proof === 'string' && proof.startsWith('ecdsa2:')) {
        const sig = proof.slice('ecdsa2:'.length);
        for (const lic of licenseCandidates) {
            for (const jh of jarCandidates) {
                const ts = proofTimestampString(timestamp);
                const payload = `${nonce}|${ts}|${hwid}|${lic}|${jh || ''}`;
                if (verifyEcdsaProof(payload, sig)) {
                    return true;
                }
            }
        }
        return false;
    }

    if (typeof proof === 'string' && proof.startsWith('ecdsa:')) {
        const sig = proof.slice('ecdsa:'.length);
        return tryVerifyAuthProofEcdsa(timestamp, hwid, licenseCandidates, jarCandidates, sig).ok;
    }

    // Dual-accept window for old clients (so users can still log in while the update rolls out)
    if (typeof proof === 'string' && proof.startsWith('ed25519:')) {
        const sig = proof.slice('ed25519:'.length);
        if (CONFIG.ED25519_PUBLIC_KEY_SPKI_B64) {
             // Fallback logic for old ed25519 clients
             try {
                const key = crypto.createPublicKey({ key: Buffer.from(CONFIG.ED25519_PUBLIC_KEY_SPKI_B64, 'base64'), format: 'der', type: 'spki' });
                for (const lic of licenseCandidates) {
                    for (const jh of jarCandidates) {
                        const payload = authProofPayload(timestamp, hwid, lic, jh || null);
                        if (crypto.verify(null, Buffer.from(payload, 'utf8'), key, Buffer.from(sig, 'base64'))) return true;
                    }
                }
             } catch(e) {}
        }
    }

    // An HMAC keyed by the public nonce is reproducible by any caller,
    // so it proves nothing. Only accept it when proof enforcement is explicitly
    // disabled for dev/test. Production requires the release ECDSA proof.
    if (REQUIRE_AUTH_PROOF) return false;
    const payload = authProofPayload(timestamp, hwid, normLic || rawLic, jarHash);
    const expected = crypto.createHmac('sha256', nonce).update(payload).digest('base64');
    return timingSafeEqualString(proof, expected);
}

function resolveAdminKey(req) {
    const header = req.headers['authorization'];
    if (typeof header === 'string' && header.startsWith('Bearer ')) {
        return header.slice(7).trim();
    }
    return null;
}

function requireAdmin(req, res) {
    if (!CONFIG.ADMIN_KEY) {
        res.status(503).json({ success: false, error: 'Admin key not configured on server' });
        return null;
    }
    const clientKey = req.ip || req.headers['x-forwarded-for'] || 'unknown_ip';
    if (isRateLimited('admin_limit_' + clientKey, 10)) {
        res.status(429).json({ success: false, error: 'Too many admin requests. Rate limit exceeded.' });
        return null;
    }
    const key = resolveAdminKey(req) || req.body?.adminKey;
    if (!timingSafeEqualString(String(key || ''), String(CONFIG.ADMIN_KEY || ''))) {
        res.status(401).json({ success: false, error: 'Unauthorized' });
        return null;
    }
    return key;
}

/**
 * Release registration uses a dedicated token, never the wider admin key.
 * Keep it only in the release machine environment and the VPS .env.
 */
function requireReleaseRegistrar(req, res) {
    if (!CONFIG.RELEASE_REGISTRATION_TOKEN) {
        res.status(503).json({ success: false, error: 'Release registration is not configured on server' });
        return false;
    }
    const clientKey = req.ip || req.headers['x-forwarded-for'] || 'unknown_ip';
    if (isRateLimited('release_registration_' + clientKey, 10)) {
        res.status(429).json({ success: false, error: 'Too many release-registration requests' });
        return false;
    }
    const header = String(req.headers.authorization || '');
    const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
    if (!timingSafeEqualString(token, String(CONFIG.RELEASE_REGISTRATION_TOKEN))) {
        res.status(401).json({ success: false, error: 'Unauthorized' });
        return false;
    }
    return true;
}

function verifyRequestSignature(req, options = {}) {
    const sig = req.headers['x-cloth-signature'] || req.headers['x-dragonite-signature'];
    const requireSig = IS_PRODUCTION || process.env.REQUIRE_REQUEST_SIGNATURE === 'true';
    if (!sig) {
        return !requireSig;
    }
    const body = req.body || {};
    const requireSession = options.requireSession === true;
    let keyMaterial;
    if (body.sessionToken) {
        keyMaterial = crypto.createHash('sha256').update(`${body.sessionToken}|dragonite`).digest();
    } else if (requireSession) {
        return false;
    } else if (options.allowErrorReportKey) {
        keyMaterial = errorReportSigningKey(body);
        if (!keyMaterial) return false;
    } else if (body.hwid) {
        keyMaterial = Buffer.from(String(body.hwid), 'utf8');
    } else {
        return false;
    }
    // Must match the exact bytes the client signed (Gson), not re-serialized JSON
    const raw = (typeof req.rawBody === 'string' && req.rawBody.length > 0)
        ? req.rawBody
        : JSON.stringify(body);
    const expected = crypto.createHmac('sha256', keyMaterial).update(raw).digest('base64');
    return timingSafeEqualString(sig, expected);
}

function sessionStillAuthorized(session, reqIp) {
    if (!session || sessionIsExpired(session)) return false;
    const licenseEntry = getLicenseEntry(session.license);
    const licenseData = licenseEntry?.data;
    if (!licenseData || licenseIsExpired(licenseData)) return false;
    if (session.ip && reqIp && session.ip !== 'unknown' && reqIp !== 'unknown' && session.ip !== reqIp) {
        if (CONFIG.SESSION_IP_BIND_MODE === 'strict') {
            return false;
        }
        if (CONFIG.SESSION_IP_BIND_MODE === 'warn') {
            console.warn('[Auth] Session IP drift for', session.mcUsername, session.ip, '->', reqIp);
        }
    }
    if (isSubjectBlacklisted({
        licenseKey: session.license,
        hwid: session.hwid,
        ip: reqIp || session.ip,
        mcUsername: session.mcUsername,
        mcUuid: session.mcUuid,
        discordId: session.discordId,
        discordUsername: session.discordUsername
    })) {
        return false;
    }
    return true;
}

function revokeSessionsForLicense(licenseKey) {
    for (const [token, session] of sessions) {
        if (session.license === licenseKey) {
            dropSession(token);
        }
    }
}

function revokeSessionsForBlacklistEntry(key) {
    if (!key) return;
    const norm = typeof key === 'string' ? key.toLowerCase() : key;
    for (const [token, session] of sessions) {
        if (session.license === key
            || session.hwid === key
            || session.hwid === normalizeHWID(key)
            || (session.mcUsername && session.mcUsername.toLowerCase() === norm)
            || session.mcUuid === key) {
            dropSession(token);
        }
    }
}

function blacklistPut(key, entry) {
    if (!key) return;
    blacklist.set(key, entry);
    revokeSessionsForBlacklistEntry(key);
}

function blacklistAppealMessage() {
    return `You have been blacklisted.\n\nIf you believe this was a false blacklist, create an appeal on our Discord:\n${CONFIG.APPEAL_DISCORD_URL}`;
}

function blacklistedResponse(res, statusCode = 403) {
    return res.status(statusCode).json({
        authenticated: false,
        success: false,
        blacklisted: true,
        reason: 'blacklisted',
        error: blacklistAppealMessage(),
        message: blacklistAppealMessage()
    });
}

function loadLicenseFailureStrikes() {
    try {
        if (!fs.existsSync(AUTH_FAILURES_FILE)) return;
        const parsed = JSON.parse(fs.readFileSync(AUTH_FAILURES_FILE, 'utf8'));
        if (parsed && typeof parsed === 'object') {
            licenseFailureStrikes.clear();
            for (const [k, v] of Object.entries(parsed)) {
                licenseFailureStrikes.set(k, v);
            }
        }
    } catch (e) {
        console.error('[Auth] Failed to load auth_failures.json:', e.message);
    }
}

function saveLicenseFailureStrikes() {
    try {
        const obj = {};
        for (const [k, v] of licenseFailureStrikes) obj[k] = v;
        fs.writeFileSync(AUTH_FAILURES_FILE, JSON.stringify(obj, null, 2), 'utf8');
    } catch (e) {
        console.error('[Auth] Failed to save auth_failures.json:', e.message);
    }
}

function clearLicenseFailureStrikes(normalizedHwid) {
    if (!normalizedHwid) return;
    if (licenseFailureStrikes.delete(normalizedHwid)) {
        saveLicenseFailureStrikes();
    }
}

/**
 * Build identifiers for auto-blacklist from a login/crack context.
 */
function buildBlacklistCtx({ normalizedHwid, rawHwid, ip, mcUsername, mcUuid, discordId, discordUsername, licenseKey }) {
    const norm = normalizedHwid || (rawHwid ? normalizeHWID(rawHwid) : null);
    const ipNorm = ip ? String(ip).replace(/^::ffff:/i, '') : null;
    return {
        normalizedHwid: norm,
        rawHwid: rawHwid || null,
        ip: ipNorm,
        mcUsername: mcUsername || null,
        mcUuid: mcUuid || null,
        discordId: discordId || null,
        discordUsername: discordUsername || null,
        licenseKey: licenseKey || null,
        licenseKeyNorm: licenseKey ? normalizeLicenseKey(licenseKey) : null
    };
}

/**
 * Permanently blacklist a subject (HWID, IP, MC, Discord, license) and revoke sessions.
 */
function autoBlacklistSubject(ctx, reason) {
    if (!ctx) return;
    if (DISABLE_AUTO_BLACKLIST) {
        console.log('[Auth] Auto-blacklist SKIPPED (DISABLE_AUTO_BLACKLIST=true) —', reason);
        return;
    }
    const createdAt = new Date().toISOString();
    const banGroup = crypto.randomUUID();
    const entry = { reason, auto: true, createdAt, banGroup };
    const keys = new Set();

    if (ctx.normalizedHwid) keys.add(ctx.normalizedHwid);
    if (ctx.ip && ctx.ip !== 'unknown') keys.add(ctx.ip);
    if (ctx.mcUsername) keys.add(String(ctx.mcUsername).toLowerCase());
    if (ctx.mcUuid) keys.add(ctx.mcUuid);
    if (ctx.discordId) keys.add(ctx.discordId);
    if (ctx.discordUsername) keys.add(String(ctx.discordUsername).toLowerCase());
    if (ctx.licenseKey) keys.add(ctx.licenseKey);
    if (ctx.licenseKeyNorm) keys.add(ctx.licenseKeyNorm);

    for (const key of keys) {
        blacklistPut(key, { ...entry, target: key });
    }
    if (ctx.normalizedHwid) {
        clearLicenseFailureStrikes(ctx.normalizedHwid);
    }
    saveBlacklist();
    console.log('[Auth] Auto-blacklisted:', [...keys].join(', '), '—', reason);
}

/** Both parties when JAR / event-id sharing is detected (separate banGroup per party). */
function autoBlacklistJarSharing({ originalLicense, newLicense }) {
    // Event IDs arrive before proof verification and are client-controlled.
    // Keep them as an investigative signal; never ban either side from this
    // unauthenticated signal alone.
    console.warn('[Auth] Suspected JAR sharing (not blacklisted):', originalLicense || 'unknown', '->', newLicense || 'unknown');
    return;
    console.log('[Auth] Auto-blacklisted JAR sharing — original + leaker');
}

/**
 * Invalid license attempts per HWID are a short-lived throttle, never a
 * permanent blacklist. A typo or a configuration rollout must not ban a user.
 * @returns {{ blacklisted: boolean, temporarilyBlocked: boolean, remaining: number, count: number }}
 */
function recordLicenseFailure(ctx) {
    const hwid = ctx.normalizedHwid;
    if (!hwid) {
        return { blacklisted: false, temporarilyBlocked: false, remaining: CONFIG.MAX_LICENSE_FAILURES, count: 0 };
    }
    if (isSubjectBlacklisted({
        hwid, ip: ctx.ip, mcUsername: ctx.mcUsername, mcUuid: ctx.mcUuid,
        discordId: ctx.discordId, discordUsername: ctx.discordUsername,
        licenseKey: ctx.licenseKey
    })) {
        return { blacklisted: true, temporarilyBlocked: false, remaining: 0, count: CONFIG.MAX_LICENSE_FAILURES };
    }

    const now = Date.now();
    const row = licenseFailureStrikes.get(hwid) || { count: 0, lastAt: null };
    const lastAtMs = Date.parse(row.lastAt || '');
    if (!Number.isFinite(lastAtMs) || now - lastAtMs > CONFIG.LICENSE_FAILURE_WINDOW_MS) {
        row.count = 0;
    }
    row.count += 1;
    row.lastAt = new Date().toISOString();
    licenseFailureStrikes.set(hwid, row);
    saveLicenseFailureStrikes();

    const remaining = Math.max(0, CONFIG.MAX_LICENSE_FAILURES - row.count);
    if (row.count >= CONFIG.MAX_LICENSE_FAILURES) {
        return { blacklisted: false, temporarilyBlocked: true, remaining: 0, count: row.count };
    }
    return { blacklisted: false, temporarilyBlocked: false, remaining, count: row.count };
}

/** Crack detected — instant auto-blacklist + Discord @everyone alert */
function punishCrack(title, webhookCtx, { autoBlacklist = false } = {}) {
    const ip = webhookCtx.req ? clientIp(webhookCtx.req) : null;
    if (autoBlacklist) autoBlacklistSubject(buildBlacklistCtx({
        normalizedHwid: webhookCtx.normalizedHwid,
        rawHwid: webhookCtx.rawHwid,
        ip,
        mcUsername: webhookCtx.mcUsername,
        mcUuid: webhookCtx.mcUuid,
        discordId: webhookCtx.discordId,
        discordUsername: webhookCtx.discordUsername,
        licenseKey: webhookCtx.licenseKey
    }), title);
    else console.warn('[Auth] Authentication anomaly (not blacklisted):', title, 'from', ip || 'unknown');

    // A failed proof can be caused by a mismatched deployment key or build.
    // Keep it visible for investigation, but do not call it a crack attempt
    // and do not ping everyone unless a future caller explicitly blacklists.
    if (!autoBlacklist) {
        const diagnosticTitle = String(title).replace('CRACK ATTEMPT', 'AUTHENTICATION REJECTED');
        sendDiscordAlert(formatCrackWebhook(diagnosticTitle, webhookCtx));
        return;
    }
    sendCrackAlert(formatCrackWebhook(title, webhookCtx));
}

let authResponseSigningKey = null;
function getAuthResponseSigningKey() {
    if (authResponseSigningKey) return authResponseSigningKey;
    const encoded = String(CONFIG.AUTH_RESPONSE_ED25519_PRIVATE_PKCS8_B64 || '').trim();
    if (!encoded) return null;
    try {
        authResponseSigningKey = crypto.createPrivateKey({
            key: Buffer.from(encoded, 'base64'),
            format: 'der',
            type: 'pkcs8'
        });
        return authResponseSigningKey;
    } catch (error) {
        console.error('[Auth] Invalid AUTH_RESPONSE_ED25519_PRIVATE_PKCS8_B64:', error.message);
        return null;
    }
}

function sendSignedAuthJson(req, res, statusCode, payload) {
    const key = getAuthResponseSigningKey();
    if (!key) {
        return res.status(503).json({ success: false, authenticated: false, error: 'Auth response signing unavailable' });
    }
    const signed = authResponseSigningPayload(req, statusCode, payload);
    const signature = crypto.sign(null, Buffer.from(signed, 'utf8'), key).toString('base64');
    const body = JSON.stringify({ ...payload, responseSignature: signature });
    res.status(statusCode);
    res.set('Content-Type', 'application/json; charset=utf-8');
    // Retain the header as a diagnostic/compatibility copy, but the client
    // verifies the body field above.
    res.set('X-Dragonite-Response-Signature', signature);
    return res.send(body);
}

/** After punishCrack — do not tell the client they are blacklisted when testing with DISABLE_AUTO_BLACKLIST. */
function crackDeniedResponse(res, statusCode = 401, detail) {
    // A rejected proof is not proof that the person is malicious. Returning a
    // blacklisted response here used to make a key/configuration error look
    // like a permanent ban and caused the client to terminate immediately.
    const generic = 'Authentication rejected (security check failed).';
    const hint = detail
        || 'ECDSA proof or build mismatch. Sync VPS ECDSA_PUBLIC_KEY_SPKI_B64 with the release build.';
    const error = hint === generic ? generic : `${generic} ${hint}`;
    return res.status(statusCode).json({
        success: false,
        authenticated: false,
        blacklisted: false,
        error
    });
}

app.get('/v1/health', (req, res) => {
    const alerts = !!resolveDiscordWebhook();
    const payload = {
        ok: true,
        service: 'cloth-auth',
        alerts
    };
    if (!IS_PRODUCTION) {
        payload.licenses = licenses.size;
        payload.sessions = sessions.size;
    }
    res.json(payload);
});

function generateNonce() {
    const nonce = crypto.randomBytes(32).toString('hex');
    const timestamp = Date.now();
    nonces.set(nonce, { timestamp, used: false });

    for (const [value, data] of nonces) {
        if (Date.now() - data.timestamp > 5 * 60 * 1000) {
            nonces.delete(value);
        }
    }

    return { nonce, timestamp };
}

function generateSessionToken() {
    return crypto.randomBytes(64).toString('hex');
}

function isValidHWID(hwid) {
    // HWID from C++ HWID.cpp is a dash-separated string of hex segments:
    // e.g. "a1b2c3d4-DESKTOP-USER-756e4974656c0306a9-3bfebfbf-0a1b2c3d"
    // We just need it to be a non-empty string of reasonable length.
    return typeof hwid === 'string' && hwid.length >= 8 && hwid.length <= 256;
}

// Normalize HWID to a consistent 64-char SHA-256 hex string for storage.
// This means the raw HWID format doesn't matter — only the hash is compared.
function normalizeHWID(hwid) {
    return crypto.createHash('sha256').update(hwid).digest('hex');
}

const HWID_HEX_RE = /^[a-f0-9]{64}$/i;

/** True if stored value is already a normalized SHA-256 hex digest. */
function isNormalizedHwidDigest(value) {
    return typeof value === 'string' && HWID_HEX_RE.test(value);
}

/**
 * Match license HWID against client raw HWID.
 * Supports legacy licenses.json rows that stored the raw string instead of the hash.
 */
function licenseHwidMatches(storedHwid, rawClientHwid) {
    if (!storedHwid || !rawClientHwid) return false;
    const normalized = normalizeHWID(rawClientHwid);
    if (storedHwid === normalized) return true;
    if (storedHwid === rawClientHwid) return true;
    if (!isNormalizedHwidDigest(storedHwid)) {
        return normalizeHWID(storedHwid) === normalized;
    }
    return false;
}

/** Persist licenses using canonical 64-char HWID hashes. */
function canonicalizeLicenseHwid(licenseData, rawClientHwid) {
    if (!licenseData || !rawClientHwid) return;
    const normalized = normalizeHWID(rawClientHwid);
    if (!licenseData.hwid || licenseData.hwid !== normalized) {
        licenseData.hwid = normalized;
    }
}


/** Client GUI uses 20 alphanumeric chars as XXXX-XXXX-XXXX-XXXX-XXXX */
function normalizeLicenseKey(key) {
    if (!key) return '';
    return String(key).replace(/[^A-Za-z0-9]/g, '').toUpperCase();
}

function formatLicenseKeyDisplay(key) {
    const raw = normalizeLicenseKey(key);
    if (raw.length !== 20) return key;
    return `${raw.slice(0, 4)}-${raw.slice(4, 8)}-${raw.slice(8, 12)}-${raw.slice(12, 16)}-${raw.slice(16, 20)}`;
}

function resolveLicenseKey(input) {
    if (!input) return null;
    const trimmed = String(input).trim();
    if (licenses.has(trimmed)) return trimmed;
    const norm = normalizeLicenseKey(trimmed);
    if (!norm) return null;
    for (const [stored] of licenses) {
        if (normalizeLicenseKey(stored) === norm) return stored;
    }
    return null;
}

function getLicenseEntry(input) {
    const key = resolveLicenseKey(input);
    if (!key) return null;
    return { key, data: licenses.get(key) };
}

function recordIpHistory(licenseData, ip) {
    if (!licenseData || !ip || ip === 'unknown') return;
    if (!Array.isArray(licenseData.pastIps)) licenseData.pastIps = [];
    licenseData.pastIps = licenseData.pastIps.filter((x) => x && x !== ip);
    licenseData.pastIps.unshift(ip);
    if (licenseData.pastIps.length > 25) {
        licenseData.pastIps = licenseData.pastIps.slice(0, 25);
    }
    licenseData.lastIp = ip;
    licenseData.ip = ip;
}

function formatIpBlock(licenseData, currentIp) {
    const last = currentIp || licenseData?.lastIp || 'unknown';
    let block = `IP (last): ${last}`;
    const past = (licenseData?.pastIps || []).filter((x) => x && x !== 'unknown' && x !== last);
    if (past.length > 0) {
        block += '\nPast IPs:';
        for (const p of past) {
            block += `\n• ${p}`;
        }
    }
    return block;
}

function formatLicenseWebhookLine(licenseKey) {
    if (!licenseKey) {
        return 'License: unknown';
    }
    const entry = getLicenseEntry(licenseKey);
    const key = entry?.key || licenseKey;
    return `License: ${formatLicenseKeyDisplay(key)}`;
}

function formatHwidWebhookLine(normalizedHwid, rawHwid, licenseData) {
    const stored = String(licenseData?.hwid || '').trim();
    const live = String(normalizedHwid || rawHwid || '').trim();
    if (stored) {
        return `Stored HWID SHA-256: ${stored}`;
    }
    if (!live) {
        return 'HWID: unknown';
    }
    return `HWID: ${live}`;
}

function resolveWebhookIdentity(ctx) {
    const body = ctx.req?.body || {};
    const pick = (value) => {
        if (value == null) return null;
        const s = String(value).trim();
        if (!s || s.toLowerCase() === 'unknown') return null;
        return s;
    };
    const first = (...candidates) => {
        for (const value of candidates) {
            const chosen = pick(value);
            if (chosen) return chosen;
        }
        return 'Unknown';
    };
    return {
        mcUsername: first(
            ctx.mcUsername, body.mcUsername, ctx.licenseData?.mcUsername, ctx.session?.mcUsername),
        mcUuid: first(ctx.mcUuid, body.mcUuid, ctx.licenseData?.mcUuid, ctx.session?.mcUuid)
    };
}

function formatLoginWebhook(title, ctx) {
    const {
        licenseKey, licenseData, rawHwid, normalizedHwid, req,
        mcUsername, mcUuid, osVersion, discordUsername, discordId,
        windowsName, pcName
    } = ctx;
    const identity = resolveWebhookIdentity(ctx);
    const ipNow = req ? clientIp(req) : (licenseData?.lastIp || 'unknown');
    return [
        title,
        `MC: ${sanitizeWebhookUserField(identity.mcUsername)}`,
        `UUID: ${sanitizeWebhookUserField(identity.mcUuid)}`,
        `Discord: ${sanitizeWebhookUserField(discordUsername || licenseData?.discordUsername || 'N/A')}${(discordId || licenseData?.discordId) ? ` (${sanitizeWebhookUserField(discordId || licenseData.discordId)})` : ''}`,
        formatLicenseWebhookLine(licenseKey),
        formatHwidWebhookLine(normalizedHwid, rawHwid, licenseData),
        `OS: ${sanitizeWebhookUserField(osVersion || licenseData?.osVersion || 'Unknown')}`,
        `Windows: ${sanitizeWebhookUserField(windowsName || licenseData?.windowsName || 'Unknown')}`,
        `PC: ${sanitizeWebhookUserField(pcName || licenseData?.pcName || 'Unknown')}`,
        formatIpBlock(licenseData, ipNow)
    ].filter(Boolean).join('\n');
}

function formatLogoutWebhook(session, licenseData, durationSeconds) {
    return [
        '👋 SESSION ENDED (bye)',
        `MC: ${sanitizeWebhookUserField(session.mcUsername || 'Unknown')}`,
        `UUID: ${sanitizeWebhookUserField(session.mcUuid || 'Unknown')}`,
        `Discord: ${sanitizeWebhookUserField(session.discordUsername || 'N/A')}${session.discordId ? ` (${sanitizeWebhookUserField(session.discordId)})` : ''}`,
        formatLicenseWebhookLine(session.license),
        formatHwidWebhookLine(session.hwid, session.rawHwid, licenseData),
        `OS: ${sanitizeWebhookUserField(session.osVersion || licenseData?.osVersion || 'Unknown')}`,
        `Windows: ${sanitizeWebhookUserField(session.windowsName || licenseData?.windowsName || 'Unknown')}`,
        `PC: ${sanitizeWebhookUserField(session.pcName || licenseData?.pcName || 'Unknown')}`,
        formatIpBlock(licenseData, session.ip),
        `Duration: ${durationSeconds}s`
    ].filter(Boolean).join('\n');
}

function formatCrackWebhook(title, ctx) {
    const base = formatLoginWebhook(title, ctx);
    if (ctx.extra) return `${base}\n${ctx.extra}`;
    return base;
}

/** Client / session errors — ping channel so you see failures immediately */
function formatErrorWebhook(title, ctx) {
    const base = formatLoginWebhook(title, ctx);
    const lines = [
        base,
        `Error code: ${sanitizeWebhookUserField(ctx.errorCode || 'unknown')}`,
        `Error: ${sanitizeWebhookUserField(ctx.errorMessage || 'n/a')}`
    ];
    if (ctx.clientReportedIp) {
        lines.push(`Client-reported IP: ${sanitizeWebhookUserField(ctx.clientReportedIp)}`);
    }
    if (ctx.detail) {
        lines.push(`Detail: ${sanitizeWebhookUserField(ctx.detail)}`);
    }
    if (ctx.jarHash) {
        lines.push(`jarHash: ${sanitizeWebhookUserField(ctx.jarHash)}`);
    }
    return lines.filter(Boolean).join('\n');
}

function errorReportSigningKey(body) {
    if (!body || typeof body !== 'object') return null;
    if (body.hwid) {
        return Buffer.from(String(body.hwid), 'utf8');
    }
    const mcUuid = body.mcUuid ? String(body.mcUuid).trim() : '';
    const machineGuid = body.machineGuid ? String(body.machineGuid).trim() : '';
    if (mcUuid && machineGuid) {
        return crypto.createHash('sha256').update(`${mcUuid}|${machineGuid}|dragonite-error`).digest();
    }
    if (mcUuid) {
        return crypto.createHash('sha256').update(`${mcUuid}|dragonite-error`).digest();
    }
    return null;
}

function normalizeMachineGuid(raw) {
    if (typeof raw !== 'string') return '';
    return raw.toLowerCase().trim();
}

/**
 * Bind or verify Windows MachineGuid on a license. Mismatch = likely JAR/license sharing.
 * @returns {{ ok: boolean, bound?: boolean }}
 */
function enforceMachineGuidBinding(licenseData, machineGuid) {
    if (!licenseData) return { ok: true };
    const submitted = normalizeMachineGuid(machineGuid);
    if (!submitted) return { ok: true };
    const stored = normalizeMachineGuid(licenseData.machineGuid);
    if (!stored) {
        licenseData.machineGuid = String(machineGuid).trim();
        return { ok: true, bound: true };
    }
    if (stored !== submitted) {
        return { ok: false };
    }
    return { ok: true };
}

function applyLoginTelemetry(licenseData, req, fields) {
    if (!licenseData) return;
    licenseData.lastLogin = new Date().toISOString();
    recordIpHistory(licenseData, clientIp(req));
    licenseData.active = true;
    licenseData.loginCount = (licenseData.loginCount || 0) + 1;
    
    // Push changing telemetry to history
    if (fields.mcUsername) {
        if (licenseData.mcUsername && licenseData.mcUsername !== fields.mcUsername && !licenseData.pastMcUsernames.includes(licenseData.mcUsername)) {
            licenseData.pastMcUsernames.push(licenseData.mcUsername);
        }
        licenseData.mcUsername = fields.mcUsername;
    }
    if (fields.mcUuid) {
        if (licenseData.mcUuid && licenseData.mcUuid !== fields.mcUuid && !licenseData.pastMcUuids.includes(licenseData.mcUuid)) {
            licenseData.pastMcUuids.push(licenseData.mcUuid);
        }
        licenseData.mcUuid = fields.mcUuid;
    }
    if (fields.discordId) {
        if (licenseData.discordId && licenseData.discordId !== fields.discordId && !licenseData.pastDiscordIds.includes(licenseData.discordId)) {
            licenseData.pastDiscordIds.push(licenseData.discordId);
        }
        licenseData.discordId = fields.discordId;
    }
    if (fields.discordUsername) {
        if (licenseData.discordUsername && licenseData.discordUsername !== fields.discordUsername && !licenseData.pastDiscordUsernames.includes(licenseData.discordUsername)) {
            licenseData.pastDiscordUsernames.push(licenseData.discordUsername);
        }
        licenseData.discordUsername = fields.discordUsername;
    }
    if (fields.windowsName) {
        if (licenseData.windowsName && licenseData.windowsName !== fields.windowsName && !licenseData.pastWindowsNames.includes(licenseData.windowsName)) {
            licenseData.pastWindowsNames.push(licenseData.windowsName);
        }
        licenseData.windowsName = fields.windowsName;
    }
    if (fields.pcName) {
        if (licenseData.pcName && licenseData.pcName !== fields.pcName && !licenseData.pastPcNames.includes(licenseData.pcName)) {
            licenseData.pastPcNames.push(licenseData.pcName);
        }
        licenseData.pcName = fields.pcName;
    }
    if (fields.osVersion) licenseData.osVersion = fields.osVersion;
    if (fields.machineGuid) licenseData.machineGuid = fields.machineGuid;
}

function isSubjectBlacklisted({ hwid, ip, mcUsername, mcUuid, discordId, discordUsername, licenseKey }) {
    const ipNorm = ip ? String(ip).replace(/^::ffff:/i, '') : null;
    const checks = [
        hwid, ip, ipNorm, mcUsername, mcUuid, discordId, discordUsername, licenseKey,
        licenseKey ? normalizeLicenseKey(licenseKey) : null,
        mcUsername ? String(mcUsername).toLowerCase() : null
    ].filter(Boolean);
    for (const value of checks) {
        if (blacklist.has(value)) return true;
        if (typeof value === 'string' && blacklist.has(value.toLowerCase())) return true;
    }
    return false;
}

function resolveDiscordWebhook() {
    const raw = (process.env.DISCORD_WEBHOOK || CONFIG.DISCORD_WEBHOOK || '').trim().replace(/^["']|["']$/g, '');
    if (!raw || /your_webhook/i.test(raw) || !raw.startsWith('https://discord.com/api/webhooks/')) {
        return null;
    }
    return raw;
}

function discordWebhookId(webhookUrl) {
    try {
        const parts = new URL(webhookUrl).pathname.split('/').filter(Boolean);
        const i = parts.indexOf('webhooks');
        return i >= 0 && parts[i + 1] ? parts[i + 1] : '?';
    } catch {
        return '?';
    }
}

let cachedWebhookChannelId = null;
const alertDeduplicator = new AlertDeduplicator(Number(process.env.DISCORD_ALERT_DEDUPE_MS || 30_000));

function parseWebhookIdToken(webhookUrl) {
    try {
        const parts = new URL(webhookUrl).pathname.split('/').filter(Boolean);
        const i = parts.indexOf('webhooks');
        if (i < 0 || !parts[i + 1] || !parts[i + 2]) return null;
        return { id: parts[i + 1], token: parts[i + 2] };
    } catch {
        return null;
    }
}

async function resolveAlertChannelId() {
    const fromEnv = (process.env.DISCORD_ALERT_CHANNEL_ID || '').trim().replace(/^["']|["']$/g, '');
    if (fromEnv) return fromEnv;
    if (cachedWebhookChannelId) return cachedWebhookChannelId;

    const webhook = resolveDiscordWebhook();
    const parsed = webhook ? parseWebhookIdToken(webhook) : null;
    if (!parsed) return null;

    try {
        const res = await fetch(`https://discord.com/api/v10/webhooks/${parsed.id}/${parsed.token}`);
        if (!res.ok) return null;
        const data = await res.json();
        if (data && data.channel_id) {
            cachedWebhookChannelId = String(data.channel_id);
            return cachedWebhookChannelId;
        }
    } catch (e) {
        console.error('[Webhook] Could not resolve webhook channel:', e.message);
    }
    return null;
}

function postDiscordWebhookPayload(payload, logLabel) {
    const webhook = resolveDiscordWebhook();
    if (!webhook) {
        console.log('[Webhook] No valid webhook URL in DISCORD_WEBHOOK — skipping alert');
        return;
    }

    let url;
    try {
        url = new URL(webhook);
    } catch (e) {
        console.error('[Webhook] Invalid DISCORD_WEBHOOK URL:', e.message);
        return;
    }

    const data = JSON.stringify(payload);
    const req = https.request({
        hostname: url.hostname,
        path: url.pathname + url.search,
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(data)
        }
    }, (res) => {
        let body = '';
        res.on('data', (chunk) => { body += chunk; });
        res.on('end', () => {
            const preview = String(payload.content || '').replace(/\s+/g, ' ').slice(0, 80);
            if (res.statusCode >= 200 && res.statusCode < 300) {
                console.log(`[Webhook] ${logLabel} OK:`, res.statusCode, 'id=' + discordWebhookId(webhook), '→', preview);
            } else {
                console.error(`[Webhook] ${logLabel} rejected:`, res.statusCode, body.slice(0, 300));
            }
        });
    });
    req.on('error', (e) => {
        console.error(`[Webhook] ${logLabel} request error:`, e.message);
    });
    req.write(data);
    req.end();
}

function postDiscordWebhook(content, options = {}) {
    const body = sanitizeWebhookContent(content);
    const prefix = options.prefix ? `${options.prefix} ` : '';
    const payload = { content: `${prefix}${body}`.trim() };
    postDiscordWebhookPayload(payload, 'Alert');
}

async function postDiscordBotChannel(content, allowedMentions) {
    const token = (process.env.DISCORD_TOKEN || '').trim().replace(/^["']|["']$/g, '');
    if (!token) return false;

    const channelId = await resolveAlertChannelId();
    if (!channelId) return false;

    try {
        const res = await fetch(`https://discord.com/api/v10/channels/${channelId}/messages`, {
            method: 'POST',
            headers: {
                Authorization: `Bot ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                content,
                allowed_mentions: allowedMentions || { parse: [] }
            })
        });
        if (!res.ok) {
            const errText = await res.text();
            console.error('[Webhook] Bot channel post failed:', res.status, errText.slice(0, 250));
            return false;
        }
        console.log('[Webhook] Bot ping OK → channel', channelId);
        return true;
    } catch (e) {
        console.error('[Webhook] Bot channel post error:', e.message);
        return false;
    }
}

function buildPingPayload(message) {
    const body = sanitizeWebhookContent(message);
    const text = `[DragoniteAuth] ${body}`;
    const roleId = (process.env.DISCORD_PING_ROLE_ID || '').trim().replace(/^["']|["']$/g, '');

    if (roleId) {
        return {
            content: `<@&${roleId}>\n${text}`,
            allowed_mentions: { roles: [roleId] }
        };
    }
    return {
        content: `@everyone\n${text}`,
        allowed_mentions: { parse: ['everyone'] }
    };
}

function discordAlertUseWebhookFallback() {
    return true;
}

async function postAlertBridge(textContent, options = {}) {
    const secret = (process.env.INTERNAL_ALERT_SECRET || process.env.ADMIN_KEY || '').trim();
    const port = Number(process.env.ALERT_BRIDGE_PORT || 8001);
    if (!secret) return false;

    const pingEveryone = options.pingEveryone === true;
    const roleId = (process.env.DISCORD_PING_ROLE_ID || '').trim() || undefined;
    const payload = JSON.stringify({
        content: textContent,
        pingEveryone,
        roleId
    });
    const maxAttempts = Number(process.env.ALERT_BRIDGE_RETRIES || 4);

    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
        try {
            const res = await fetch(`http://127.0.0.1:${port}/internal/ping`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Alert-Secret': secret
                },
                body: payload
            });
            if (res.ok) {
                console.log('[BotAlert] OK via dragonite-bot bridge', pingEveryone ? '(ping)' : '');
                return true;
            }
            const errBody = await res.text();
            console.warn('[BotAlert] Bridge rejected:', res.status, errBody.slice(0, 200));
            return false;
        } catch (e) {
            if (attempt < maxAttempts) {
                await new Promise((r) => setTimeout(r, 1500));
                continue;
            }
            console.warn('[BotAlert] Bridge unreachable:', e.message);
        }
    }
    return false;
}

async function deliverDiscordAlert(message, options = {}) {
    const pingEveryone = options.pingEveryone === true;
    const body = sanitizeWebhookContent(message);
    const text = `[DragoniteAuth] ${body}`;
    const deliveredByBridge = await postAlertBridge(text, { pingEveryone });
    if (!deliveredByBridge && discordAlertUseWebhookFallback()) {
        if (pingEveryone) {
            postDiscordWebhookPayload(buildPingPayload(message), 'Ping fallback');
        } else {
            postDiscordWebhook(message, { prefix: '[DragoniteAuth]' });
        }
    }
}

function sendDiscordAlert(message) {
    if (!alertDeduplicator.accept(`normal|${message}`)) return;
    console.log('[BotAlert]', String(message).split('\n')[0]);
    deliverDiscordAlert(message, { pingEveryone: false }).catch((e) => {
        console.error('[BotAlert] Delivery failed:', e.message);
    });
}

function sendDiscordAlertPing(message) {
    if (!alertDeduplicator.accept(`ping|${message}`)) return;
    console.log('[BotAlert] Ping:', String(message).split('\n')[0]);
    deliverDiscordAlert(message, { pingEveryone: true }).catch((e) => {
        console.error('[BotAlert] Ping delivery failed:', e.message);
    });
}

/** Crack / invalid / blacklisted attempts only (@everyone) */
function sendCrackAlert(message) {
    console.log('[Webhook] Crack alert:', String(message).split('\n')[0]);
    sendDiscordAlert(message);
}

/** Client startup/auth errors — ping channel so you see failures immediately */
function sendClientErrorAlert(message) {
    console.log('[Webhook] Client error:', String(message).split('\n')[0]);
    sendDiscordAlert(message);
}

function isRateLimited(key, maxRequests) {
    const now = Date.now();
    const bucket = rateLimitBuckets.get(key);
    if (!bucket || bucket.resetAt <= now) {
        rateLimitBuckets.set(key, { count: 1, resetAt: now + CONFIG.RATE_LIMIT_WINDOW_MS });
        return false;
    }

    if (bucket.count >= maxRequests) {
        return true;
    }

    bucket.count += 1;
    return false;
}

function normalizeJarHash(jarHash) {
    if (!jarHash) return null;
    return String(jarHash).trim().toLowerCase();
}

function jarHashEnforcementEnabled() {
    return (CONFIG.EXPECTED_JAR_HASHES && CONFIG.EXPECTED_JAR_HASHES.size > 0)
        || registeredJarHashes.size > 0;
}

function jarHashAllowed(jarHash, licenseData) {
    if (!jarHashEnforcementEnabled()) {
        return true;
    }
    const norm = normalizeJarHash(jarHash);
    if (!norm || !/^[a-f0-9]{64}$/.test(norm)) {
        return false;
    }
    if (CONFIG.EXPECTED_JAR_HASHES.has(norm) || registeredJarHashes.has(norm)) {
        return true;
    }
    const perLicense = licenseData?.allowedJarHashes;
    if (Array.isArray(perLicense)) {
        for (const h of perLicense) {
            if (norm === normalizeJarHash(h)) {
                return true;
            }
        }
    }
    return false;
}

function licenseAllowsJar(licenseData, jarHash) {
    if (licenseData && (licenseData.devMode === true || licenseData.licenseType === 'developer')) {
        return true;
    }
    return jarHashAllowed(jarHash, licenseData);
}

function collectAllowedJarHashes(licenseData) {
    const set = new Set();
    if (jarHashEnforcementEnabled()) {
        for (const h of CONFIG.EXPECTED_JAR_HASHES) {
            set.add(h);
        }
        for (const h of registeredJarHashes) {
            set.add(h);
        }
    }
    if (licenseData?.allowedJarHashes && Array.isArray(licenseData.allowedJarHashes)) {
        for (const h of licenseData.allowedJarHashes) {
            const norm = normalizeJarHash(h);
            if (norm) set.add(norm);
        }
    }
    return [...set];
}

function loadManualJarHashes() {
    if (!fs.existsSync(MANUAL_JAR_HASHES_FILE)) {
        return;
    }
    try {
        const parsed = JSON.parse(fs.readFileSync(MANUAL_JAR_HASHES_FILE, 'utf8'));
        const list = Array.isArray(parsed) ? parsed : (Array.isArray(parsed?.hashes) ? parsed.hashes : []);
        for (const entry of list) {
            const norm = normalizeJarHash(entry);
            if (norm && /^[a-f0-9]{64}$/.test(norm)) {
                registeredJarHashes.add(norm);
            }
        }
    } catch (e) {
        console.error('[Auth] Failed to load manual_jar_hashes.json:', e.message);
    }
}

function persistManualJarHashes() {
    const hashes = [...registeredJarHashes].filter((h) => /^[a-f0-9]{64}$/.test(h)).sort();
    writeJsonAtomic(MANUAL_JAR_HASHES_FILE, { hashes, updatedAt: new Date().toISOString() });
}

function registerManualJarHash(jarSha256) {
    const norm = normalizeJarHash(jarSha256);
    if (!norm || !/^[a-f0-9]{64}$/.test(norm)) {
        throw new Error('jarSha256 must be a 64-character lowercase hex SHA-256');
    }
    const created = !registeredJarHashes.has(norm);
    registeredJarHashes.add(norm);
    persistManualJarHashes();
    return { jarSha256: norm, created };
}

function reportJarHashUpdateRequired(webhookCtx) {
    sendDiscordAlert(formatCrackWebhook('⚠️ JAR HASH MISMATCH (update required)', webhookCtx));
}

function updateRequiredPayload() {
    return {
        message: 'Client build integrity check failed. Please download an official release.',
        updateUrl: process.env.UPDATE_URL || 'https://dragoniteclient.fun/download',
        requiredVersion: process.env.CLIENT_REQUIRED_VERSION || '1.0.0'
    };
}

function sanitizeLicense(licenseKey) {
    return sensitiveFingerprint(licenseKey);
}

function sanitizeHwid(hwid) {
    return sensitiveFingerprint(hwid);
}

// HWID-first auth - check if HWID is already registered
app.post('/v1/auth/hwid', (req, res) => {
    refreshBlacklistIfFileChanged();
    const clientKey = `${clientIp(req)}:hwid`;
    if (isRateLimited(clientKey, CONFIG.AUTH_RATE_LIMIT)) {
        return res.status(429).json({ authenticated: false, error: 'Too many requests' });
    }
    if (!verifyRequestSignature(req)) {
        return res.status(401).json({ authenticated: false, error: 'Invalid signature' });
    }

    const {
        hwid, jarHash, eventId, mcUsername, mcUuid, discordId, discordUsername, osVersion,
        machineGuid, nonce, timestamp, proof, windowsName, pcName
    } = req.body || {};

    if (!hwid) {
        return res.status(400).json({ authenticated: false, error: 'Missing HWID' });
    }

    if (!isValidHWID(hwid)) {
        return res.status(400).json({ authenticated: false, error: 'Invalid HWID' });
    }

    const normalizedHwid = normalizeHWID(hwid);

    if (!IS_PRODUCTION) {
        console.log('[Auth] HWID check:', mcUsername || 'Unknown', sanitizeHwid(normalizedHwid));
    }

    // Check blacklist
    if (isSubjectBlacklisted({ hwid: normalizedHwid, ip: clientIp(req), mcUsername, mcUuid, discordId, discordUsername })) {
        return blacklistedResponse(res);
    }

    // Event ID honeytrap check
    if (ENABLE_EVENT_ID_HONEYTRAP && eventId) {
        const existingEvent = eventIds.get(eventId);
        if (existingEvent && existingEvent.hwid && existingEvent.hwid !== normalizedHwid) {
            // JAR sharing detected
            autoBlacklistJarSharing({
                originalLicense: existingEvent.license,
                originalHwid: existingEvent.hwid,
                newLicense: null,
                newHwid: normalizedHwid,
                newIp: clientIp(req),
                newMcUsername: mcUsername,
                newMcUuid: mcUuid
            });
            blacklistPut(eventId, { reason: 'Shared JAR', auto: true, createdAt: new Date().toISOString() });
            saveBlacklist();
            sendCrackAlert(formatCrackWebhook('🚨 JAR SHARING DETECTED (HWID auth)', {
                licenseKey: 'n/a', licenseData: null, rawHwid: hwid, normalizedHwid, req,
                mcUsername, mcUuid, osVersion, discordUsername, discordId,
                extra: `Event ID: ${eventId}`
            }));
            return blacklistedResponse(res);
        }
        if (!existingEvent) {
            eventIds.set(eventId, {
                hwid: normalizedHwid,
                license: null,
                ip: clientIp(req),
                username: mcUsername || 'Unknown',
                firstSeen: new Date().toISOString(),
                lastSeen: new Date().toISOString()
            });
            saveEventIds();
        }
    }

    refreshLicensesIfFileChanged();

    // Find license bound to this HWID
    let foundLicenseKey = null;
    let licenseData = null;
    for (const [licenseKey, data] of licenses) {
        if (licenseHwidMatches(data.hwid, hwid)) {
            if (!licenseIsExpired(data)) {
                foundLicenseKey = licenseKey;
                licenseData = data;
                canonicalizeLicenseHwid(licenseData, hwid);
                break;
            }
        }
    }

    if (!foundLicenseKey || !licenseData) {
        console.log('[Auth] HWID not bound — license required:', mcUsername, 'hash', sanitizeHwid(normalizedHwid));
        return res.json({
            authenticated: false,
            reason: 'hwid_not_found',
            message: 'Please enter your license key'
        });
    }

    const guidCheck = enforceMachineGuidBinding(licenseData, machineGuid);
    if (!guidCheck.ok) {
        punishCrack('🚨 LICENSE / MACHINE MISMATCH (HWID auto-login)', {
            licenseKey: foundLicenseKey, licenseData, rawHwid: hwid, normalizedHwid, req,
            mcUsername, mcUuid, osVersion, discordUsername, discordId,
            extra: 'MachineGuid does not match license record'
        }, { autoBlacklist: false });
        return res.status(403).json({
            authenticated: false,
            blacklisted: false,
            error: 'Machine identity does not match this license. Contact support if this is your PC.'
        });
    }

    if (!licenseAllowsJar(licenseData, jarHash)) {
        reportJarHashUpdateRequired({
            licenseKey: foundLicenseKey, licenseData, rawHwid: hwid, normalizedHwid, req,
            mcUsername, mcUuid, osVersion, discordUsername, discordId, windowsName, pcName,
            extra: `jarHash: ${sanitizeWebhookUserField(jarHash || 'missing')}`
        });
        const update = updateRequiredPayload();
        return res.status(426).json({
            authenticated: false,
            reason: 'update_required',
            message: update.message,
            updateRequired: true,
            updateUrl: update.updateUrl,
            requiredVersion: update.requiredVersion
        });
    }

    if (REQUIRE_AUTH_PROOF) {
        if (!nonce || proof == null || proof === '') {
            return res.status(401).json({ authenticated: false, error: 'Nonce-bound release proof required' });
        }
        const nonceData = nonces.get(nonce);
        if (!nonceData || nonceData.used) {
            return res.status(400).json({ authenticated: false, error: 'Invalid or used nonce' });
        }
        if (Date.now() - nonceData.timestamp > 5 * 60 * 1000) {
            return res.status(400).json({ authenticated: false, error: 'Nonce expired' });
        }
        const tsNum = Number(timestamp);
        if (!Number.isNaN(tsNum) && Math.abs(tsNum - nonceData.timestamp) > 5 * 60 * 1000) {
            return res.status(400).json({ authenticated: false, error: 'Challenge timestamp mismatch' });
        }
        const hwidProofValid = typeof proof === 'string' && proof.startsWith('hmac2:')
            ? verifyHwidNonceBoundHmac2(
                nonce, timestamp, hwid, proof, jarHash,
                NATIVE_IKM_HEX, HWID_AUTH_PROOF_MARKER)
            : verifyAuthProof(
                nonce, timestamp, hwid, HWID_AUTH_PROOF_MARKER, proof, jarHash);
        if (!hwidProofValid) {
            if (typeof proof === 'string' && proof.startsWith('hmac2:')) {
                const diagnosticPayload = `${nonce}|${proofTimestampString(timestamp)}|${hwid}|${HWID_AUTH_PROOF_MARKER}|${normalizeJarHash(jarHash) || ''}`;
                const diagnostic = diagnoseLicenseBoundHmac2(
                    diagnosticPayload, proof.slice('hmac2:'.length),
                    NATIVE_IKM_HEX, HWID_AUTH_PROOF_MARKER);
                console.error('[Auth] HMAC2 mismatch fingerprints:', JSON.stringify(diagnostic));
            }
            console.log('[Auth] HWID auto-login rejected: invalid nonce-bound proof from', clientIp(req));
            punishCrack('❌ CRACK ATTEMPT (invalid HWID auto-login proof)', {
                licenseKey: foundLicenseKey, licenseData, rawHwid: hwid, normalizedHwid, req,
                mcUsername, mcUuid, osVersion, discordUsername, discordId
            }, { autoBlacklist: false });
            return res.status(401).json({ authenticated: false, error: 'Invalid proof' });
        }
        nonceData.used = true;
    }

    // HWID found and valid - create session
    const sessionToken = generateSessionToken();
    const expiresAt = new Date(Date.now() + CONFIG.SESSION_DURATION_HOURS * 60 * 60 * 1000).toISOString();

    putSession(sessionToken, {
        username: licenseData.username || mcUsername || 'Unknown',
        hwid: normalizedHwid,
        rawHwid: hwid,
        license: foundLicenseKey,
        expiresAt,
        lastHeartbeat: new Date().toISOString(),
        jarHash: normalizeJarHash(jarHash) || null,
        sessionStart: new Date().toISOString(),
        ip: clientIp(req),
        osVersion: osVersion || licenseData.osVersion || 'Unknown',
        mcUsername: mcUsername || licenseData.mcUsername || 'Unknown',
        mcUuid: mcUuid || licenseData.mcUuid || 'Unknown',
        windowsName: licenseData.windowsName || 'Unknown',
        pcName: licenseData.pcName || 'Unknown',
        discordId: discordId || licenseData.discordId || null,
        discordUsername: discordUsername || licenseData.discordUsername || null
    });

    applyLoginTelemetry(licenseData, req, {
        mcUsername, mcUuid, discordId, discordUsername,
        windowsName: licenseData.windowsName, pcName: licenseData.pcName,
        osVersion: osVersion || licenseData.osVersion,
        machineGuid: typeof machineGuid === 'string' ? machineGuid : licenseData.machineGuid
    });
    clearLicenseFailureStrikes(normalizedHwid);
    saveLicenses();

    console.log('[Auth] Login OK (HWID auto):', mcUsername || licenseData.mcUsername,
        sensitiveFingerprint(foundLicenseKey));
    sendDiscordAlert(formatLoginWebhook('✅ AUTO-LOGIN (HWID)', {
        licenseKey: foundLicenseKey, licenseData, rawHwid: hwid, normalizedHwid, req,
        mcUsername, mcUuid, osVersion: osVersion || licenseData.osVersion,
        discordUsername, discordId,
        windowsName: licenseData.windowsName, pcName: licenseData.pcName
    }));

    return sendSignedAuthJson(req, res, 200, {
        authenticated: true,
        sessionToken,
        expiresAt,
        username: licenseData.username || mcUsername,
        licenseType: licenseData.licenseType || 'lifetime_normal',
        gracePeriod: CONFIG.GRACE_PERIOD_HOURS,
        allowedJarHashes: collectAllowedJarHashes(licenseData)
    });
});

app.get('/v1/challenge', (req, res) => {
    const clientKey = `${clientIp(req)}:challenge`;
    if (isRateLimited(clientKey, CONFIG.CHALLENGE_RATE_LIMIT)) {
        return res.status(429).json({ error: 'Too many requests' });
    }

    const { nonce, timestamp } = generateNonce();
    res.json({
        nonce,
        timestamp,
        expiresIn: 300
    });
});

app.post('/v1/auth', (req, res) => {
    refreshBlacklistIfFileChanged();
    const clientKey = `${clientIp(req)}:auth`;
    if (isRateLimited(clientKey, CONFIG.AUTH_RATE_LIMIT)) {
        return res.status(429).json({ success: false, error: 'Too many requests' });
    }
    if (!verifyRequestSignature(req)) {
        console.log('[Auth] License login rejected: invalid signature from', clientIp(req));
        return res.status(401).json({ success: false, error: 'Invalid signature' });
    }

    const { username, hwid, nonce, timestamp, license, jarHash, machineGuid, eventId, windowsName, pcName, mcUsername, mcUuid, osVersion, discordId, discordUsername, proof } = req.body || {};
    console.log('[Auth] License login attempt:', sanitizeLicense(license), 'MC:', mcUsername || username || 'Unknown', 'IP:', clientIp(req));

    if (!username || !hwid || !nonce || !license) {
        return res.status(400).json({ success: false, error: 'Missing fields' });
    }

    if (!isValidHWID(hwid)) {
        punishCrack('❌ CRACK ATTEMPT (invalid HWID)', {
            licenseKey: license, licenseData: null, rawHwid: hwid, normalizedHwid: null, req,
            mcUsername, mcUuid, osVersion, discordUsername, discordId, windowsName, pcName
        });
        return blacklistedResponse(res, 400);
    }

    // Check MachineGuid against the hwids.txt allowlist (if the file has entries).
    // Re-read on every auth so you can add/remove entries without restarting the server.
    const approvedHwids = loadApprovedHwids();
    if (approvedHwids.size > 0) {
        const submittedGuid = (typeof machineGuid === 'string' ? machineGuid : '').toLowerCase().trim();
        if (!submittedGuid) {
            return res.status(400).json({
                success: false,
                blacklisted: false,
                error: 'Machine ID missing from client. Reinstall the latest Dragonite build.'
            });
        }
        if (!approvedHwids.has(submittedGuid)) {
            punishCrack('❌ CRACK ATTEMPT (machine not approved)', {
                licenseKey: license, licenseData: null, rawHwid: hwid, normalizedHwid: null, req,
                mcUsername, mcUuid, osVersion, discordUsername, discordId, windowsName, pcName,
                extra: `MachineGuid: ${submittedGuid}`
            });
            return blacklistedResponse(res);
        }
    }

    // Normalize to SHA-256 so storage is always consistent 64-char hex.
    // The raw HWID never touches disk or logs.
    const normalizedHwid = normalizeHWID(hwid);

    if (isSubjectBlacklisted({
        licenseKey: license, hwid: normalizedHwid, ip: clientIp(req),
        mcUsername, mcUuid, discordId, discordUsername
    })) {
        return blacklistedResponse(res);
    }

    // === EVENT ID HONEYTRAP DETECTION ===
    // If same Event ID is used by different HWID, someone shared the JAR
    if (ENABLE_EVENT_ID_HONEYTRAP && eventId) {
        const existingEvent = eventIds.get(eventId);
        if (existingEvent) {
            // Check if HWID is different
            if (existingEvent.hwid && existingEvent.hwid !== normalizedHwid) {
                const originalLicense = existingEvent.license;
                const originalHwid = existingEvent.hwid;

                autoBlacklistJarSharing({
                    originalLicense,
                    originalHwid,
                    newLicense: license,
                    newHwid: normalizedHwid,
                    newIp: clientIp(req),
                    newMcUsername: mcUsername,
                    newMcUuid: mcUuid
                });
                blacklistPut(eventId, { reason: 'Shared JAR', auto: true, createdAt: new Date().toISOString() });
                saveBlacklist();
                saveEventIds();

                sendCrackAlert(formatCrackWebhook('🚨 JAR SHARING DETECTED (license auth)', {
                    licenseKey: license, licenseData: null, rawHwid: hwid, normalizedHwid, req,
                    mcUsername, mcUuid, osVersion, discordUsername, discordId, windowsName, pcName,
                    extra: `Event ID: ${eventId}\nOriginal license: ${originalLicense || 'unknown'}`
                }));

                return blacklistedResponse(res);
            }
            
            // Same HWID - update last seen
            existingEvent.lastSeen = new Date().toISOString();
            existingEvent.ip = clientIp(req);
        } else {
            // New Event ID - log it
            eventIds.set(eventId, {
                hwid: normalizedHwid,
                license,
                ip: clientIp(req),
                username,
                firstSeen: new Date().toISOString(),
                lastSeen: new Date().toISOString()
            });
        }
        saveEventIds();
    }

    refreshLicensesIfFileChanged();
    const licenseEntry = getLicenseEntry(license);
    if (!licenseEntry) {

        const blCtx = buildBlacklistCtx({
            normalizedHwid, rawHwid: hwid, ip: clientIp(req),
            mcUsername, mcUuid, discordId, discordUsername, licenseKey: license
        });
        const strike = recordLicenseFailure(blCtx);
        if (strike.blacklisted) {
            sendCrackAlert(formatCrackWebhook('🚫 AUTO-BLACKLIST (too many invalid licenses)', {
                licenseKey: license, licenseData: null, rawHwid: hwid, normalizedHwid, req,
                mcUsername, mcUuid, osVersion, discordUsername, discordId, windowsName, pcName,
                extra: `${strike.count} failed attempts`
            }));
            return blacklistedResponse(res);
        }
        if (strike.temporarilyBlocked) {
            const cooldownMinutes = Math.ceil(CONFIG.LICENSE_FAILURE_WINDOW_MS / 60_000);
            sendDiscordAlert(formatCrackWebhook('⛔ RATE LIMITED (too many invalid licenses)', {
                licenseKey: license, licenseData: null, rawHwid: hwid, normalizedHwid, req,
                mcUsername, mcUuid, osVersion, discordUsername, discordId, windowsName, pcName,
                extra: `Temporarily blocked for ${cooldownMinutes} min â€” ${strike.count} failed attempts`
            }));
            return res.status(429).json({
                success: false,
                blacklisted: false,
                error: `Too many invalid license attempts. Please wait ${cooldownMinutes} minutes before trying again.`,
                attemptsRemaining: 0
            });
        }
        sendDiscordAlert(formatCrackWebhook('❌ INVALID LICENSE ATTEMPT', {
            licenseKey: license, licenseData: null, rawHwid: hwid, normalizedHwid, req,
            mcUsername, mcUuid, osVersion, discordUsername, discordId, windowsName, pcName,
            extra: `Attempt ${strike.count} of ${strike.count + strike.remaining} before cooldown`
        }));
        return res.status(401).json({
            success: false,
            blacklisted: false,
            error: `Invalid license. ${strike.remaining} attempt(s) remaining before a temporary cooldown.`,
            attemptsRemaining: strike.remaining
        });
    }
    const resolvedLicenseKey = licenseEntry.key;
    const licenseData = licenseEntry.data;

    // Check blacklist (license, HWID, IP, MC username, MC UUID)
    if (isSubjectBlacklisted({
        licenseKey: resolvedLicenseKey, hwid: normalizedHwid, ip: clientIp(req),
        mcUsername, mcUuid, discordId, discordUsername
    })) {
        return blacklistedResponse(res);
    }

    const nonceData = nonces.get(nonce);
    if (!nonceData || nonceData.used) {
        return res.status(400).json({ success: false, error: 'Invalid or used nonce' });
    }

    if (Date.now() - nonceData.timestamp > 5 * 60 * 1000) {
        return res.status(400).json({ success: false, error: 'Nonce expired' });
    }

    const tsNum = Number(timestamp);
    if (!Number.isNaN(tsNum) && Math.abs(tsNum - nonceData.timestamp) > 5 * 60 * 1000) {
        return res.status(400).json({ success: false, error: 'Challenge timestamp mismatch' });
    }

    nonceData.used = true;

    if (!verifyAuthProof(nonce, timestamp, hwid, license, proof, jarHash)) {
        const proofKind = typeof proof === 'string' && (proof.startsWith('ecdsa2:') || proof.startsWith('ecdsa:')) ? 'ecdsa' : (typeof proof === 'string' && proof.startsWith('ed25519:') ? 'ed25519' : (proof ? 'unknown' : 'missing'));
        console.log('[Auth] License login rejected: invalid challenge proof (kind=%s)', proofKind);
        console.log('[Auth] proof jarHash:', jarHash || '(missing)', '| VPS ECDSA configured:', !!CONFIG.ECDSA_PUBLIC_KEY_SPKI_B64);
        if (process.env.AUTH_DEBUG_PROOF === 'true') {
            console.log('[Auth] proof debug identifiers:', sensitiveFingerprint(hwid),
                sensitiveFingerprint(license));
        }
        punishCrack('❌ CRACK ATTEMPT (invalid challenge proof)', {
            licenseKey: resolvedLicenseKey, licenseData, rawHwid: hwid, normalizedHwid, req,
            mcUsername, mcUuid, osVersion, discordUsername, discordId, windowsName, pcName,
            extra: `proof=${proofKind} jarHash=${jarHash ? 'present' : 'missing'}`
        }, { autoBlacklist: false });
        const fp = ecdsaSpkiFingerprint();
        let detail;
        if (IS_PRODUCTION) {
            detail = 'Authentication rejected (security check failed).';
        } else {
            detail = !ecdsaSpkiB64Clean()
                ? 'VPS is missing ECDSA_PUBLIC_KEY_SPKI_B64 in .env.'
                : (proofKind === 'ecdsa'
                    ? `ECDSA signature did not verify. VPS SPKI fingerprint=${fp} - must match JAR log line. Copy ECDSA_PUBLIC_KEY_SPKI_B64 from the latest Build-Release.bat output (native/master_ecdsa_spki.b64), then pm2 restart auth-server.`
                    : 'Client sent a non-ECDSA proof - deploy the latest release JAR.');
        }
        return crackDeniedResponse(res, 401, detail);
    }

    if (licenseIsExpired(licenseData)) {
        // Expired subscription is not a crack attempt — no strikes or auto-blacklist.
        return res.status(401).json({
            success: false,
            blacklisted: false,
            error: 'License expired. Renew your subscription to continue.'
        });
    }

    const guidCheck = enforceMachineGuidBinding(licenseData, machineGuid);
    if (!guidCheck.ok) {
        punishCrack('🚨 LICENSE / MACHINE MISMATCH (license login)', {
            licenseKey: resolvedLicenseKey, licenseData, rawHwid: hwid, normalizedHwid, req,
            mcUsername, mcUuid, osVersion, discordUsername, discordId, windowsName, pcName,
            extra: 'MachineGuid does not match license record'
        });
        return blacklistedResponse(res);
    }

    if (!licenseAllowsJar(licenseData, jarHash)) {
        const normJar = normalizeJarHash(jarHash) || '(missing)';
        console.log('[Auth] License login rejected: JAR hash not allowlisted:', normJar);
        reportJarHashUpdateRequired({
            licenseKey: resolvedLicenseKey, licenseData, rawHwid: hwid, normalizedHwid, req,
            mcUsername, mcUuid, osVersion, discordUsername, discordId, windowsName, pcName,
            extra: `jarHash: ${sanitizeWebhookUserField(jarHash || 'missing')}`
        });
        const update = updateRequiredPayload();
        return res.status(426).json({
            success: false,
            blacklisted: false,
            error: update.message,
            updateRequired: true,
            updateUrl: update.updateUrl,
            requiredVersion: update.requiredVersion,
            jarHash: normJar !== '(missing)' ? normJar : undefined,
            hint: 'This client build is no longer allowed by the release channel.'
        });
    }

    if (licenseData.username && licenseData.username !== username) {
        sendDiscordAlert(formatLoginWebhook('⚠️ USERNAME MISMATCH DETECTED (Allowed Launch)', {
            licenseKey: resolvedLicenseKey, licenseData, rawHwid: hwid, normalizedHwid, req,
            mcUsername, mcUuid, osVersion, discordUsername, discordId, windowsName, pcName
        }));
    }

    if (licenseData.hwid && !licenseHwidMatches(licenseData.hwid, hwid)) {
        if (licenseData.hwidChanges >= CONFIG.MAX_HWID_CHANGES) {
            // License already bound to different HWID - notify owner for giveaway tracking
            const winUser = windowsName || 'Unknown';
            const pc = pcName || 'Unknown';
            const mcUser = mcUsername || 'Unknown';
            const mcId = mcUuid || 'Unknown';
            punishCrack('🎁 CRACK ATTEMPT (license already used on another PC)', {
                licenseKey: resolvedLicenseKey, licenseData, rawHwid: hwid, normalizedHwid, req,
                mcUsername: mcUser, mcUuid: mcId, osVersion, discordUsername, discordId,
                windowsName: winUser, pcName: pc,
                extra: `Bound HWID (hash): ${licenseData.hwid}`
            });
            return blacklistedResponse(res);
        }

        // Preserve current values in history arrays before overwrite
        if (licenseData.hwid && !licenseData.pastHwids.includes(licenseData.hwid)) {
            licenseData.pastHwids.push(licenseData.hwid);
        }
        if (licenseData.windowsName && !licenseData.pastWindowsNames.includes(licenseData.windowsName)) {
            licenseData.pastWindowsNames.push(licenseData.windowsName);
        }
        if (licenseData.pcName && !licenseData.pastPcNames.includes(licenseData.pcName)) {
            licenseData.pastPcNames.push(licenseData.pcName);
        }
        if (licenseData.mcUsername && !licenseData.pastMcUsernames.includes(licenseData.mcUsername)) {
            licenseData.pastMcUsernames.push(licenseData.mcUsername);
        }
        if (licenseData.mcUuid && !licenseData.pastMcUuids.includes(licenseData.mcUuid)) {
            licenseData.pastMcUuids.push(licenseData.mcUuid);
        }
        if (licenseData.discordId && !licenseData.pastDiscordIds.includes(licenseData.discordId)) {
            licenseData.pastDiscordIds.push(licenseData.discordId);
        }
        if (licenseData.discordUsername && !licenseData.pastDiscordUsernames.includes(licenseData.discordUsername)) {
            licenseData.pastDiscordUsernames.push(licenseData.discordUsername);
        }
        if (licenseData.ip && !licenseData.pastIps.includes(licenseData.ip)) {
            licenseData.pastIps.push(licenseData.ip);
        }
        if (licenseData.lastIp && !licenseData.pastIps.includes(licenseData.lastIp)) {
            licenseData.pastIps.push(licenseData.lastIp);
        }

        licenseData.hwid = normalizedHwid;
        licenseData.hwidChanges += 1;
        licenseData.windowsName = windowsName || licenseData.windowsName || null;
        licenseData.pcName = pcName || licenseData.pcName || null;
        licenseData.mcUsername = mcUsername || licenseData.mcUsername || null;
        licenseData.mcUuid = mcUuid || licenseData.mcUuid || null;
    }

    if (!licenseData.hwid) {
        canonicalizeLicenseHwid(licenseData, hwid);
        licenseData.windowsName = windowsName || null;
        licenseData.pcName = pcName || null;
        licenseData.mcUsername = mcUsername || null;
        licenseData.mcUuid = mcUuid || null;

        // Capture original owner details
        licenseData.ownerHwid = licenseData.hwid;
        licenseData.ownerWindowsName = windowsName || null;
        licenseData.ownerPcName = pcName || null;
        licenseData.ownerMcUsername = mcUsername || null;
        licenseData.ownerMcUuid = mcUuid || null;
        licenseData.ownerDiscordId = discordId || null;
        licenseData.ownerDiscordUsername = discordUsername || null;
        licenseData.ownerIp = clientIp(req) || null;
    }

    const sessionToken = generateSessionToken();
    const expiresAt = new Date(Date.now() + CONFIG.SESSION_DURATION_HOURS * 60 * 60 * 1000).toISOString();

    putSession(sessionToken, {
        username,
        hwid: normalizedHwid,
        rawHwid: hwid,
        license: resolvedLicenseKey,
        expiresAt,
        lastHeartbeat: new Date().toISOString(),
        jarHash: normalizeJarHash(jarHash) || null,
        sessionStart: new Date().toISOString(),
        ip: clientIp(req),
        osVersion: osVersion || 'Unknown',
        mcUsername: mcUsername || 'Unknown',
        mcUuid: mcUuid || 'Unknown',
        windowsName: windowsName || 'Unknown',
        pcName: pcName || 'Unknown',
        discordId: discordId || null,
        discordUsername: discordUsername || null
    });

    applyLoginTelemetry(licenseData, req, {
        mcUsername, mcUuid, discordId, discordUsername, windowsName, pcName, osVersion,
        machineGuid: typeof machineGuid === 'string' ? machineGuid : licenseData.machineGuid
    });
    clearLicenseFailureStrikes(normalizedHwid);
    saveLicenses();
    const mcUser = mcUsername || licenseData.mcUsername || 'Unknown';
    console.log('[Auth] Login OK (license):', mcUser, sensitiveFingerprint(resolvedLicenseKey));
    sendDiscordAlert(formatLoginWebhook('✅ LOGIN SUCCESS', {
        licenseKey: resolvedLicenseKey, licenseData, rawHwid: hwid, normalizedHwid, req,
        mcUsername, mcUuid, osVersion, discordUsername, discordId, windowsName, pcName
    }));

    return sendSignedAuthJson(req, res, 200, {
        success: true,
        sessionToken,
        expiresAt,
        licenseType: licenseData.licenseType || 'lifetime_normal',
        discordId: discordId || null,
        discordUsername: discordUsername || null,
        gracePeriod: CONFIG.GRACE_PERIOD_HOURS,
        allowedJarHashes: collectAllowedJarHashes(licenseData)
    });
});

app.post('/v1/heartbeat', (req, res) => {
    const hbKey = `${clientIp(req)}:heartbeat`;
    if (isRateLimited(hbKey, CONFIG.HEARTBEAT_RATE_LIMIT)) {
        return res.status(429).json({ ok: false, error: 'Too many requests', revoked: false });
    }
    if (!verifyRequestSignature(req)) {
        return res.status(401).json({ ok: false, error: 'Invalid signature', revoked: true });
    }

    const { sessionToken, hwid, jarHash } = req.body || {};
    if (!sessionToken) {
        return res.status(400).json({ ok: false, error: 'Missing session token' });
    }

    const session = sessions.get(sessionToken);
    if (!session) {
        return res.status(401).json({ ok: false, error: 'Invalid session', revoked: true });
    }

    if (sessionIsExpired(session)) {
        dropSession(sessionToken);
        return res.status(401).json({ ok: false, error: 'Session expired', revoked: true });
    }

    const heartbeatEntry = getLicenseEntry(session.license);
    const heartbeatLicense = heartbeatEntry?.data;
    const effectiveJarHash = jarHash || session.jarHash;
    if (jarHashEnforcementEnabled() && !jarHashAllowed(effectiveJarHash, heartbeatLicense)) {
        dropSession(sessionToken);
        reportJarHashUpdateRequired({
            licenseKey: session.license, licenseData: heartbeatLicense,
            rawHwid: session.rawHwid, normalizedHwid: session.hwid, req,
            mcUsername: session.mcUsername, mcUuid: session.mcUuid,
            osVersion: session.osVersion, discordUsername: session.discordUsername,
            discordId: session.discordId, windowsName: session.windowsName, pcName: session.pcName,
            extra: `jarHash: ${sanitizeWebhookUserField(effectiveJarHash || 'missing')}`
        });
        const update = updateRequiredPayload();
        return res.status(426).json({
            ok: false, error: update.message, revoked: true, blacklisted: false,
            updateRequired: true, updateUrl: update.updateUrl, requiredVersion: update.requiredVersion
        });
    }
    if (jarHash) {
        session.jarHash = normalizeJarHash(jarHash) || session.jarHash;
    }

    if (hwid) {
        if (!licenseHwidMatches(session.hwid, hwid)) {
            dropSession(sessionToken);
            console.log('[Auth] Heartbeat HWID mismatch for', session.username);
            const lic = licenses.get(session.license);
            punishCrack('❌ CRACK ATTEMPT (heartbeat HWID mismatch)', {
                licenseKey: session.license, licenseData: lic, rawHwid: hwid, normalizedHwid: normalizeHWID(hwid), req,
                mcUsername: session.mcUsername, mcUuid: session.mcUuid,
                osVersion: session.osVersion, discordUsername: session.discordUsername, discordId: session.discordId,
                windowsName: session.windowsName, pcName: session.pcName,
                extra: `Session HWID (hash): ${session.hwid}`
            });
            return res.status(401).json({ ok: false, error: 'HWID mismatch', revoked: true, blacklisted: false });
        }
    }

    const { heartbeatProof, heartbeatSlot } = req.body || {};
    if (heartbeatProof != null && heartbeatSlot != null) {
        const slot = Number(heartbeatSlot);
        const nowSlot = Math.floor(Date.now() / 60000);
        let ok = false;
        for (const candidate of [slot, slot - 1, nowSlot, nowSlot - 1]) {
            const expected = crypto.createHmac('sha256', sessionToken)
                .update(String(candidate))
                .digest('base64');
            if (timingSafeEqualString(heartbeatProof, expected)) {
                ok = true;
                break;
            }
        }
        if (!ok) {
            dropSession(sessionToken);
            return res.status(401).json({ ok: false, error: 'Invalid heartbeat proof', revoked: true });
        }
    }

    if (!sessionStillAuthorized(session, clientIp(req))) {
        refreshBlacklistIfFileChanged();
        const lic = heartbeatLicense;
        const revokedIp = clientIp(req);
        const licenseExpired = lic && licenseIsExpired(lic);
        if (isSubjectBlacklisted({
            licenseKey: session.license, hwid: session.hwid, ip: revokedIp,
            mcUsername: session.mcUsername, mcUuid: session.mcUuid,
            discordId: session.discordId, discordUsername: session.discordUsername
        })) {
            console.log('[Auth] Heartbeat revoked — blacklisted:', session.mcUsername, sensitiveFingerprint(session.license));
        } else if (licenseExpired) {
            console.log('[Auth] Heartbeat revoked — license expired:', session.mcUsername, sensitiveFingerprint(session.license));
        } else if (!heartbeatEntry) {
            console.log('[Auth] Heartbeat revoked — license not found:', session.mcUsername, sensitiveFingerprint(session.license));
        } else {
            console.log('[Auth] Heartbeat revoked — session unauthorized:', session.mcUsername, sensitiveFingerprint(session.license));
        }
        dropSession(sessionToken);
        revokeSessionsForLicense(session.license);
        const hbError = licenseExpired ? 'License expired' : 'Session revoked';
        return res.status(401).json({ ok: false, error: hbError, revoked: true });
    }

    if (PERSIST_SESSIONS) sessionStore.upsert(sessionToken, session);
    return sendSignedAuthJson(req, res, 200, { ok: true, expiresAt: session.expiresAt });
});

// Logout endpoint - called when MC closes
app.post('/v1/logout', (req, res) => {
    if (!verifyRequestSignature(req, { requireSession: true })) {
        return res.status(401).json({ ok: false, error: 'Invalid signature' });
    }
    const { sessionToken } = req.body || {};
    if (!sessionToken) {
        return res.status(400).json({ ok: false, error: 'Missing session token' });
    }

    const session = sessions.get(sessionToken);
    if (session) {
        const sessionEnd = new Date();
        const sessionStart = new Date(session.sessionStart || sessionEnd);
        const sessionDurationSeconds = Math.floor((sessionEnd - sessionStart) / 1000);

        const entry = getLicenseEntry(session.license);
        const lic = entry?.data || null;
        if (entry && lic) {
            lic.lastLogout = sessionEnd.toISOString();
            lic.lastSessionDurationSeconds = sessionDurationSeconds;
            lic.active = false;
            saveLicenses();
        }

        sendDiscordAlert(formatLogoutWebhook({
            ...session,
            license: entry?.key || session.license
        }, lic, sessionDurationSeconds));
        dropSession(sessionToken);
    }
    
    res.json({ ok: true });
});

function clientErrorRequestTrusted(req) {
    const body = req.body || {};
    if (!body.errorCode && !body.errorMessage) {
        return { trusted: false, status: 400, error: 'Missing error fields' };
    }

    if (body.sessionToken
            && sessions.has(body.sessionToken)
            && verifyRequestSignature(req, { requireSession: true })) {
        return { trusted: true, mode: 'session' };
    }

    return {
        trusted: false,
        status: 401,
        error: 'An authenticated session is required for client error reports'
    };
}

app.post('/v1/client-error', (req, res) => {
    const clientKey = `${clientIp(req)}:client-error`;
    if (isRateLimited(clientKey, 30)) {
        return res.status(429).json({ ok: false, error: 'Too many requests' });
    }

    const auth = clientErrorRequestTrusted(req);
    if (!auth.trusted) {
        const body = req.body || {};
        console.warn('[ClientError] Rejected from', clientIp(req), auth.error || '', 'code=', body.errorCode || '');
        const payload = { ok: false, error: auth.error || 'Unauthorized' };
        if (auth.hint) payload.hint = auth.hint;
        return res.status(auth.status || 401).json(payload);
    }

    const body = req.body || {};
    const {
        errorCode, errorMessage, hwid, license: clientLicense, mcUsername, mcUuid, osVersion,
        discordId, discordUsername, windowsName, pcName, machineGuid, ip, jarHash, detail
    } = body;

    const normalizedHwid = hwid && isValidHWID(hwid) ? normalizeHWID(hwid) : null;
    let licenseKey = null;
    let licenseData = null;
    // 1. Try to find license by HWID (most reliable)
    if (normalizedHwid) {
        for (const [key, data] of licenses) {
            if (licenseHwidMatches(data.hwid, hwid)) {
                licenseKey = key;
                licenseData = data;
                break;
            }
        }
    }
    // 2. Fall back to license key the client sent (e.g. blacklisted before HWID was bound)
    if (!licenseKey && clientLicense) {
        const resolved = getLicenseEntry(clientLicense);
        if (resolved) {
            licenseKey = resolved.key;
            licenseData = resolved.data;
        } else {
            // Key exists in client report but not in our DB — still show it
            licenseKey = String(clientLicense).trim();
        }
    }

    sendClientErrorAlert(formatErrorWebhook('🚨 CLIENT ERROR (startup/auth)', {
        licenseKey: licenseKey || clientLicense || 'n/a',
        licenseData,
        rawHwid: hwid || null,
        normalizedHwid,
        req,
        mcUsername,
        mcUuid,
        osVersion,
        discordUsername,
        discordId,
        windowsName,
        pcName,
        errorCode: errorCode || 'client_error',
        errorMessage: errorMessage || 'unknown',
        clientReportedIp: ip || null,
        jarHash,
        detail
    }));

    res.json({ ok: true });
});

app.post('/v1/security-report', (req, res) => {
    const clientKey = `${clientIp(req)}:security`;
    if (isRateLimited(clientKey, 10)) {
        return res.status(429).json({ ok: false, error: 'Too many requests' });
    }
    if (!verifyRequestSignature(req, { requireSession: true })) {
        return res.status(401).json({ ok: false, error: 'Invalid signature' });
    }
    const { sessionToken, reason, errorCode, errorMessage } = req.body || {};
    if (!sessionToken) {
        return res.status(400).json({ ok: false, error: 'Missing session token' });
    }
    const session = sessions.get(sessionToken);
    if (!session || sessionIsExpired(session)) {
        return res.status(401).json({ ok: false, error: 'Invalid session' });
    }
    const lic = licenses.get(session.license);
    sendClientErrorAlert(formatErrorWebhook('⚠️ CLIENT SECURITY REPORT', {
        licenseKey: session.license,
        licenseData: lic,
        rawHwid: session.rawHwid,
        normalizedHwid: session.hwid,
        req,
        mcUsername: session.mcUsername,
        mcUuid: session.mcUuid,
        osVersion: session.osVersion,
        discordUsername: session.discordUsername,
        discordId: session.discordId,
        windowsName: session.windowsName,
        pcName: session.pcName,
        errorCode: errorCode || 'security_report',
        errorMessage: errorMessage || reason || 'unknown',
        jarHash: session.jarHash
    }));
    res.json({ ok: true });
});

app.post('/v1/verify', (req, res) => {
    const clientKey = `${clientIp(req)}:verify`;
    if (isRateLimited(clientKey, CONFIG.VERIFY_RATE_LIMIT)) {
        return res.status(429).json({ valid: false, error: 'Too many requests' });
    }
    if (!verifyRequestSignature(req, { requireSession: true })) {
        return res.status(401).json({ valid: false, error: 'Invalid signature' });
    }

    const { jarHash, sessionToken } = req.body || {};
    if (!sessionToken || !jarHash) {
        return res.status(400).json({ valid: false, error: 'Missing verification fields' });
    }

    const session = sessions.get(sessionToken);
    if (!session) {
        return res.status(401).json({ valid: false, error: 'Invalid session' });
    }

    if (sessionIsExpired(session)) {
        dropSession(sessionToken);
        return res.status(401).json({ valid: false, error: 'Session expired' });
    }

    if (!sessionStillAuthorized(session, clientIp(req))) {
        dropSession(sessionToken);
        return res.status(401).json({ valid: false, error: 'Session revoked' });
    }

    const licenseData = licenses.get(session.license);
    if (licenseIsExpired(licenseData)) {
        dropSession(sessionToken);
        return res.status(401).json({ valid: false, error: 'License expired' });
    }

    const effectiveJar = normalizeJarHash(jarHash) || session.jarHash;
    if (!licenseAllowsJar(licenseData, effectiveJar)) {
        dropSession(sessionToken);
        return res.status(403).json({ valid: false, error: 'Invalid client build' });
    }

    if (effectiveJar && !session.jarHash) {
        session.jarHash = effectiveJar;
        if (PERSIST_SESSIONS) sessionStore.upsert(sessionToken, session);
    }

    res.json({ valid: true, mode: 'session' });
});

app.post('/v1/admin/license', (req, res) => {
    if (!requireAdmin(req, res)) return;
    const { username, licenseKey, durationDays, licenseType, lifetime, jarHashes } = req.body || {};

    if (!licenseKey) {
        return res.status(400).json({ success: false, error: 'Missing licenseKey' });
    }

    const storedKey = normalizeLicenseKey(licenseKey);
    if (storedKey.length !== 20) {
        return res.status(400).json({ success: false, error: 'License key must be 20 characters (XXXX-XXXX-XXXX-XXXX-XXXX)' });
    }

    const existing = licenses.get(storedKey);
    if (existing) {
        return res.status(409).json({
            success: false,
            error: 'License already exists — use PATCH /v1/admin/license/jar-hashes to append JAR hashes'
        });
    }

    const type = licenseType || 'monthly';
    const isLifetime = lifetime === true
        || durationDays === 0
        || type === 'lifetime_normal'
        || type === 'lifetime_premium';

    let expiresAt = null;
    if (!isLifetime) {
        const days = Number(durationDays);
        const effectiveDays = Number.isFinite(days) && days > 0 ? days : 30;
        expiresAt = new Date(Date.now() + effectiveDays * 24 * 60 * 60 * 1000).toISOString();
    }

    licenses.set(storedKey, normalizeLicenseRecord({
        username: username || null,
        licenseType: type,
        hwid: null,
        hwidChanges: 0,
        expiresAt,
        active: false,
        createdAt: new Date().toISOString(),
        allowedJarHashes: Array.isArray(jarHashes) ? jarHashes.filter(Boolean) : []
    }));
    saveLicenses();

    res.json({
        success: true,
        licenseKey: storedKey,
        licenseKeyDisplay: formatLicenseKeyDisplay(storedKey),
        licenseType: type,
        expiresAt,
        lifetime: isLifetime
    });
});

app.patch('/v1/admin/license/jar-hashes', (req, res) => {
    if (!requireAdmin(req, res)) return;
    const { licenseKey, jarHashes, append } = req.body || {};
    if (!licenseKey) {
        return res.status(400).json({ success: false, error: 'Missing licenseKey' });
    }
    const storedKey = normalizeLicenseKey(licenseKey);
    const licenseData = licenses.get(storedKey);
    if (!licenseData) {
        return res.status(404).json({ success: false, error: 'License not found' });
    }
    const incoming = Array.isArray(jarHashes)
        ? jarHashes.map((h) => normalizeJarHash(h)).filter((h) => h && /^[a-f0-9]{64}$/.test(h))
        : [];
    if (incoming.length === 0) {
        return res.status(400).json({ success: false, error: 'jarHashes must include at least one valid SHA-256 hex' });
    }
    const shouldAppend = append !== false;
    if (shouldAppend) {
        const merged = new Set([...(licenseData.allowedJarHashes || []), ...incoming]);
        licenseData.allowedJarHashes = [...merged];
    } else {
        licenseData.allowedJarHashes = incoming;
    }
    saveLicenses();
    res.json({
        success: true,
        licenseKey: storedKey,
        allowedJarHashes: licenseData.allowedJarHashes
    });
});

app.post('/v1/admin/jar-hash', (req, res) => {
    if (!requireAdmin(req, res)) return;
    const { jarSha256, jarHash } = req.body || {};
    try {
        const result = registerManualJarHash(jarSha256 || jarHash);
        console.log('[Auth] Manual JAR hash registered:', result.jarSha256.slice(0, 16) + '…');
        res.json({ success: true, ...result, totalGlobalHashes: registeredJarHashes.size });
    } catch (error) {
        res.status(400).json({ success: false, error: error.message });
    }
});

app.get('/v1/admin/licenses', (req, res) => {
    if (!requireAdmin(req, res)) return;

    const list = [];
    for (const [key, data] of licenses) {
        list.push({ licenseKey: key, ...data });
    }

    res.json({ licenses: list });
});

// Website-to-auth synchronization. This is deliberately loopback-only and
// uses a dedicated token rather than the wider admin credential.
app.post('/v1/internal/license-sync', (req, res) => {
    if (!requireWebsiteSync(req, res)) return;

    const body = req.body || {};
    const operation = String(body.operation || '').trim().toLowerCase();
    const storedKey = normalizeLicenseKey(body.licenseKey);
    if (storedKey.length !== 20) {
        return res.status(400).json({ success: false, error: 'Invalid license key' });
    }

    const existingKey = resolveLicenseKey(storedKey);
    if (operation === 'remove') {
        if (existingKey) {
            licenses.delete(existingKey);
            revokeSessionsForLicense(existingKey);
            if (!saveLicenses()) {
                return res.status(500).json({ success: false, error: 'License persistence failed' });
            }
        }
        return res.json({ success: true, removed: Boolean(existingKey) });
    }

    if (operation !== 'upsert' && operation !== 'expiry') {
        return res.status(400).json({ success: false, error: 'Unsupported operation' });
    }

    if (body.expiresAt && Number.isNaN(Date.parse(body.expiresAt))) {
        return res.status(400).json({ success: false, error: 'Invalid expiry' });
    }
    const expiresAt = body.expiresAt == null || body.expiresAt === ''
        ? null
        : new Date(body.expiresAt).toISOString();

    if (operation === 'expiry') {
        if (!existingKey) {
            return res.status(404).json({ success: false, error: 'License not found' });
        }
        licenses.get(existingKey).expiresAt = expiresAt;
        if (!saveLicenses()) {
            return res.status(500).json({ success: false, error: 'License persistence failed' });
        }
        return res.json({ success: true });
    }

    const allowedTypes = new Set([
        'lifetime_normal',
        'lifetime_premium',
        'normal-monthly',
        'premium-monthly',
        'custom'
    ]);
    const licenseType = String(body.licenseType || '').trim();
    if (!allowedTypes.has(licenseType)) {
        return res.status(400).json({ success: false, error: 'Invalid license type' });
    }

    const keyToStore = existingKey || storedKey;
    const current = existingKey ? licenses.get(existingKey) : {};
    const record = normalizeLicenseRecord({
        ...current,
        username: current.username || null,
        discordId: body.discordId == null ? (current.discordId || null) : String(body.discordId),
        licenseType,
        expiresAt,
        createdAt: current.createdAt || body.createdAt || new Date().toISOString(),
        hwid: current.hwid || null,
        hwidChanges: current.hwidChanges || 0,
        allowedJarHashes: Array.isArray(current.allowedJarHashes) ? current.allowedJarHashes : []
    });
    licenses.set(keyToStore, record);
    if (!saveLicenses()) {
        if (existingKey) licenses.set(existingKey, current);
        else licenses.delete(keyToStore);
        return res.status(500).json({ success: false, error: 'License persistence failed' });
    }
    return res.json({ success: true, licenseKey: keyToStore });
});

// ─── Split-Key Endpoints ─────────────────────────────────────────────────────
// Client retrieves server's key half after auth
// Requires valid session token - prevents unauthorized key access
app.get('/v1/keys/:keyId', (req, res) => {
    const keyRequestKey = `${clientIp(req)}:key-retrieval`;
    if (isRateLimited(keyRequestKey, CONFIG.KEY_RETRIEVAL_RATE_LIMIT)) {
        return res.status(429).json({ error: 'Too many key retrieval requests' });
    }
    const { keyId } = req.params;
    // Never accept bearer tokens in a URL: query strings leak into proxy logs.
    const sessionToken = req.headers['x-session-token'] || req.headers['authorization']?.replace(/^Bearer /i, '');
    
    if (!sessionToken) {
        return res.status(401).json({ error: 'Missing session token' });
    }
    
    const session = sessions.get(sessionToken);
    if (!session || sessionIsExpired(session)) {
        return res.status(401).json({ error: 'Invalid or expired session' });
    }
    
    const keyData = keyHalves.get(keyId);
    if (!keyData) {
        return res.status(404).json({ error: 'Key not found' });
    }
    
    const keyAccess = authorizeKeyAccess({
        production: IS_PRODUCTION,
        session,
        keyData,
        release: releaseRegistry.get(keyId)
    });
    if (!keyAccess.allowed) {
        console.log('[Auth] Key access denied for', session.username, ':', keyAccess.reason);
        punishCrack('❌ CRACK ATTEMPT (wrong license for key half)', {
            licenseKey: session.license, licenseData: licenses.get(session.license),
            rawHwid: session.rawHwid, normalizedHwid: session.hwid, req,
            mcUsername: session.mcUsername, mcUuid: session.mcUuid,
            osVersion: session.osVersion, discordUsername: session.discordUsername, discordId: session.discordId,
            windowsName: session.windowsName, pcName: session.pcName
        });
        return res.status(403).json({ error: 'Key is not authorized for this session' });
    }
    
    if (!sessionStillAuthorized(session, clientIp(req))) {
        return res.status(401).json({ error: 'Session revoked' });
    }

    // Encrypted with key the client can derive (matches request-signing material)
    const sessionKey = crypto.createHash('sha256').update(`${sessionToken}|dragonite`).digest();
    const iv = crypto.randomBytes(12);
    const encryptedHalf = crypto.createCipheriv('aes-256-gcm', sessionKey, iv);
    const encrypted = Buffer.concat([encryptedHalf.update(keyData.keyHalfB, 'hex'), encryptedHalf.final()]);
    const authTag = encryptedHalf.getAuthTag();

    res.json({
        keyId,
        keyHalfB: Buffer.concat([iv, encrypted, authTag]).toString('base64'),
        encrypted: true
    });
});

// Admin: Create a new key half
app.post('/v1/admin/key', (req, res) => {
    if (!requireAdmin(req, res)) return;
    const { keyId, keyHalfB, assignTo } = req.body || {};
    
    if (!keyId || !keyHalfB) {
        return res.status(400).json({ success: false, error: 'Missing keyId or keyHalfB' });
    }
    
    // Validate keyHalfB is valid hex and proper length (16 bytes = 32 hex chars for AES-256 split key)
    if (!/^[a-fA-F0-9]{32}$/.test(keyHalfB)) {
        return res.status(400).json({ success: false, error: 'keyHalfB must be 32 hex characters (16 bytes)' });
    }
    if (!/^build-[0-9]+$/.test(String(keyId))) {
        return res.status(400).json({ success: false, error: 'keyId must be build-<timestamp>' });
    }

    const existing = keyHalves.get(keyId);
    if (existing) {
        if (String(existing.keyHalfB).toLowerCase() !== String(keyHalfB).toLowerCase()) {
            return res.status(409).json({ success: false, error: 'A different key half already exists for this keyId' });
        }
        return res.json({ success: true, registered: false, keyId, assignedTo: existing.assignedTo || 'unassigned' });
    }
    
    keyHalves.set(keyId, {
        keyHalfB,
        createdAt: new Date().toISOString(),
        assignedTo: assignTo || null
    });
    saveKeyHalves();
    
    res.json({ success: true, keyId, assignedTo: assignTo || 'unassigned' });
});

// Admin: List all key halves
app.get('/v1/admin/keys', (req, res) => {
    if (!requireAdmin(req, res)) return;
    
    const list = [];
    for (const [keyId, data] of keyHalves) {
        list.push({ keyId, ...data });
    }
    
    res.json({ keys: list });
});

// Admin: Assign key to license
app.post('/v1/admin/key/assign', (req, res) => {
    if (!requireAdmin(req, res)) return;
    const { keyId, licenseKey } = req.body || {};
    
    const keyData = keyHalves.get(keyId);
    if (!keyData) {
        return res.status(404).json({ success: false, error: 'Key not found' });
    }
    
    if (licenseKey && !licenses.has(licenseKey)) {
        return res.status(404).json({ success: false, error: 'License not found' });
    }
    
    keyData.assignedTo = licenseKey || null;
    saveKeyHalves();
    
    res.json({ success: true, keyId, assignedTo: keyData.assignedTo });
});

// Release registration is deliberately separate from general admin actions.
// The canonical registry write persists the key half and allowed JAR hash as
// one logical release decision, and duplicate retries are safe.
app.post('/v1/release/register', (req, res) => {
    if (!requireReleaseRegistrar(req, res)) return;
    const candidate = req.body || {};
    const existingKey = keyHalves.get(candidate.keyId);
    if (existingKey && String(existingKey.keyHalfB).toLowerCase() !== String(candidate.keyHalfB || '').toLowerCase()) {
        return res.status(409).json({ success: false, error: 'A different key half already exists for this keyId' });
    }
    try {
        const result = releaseRegistry.register(candidate);
        const release = result.release;
        keyHalves.set(release.keyId, {
            keyHalfB: release.keyHalfB,
            createdAt: release.createdAtUtc,
            assignedTo: existingKey?.assignedTo || null
        });
        registeredJarHashes.add(release.jarSha256);

        // If the build pipeline sent a new ECDSA public key, apply it immediately.
        // This means auth/ECDSA key rotation is fully automatic — no VPS restart needed.
        if (typeof candidate.ecdsaPublicSpkiB64 === 'string' && candidate.ecdsaPublicSpkiB64.trim().length > 0) {
            const newKey = candidate.ecdsaPublicSpkiB64.replace(/[^A-Za-z0-9+/=]/g, '').trim();
            try {
                // Sanity-check: must be a valid EC key before applying
                const testKey = crypto.createPublicKey({
                    key: Buffer.from(newKey, 'base64'),
                    format: 'der',
                    type: 'spki'
                });
                if (testKey.asymmetricKeyType !== 'ec') throw new Error('Not an EC key');
                CONFIG.ECDSA_PUBLIC_KEY_SPKI_B64 = newKey;
                console.log('[Auth] ECDSA public key updated via release registration: ' + newKey.slice(0, 16) + '…');
            } catch (e) {
                console.error('[Auth] Ignoring invalid ecdsaPublicSpkiB64 in registration payload:', e.message);
            }
        }

        res.status(result.created ? 201 : 200).json({
            success: true,
            registered: result.created,
            buildId: release.buildId,
            keyId: release.keyId,
            jarSha256: release.jarSha256
        });
    } catch (error) {
        res.status(400).json({ success: false, error: error.message });
    }
});

app.post('/v1/release/verify', (req, res) => {
    if (!requireReleaseRegistrar(req, res)) return;
    const candidate = req.body || {};
    const release = releaseRegistry.get(String(candidate.buildId || '').trim());
    if (!release
            || release.keyId !== String(candidate.keyId || '').trim()
            || release.jarSha256 !== String(candidate.jarSha256 || '').trim().toLowerCase()) {
        return res.status(404).json({ success: false, registered: false, error: 'Release is not registered exactly as requested' });
    }
    res.json({ success: true, registered: true, buildId: release.buildId, keyId: release.keyId, jarSha256: release.jarSha256 });
});

// Load persisted licenses from disk now that all functions are defined
loadLicenses();
loadKeyHalves();
releaseRegistry.load();
loadManualJarHashes();
for (const release of releaseRegistry.releases.values()) {
    const existing = keyHalves.get(release.keyId);
    if (existing && String(existing.keyHalfB).toLowerCase() !== release.keyHalfB) {
        throw new Error(`Release registry conflicts with key_halves.json for ${release.keyId}`);
    }
    keyHalves.set(release.keyId, {
        keyHalfB: release.keyHalfB,
        createdAt: release.createdAtUtc,
        assignedTo: existing?.assignedTo || null
    });
    registeredJarHashes.add(release.jarSha256);
}
loadEventIds();
loadBlacklist();
loadLicenseFailureStrikes();

// Discord OAuth endpoints
app.get('/v1/auth/discord', (req, res) => {
    if (!CONFIG.DISCORD_CLIENT_ID || !CONFIG.DISCORD_CLIENT_SECRET) {
        return res.status(503).json({ error: 'Discord OAuth not configured' });
    }

    const state = crypto.randomBytes(24).toString('hex');
    oauthStates.set(state, Date.now() + 10 * 60 * 1000);
    
    const params = new URLSearchParams({
        client_id: CONFIG.DISCORD_CLIENT_ID,
        redirect_uri: CONFIG.DISCORD_REDIRECT_URI,
        response_type: 'code',
        scope: 'identify',
        state
    });
    
    res.redirect(`https://discord.com/api/oauth2/authorize?${params}`);
});

app.get('/v1/auth/discord/callback', async (req, res) => {
    const { code, error, state } = req.query;
    
    if (error) {
        return res.send(`<script>window.close();</script><body>Discord auth cancelled. You can close this window.</body>`);
    }
    
    if (!code) {
        return res.status(400).json({ error: 'No code provided' });
    }

    const stateKey = typeof state === 'string' ? state : '';
    const stateExp = oauthStates.get(stateKey);
    oauthStates.delete(stateKey);
    if (!stateExp || Date.now() > stateExp) {
        return res.status(400).send('Invalid or expired OAuth state. Close this window and try again.');
    }
    
    try {
        // Exchange code for token
        const tokenRes = await fetch('https://discord.com/api/oauth2/token', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({
                client_id: CONFIG.DISCORD_CLIENT_ID,
                client_secret: CONFIG.DISCORD_CLIENT_SECRET,
                grant_type: 'authorization_code',
                code,
                redirect_uri: CONFIG.DISCORD_REDIRECT_URI
            })
        });
        
        const tokenData = await tokenRes.json();
        
        if (!tokenData.access_token) {
            return res.status(400).json({ error: 'Failed to get access token' });
        }
        
        // Get user info
        const userRes = await fetch('https://discord.com/api/users/@me', {
            headers: { Authorization: `Bearer ${tokenData.access_token}` }
        });
        
        const userData = await userRes.json();
        const safeId = String(userData.id || '').replace(/[^\d]/g, '');
        const safeUser = String(userData.username || 'user')
            .replace(/\\/g, '\\\\')
            .replace(/'/g, "\\'")
            .replace(/</g, '')
            .replace(/>/g, '');
        const safeAvatar = String(userData.avatar || '')
            .replace(/[^\w-]/g, '');
        const origin = `https://${CONFIG.DOMAIN}`;

        res.send(`<!DOCTYPE html>
<html><head><title>Discord Auth</title></head>
<body>
<script>
(function () {
  var data = { type: 'discord-auth', discordId: '${safeId}', discordUsername: '${safeUser}', discordAvatar: '${safeAvatar}' };
  if (window.opener) {
    window.opener.postMessage(data, ${JSON.stringify(origin)});
  }
  window.close();
})();
</script>
<p>Discord authentication successful! You can close this window.</p>
</body></html>`);
    } catch (err) {
        console.error('[Discord OAuth] Error:', err);
        res.status(500).json({ error: 'Discord OAuth failed' });
    }
});

// ============================================================
// INTERNAL SYNC - GET LICENSE BY DISCORD (USED BY WEBSITE DB)
// ============================================================
app.post('/api/license/findByDiscord', (req, res) => {
    if (!requireWebsiteSync(req, res)) {
        console.warn('[Auth] Blocked unauthorized attempt on findByDiscord from', clientIp(req));
        return;
    }

    const { discordId, discordTag } = req.body;
    if (!discordId && !discordTag) return res.status(400).json({ error: 'Missing discord info' });

    const userLicenses = [];
    for (const [key, l] of licenses.entries()) {
        const discordVal = l.discord || l.discordId || l.ownerDiscordId;
        if (!discordVal) continue;
        const ld = String(discordVal).toLowerCase();
        if (ld === String(discordId).toLowerCase() || 
            (discordTag && ld === String(discordTag).toLowerCase()) ||
            (l.discordUsername && String(l.discordUsername).toLowerCase() === String(discordTag).toLowerCase()) ||
            (l.ownerDiscordUsername && String(l.ownerDiscordUsername).toLowerCase() === String(discordTag).toLowerCase())) {
            userLicenses.push({
                key: key,
                type: l.licenseType || l.type || 'normal',
                createdAt: l.createdAt || new Date().toISOString(),
                resetsUsed: l.resetsUsed || 0,
                hasHwid: !!l.hwid
            });
        }
    }

    if (userLicenses.length === 0) {
        return res.json({ found: false });
    }

    userLicenses.sort((a, b) => {
        if (a.type === 'premium' && b.type !== 'premium') return -1;
        if (a.type !== 'premium' && b.type === 'premium') return 1;
        return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
    });

    const bestLicense = userLicenses[0];
    
    res.json({
        found: true,
        key: bestLicense.key,
        type: bestLicense.type,
        createdAt: bestLicense.createdAt,
        resetsUsed: bestLicense.resetsUsed,
        hasHwid: !!bestLicense.hwid
    });
});

app.post('/v1/admin/test-ping', (req, res) => {
    if (!requireAdmin(req, res)) return;
    sendClientErrorAlert('Admin test ping from VPS — if you were notified, error alerts work.');
    res.json({ ok: true, message: 'Ping queued (check Discord + pm2 logs)' });
});

// Static Transcripts Endpoint
const TRANSCRIPTS_DIR = path.join(__dirname, 'transcripts');
if (!fs.existsSync(TRANSCRIPTS_DIR)) fs.mkdirSync(TRANSCRIPTS_DIR, { recursive: true });
app.post('/v1/configs/publish', (req, res) => {
    const { sessionToken, name, author, shareCode } = req.body || {};
    if (!sessionToken || !shareCode || typeof shareCode !== 'string') {
        return res.status(400).json({ ok: false, error: 'Missing sessionToken or shareCode' });
    }
    if (shareCode.length > 12000) {
        return res.status(413).json({ ok: false, error: 'Config too large' });
    }
    const session = sessions.get(sessionToken);
    if (!session || sessionIsExpired(session)) {
        return res.status(401).json({ ok: false, error: 'Invalid session', revoked: true });
    }
    const id = crypto.randomBytes(6).toString('hex');
    const entry = {
        id,
        name: String(name || 'Shared config').slice(0, 48),
        author: String(author || session.mcUsername || session.discordUsername || 'player').slice(0, 32),
        shareCode: shareCode.trim(),
        updated: Date.now(),
        downloads: 0
    };
    try {
        const storePath = path.join(__dirname, 'data', 'shared-configs.json');
        fs.mkdirSync(path.dirname(storePath), { recursive: true });
        let list = [];
        if (fs.existsSync(storePath)) {
            list = JSON.parse(fs.readFileSync(storePath, 'utf8'));
            if (!Array.isArray(list)) list = [];
        }
        list.unshift(entry);
        list = list.slice(0, 200);
        fs.writeFileSync(storePath, JSON.stringify(list, null, 2));
        return res.json({ ok: true, id: entry.id, name: entry.name });
    } catch (e) {
        console.error('[Auth] config publish failed', e);
        return res.status(500).json({ ok: false, error: 'Failed to publish config' });
    }
});

app.post('/v1/configs/list', (req, res) => {
    const { sessionToken } = req.body || {};
    if (!sessionToken) {
        return res.status(400).json({ ok: false, error: 'Missing sessionToken' });
    }
    const session = sessions.get(sessionToken);
    if (!session || sessionIsExpired(session)) {
        return res.status(401).json({ ok: false, error: 'Invalid session', revoked: true });
    }
    try {
        const storePath = path.join(__dirname, 'data', 'shared-configs.json');
        let list = [];
        if (fs.existsSync(storePath)) {
            list = JSON.parse(fs.readFileSync(storePath, 'utf8'));
            if (!Array.isArray(list)) list = [];
        }
        return res.json({
            ok: true,
            configs: list.slice(0, 40).map(c => ({
                id: c.id,
                name: c.name,
                author: c.author,
                updated: c.updated,
                downloads: c.downloads || 0,
                shareCode: c.shareCode
            }))
        });
    } catch (e) {
        return res.status(500).json({ ok: false, error: 'Failed to list configs' });
    }
});

app.use('/transcripts', express.static(TRANSCRIPTS_DIR));

// Landing page — nginx proxies / to this app; without static, homepage was blank.
const PUBLIC_DIR = path.join(__dirname, 'public');
app.use(express.static(PUBLIC_DIR, { index: 'index.html', maxAge: IS_PRODUCTION ? '1h' : 0 }));
app.get('/', (req, res) => {
    res.sendFile(path.join(PUBLIC_DIR, 'index.html'));
});

if (IS_PRODUCTION) {
    if (!process.env.SERVER_SECRET || process.env.SERVER_SECRET.length < 32) {
        console.error('[Auth] FATAL: SERVER_SECRET must be set (32+ chars) in production');
        process.exit(1);
    }
    if (!CONFIG.ADMIN_KEY) {
        console.error('[Auth] FATAL: ADMIN_KEY must be set in production');
        process.exit(1);
    }
    if (!CONFIG.RELEASE_REGISTRATION_TOKEN || CONFIG.RELEASE_REGISTRATION_TOKEN.length < 32) {
        console.error('[Auth] FATAL: RELEASE_REGISTRATION_TOKEN must be set to a 32+ character value in production');
        process.exit(1);
    }
    if (!process.env.WEBSITE_SYNC_TOKEN || process.env.WEBSITE_SYNC_TOKEN.trim().length < 32) {
        console.error('[Auth] FATAL: WEBSITE_SYNC_TOKEN must be set to a 32+ character value in production');
        process.exit(1);
    }
    if (!getAuthResponseSigningKey()) {
        console.error('[Auth] FATAL: AUTH_RESPONSE_ED25519_PRIVATE_PKCS8_B64 must be a valid Ed25519 PKCS#8 key in production');
        process.exit(1);
    }
    if (!CONFIG.ECDSA_PUBLIC_KEY_SPKI_B64) {
        console.error('[Auth] FATAL: ECDSA_PUBLIC_KEY_SPKI_B64 must be set in production '
            + 'for nonce-bound login and autologin proofs.');
        process.exit(1);
    }
    const enforceJarHash = process.env.ENFORCE_JAR_HASH !== 'false';
    if (enforceJarHash && !jarHashEnforcementEnabled()) {
        console.warn('[Auth] Release provisioning lock: no JAR hash is registered yet. '
            + 'Only authenticated release registration is available until the first build is registered.');
    }
    if (!enforceJarHash) {
        console.warn('[Auth] WARNING: JAR hash enforcement explicitly disabled. This should never be true in production.');
    }
}

app.listen(CONFIG.PORT, () => {
    console.log('========================================');
    console.log('       Dragonite Auth Server v1.4.1     ');
    console.log('========================================');
    console.log();
    console.log(`Port: ${CONFIG.PORT}`);
    const wh = resolveDiscordWebhook();
    console.log(wh
        ? `Webhook alerts: Configured (webhook id ${discordWebhookId(wh)} — from .env)`
        : 'Webhook alerts: Disabled (set DISCORD_WEBHOOK to https://discord.com/api/webhooks/...)');
    const pingRole = (process.env.DISCORD_PING_ROLE_ID || '').trim();
    const alertCh = (process.env.DISCORD_ALERT_CHANNEL_ID || '').trim();
    const botTok = (process.env.DISCORD_TOKEN || '').trim();
    const bridgePort = process.env.ALERT_BRIDGE_PORT || '8001';
    if (alertCh && (process.env.INTERNAL_ALERT_SECRET || CONFIG.ADMIN_KEY)) {
        const pingMode = pingRole ? `role <@&${pingRole}>` : '@everyone on errors';
        console.log(`Discord alerts: dragonite-bot → channel ${alertCh} (port ${bridgePort}, ${pingMode})`);
        console.log('Webhook fallback: ' + (process.env.DISCORD_ALERT_USE_WEBHOOK === 'true' ? 'enabled' : 'disabled'));
    } else {
        console.log('Discord alerts: set DISCORD_ALERT_CHANNEL_ID + restart dragonite-bot');
    }
    console.log(`Discord OAuth: ${CONFIG.DISCORD_CLIENT_ID ? 'Configured' : 'Disabled'}`);
    console.log(releaseProvisioningMode()
        ? 'JAR SHA allowlist: provisioning lock (register the first release)'
        : jarHashEnforcementEnabled()
        ? `JAR SHA allowlist: ${CONFIG.EXPECTED_JAR_HASHES.size + registeredJarHashes.size} global hash(es)`
        : 'JAR SHA allowlist: disabled (set ENFORCE_JAR_HASH=true in production)');
    if (ecdsaSpkiB64Clean()) {
        try {
            const key = crypto.createPublicKey({
                key: Buffer.from(ecdsaSpkiB64Clean(), 'base64'),
                format: 'der',
                type: 'spki'
            });
            console.log(`ECDSA verify key: configured (type=${key.asymmetricKeyType}, fingerprint=${ecdsaSpkiFingerprint()})`);
        } catch (e) {
            console.log(`ECDSA verify key: INVALID in .env - ${e.message}`);
        }
    } else {
        console.log('ECDSA verify key: MISSING - set ECDSA_PUBLIC_KEY_SPKI_B64 from native/master_ecdsa_spki.b64');
    }
    console.log(DISABLE_AUTO_BLACKLIST
        ? 'Auto-blacklist: DISABLED (testing mode)'
        : 'Auto-blacklist: enabled');
    console.log(`Admin key: ${CONFIG.ADMIN_KEY ? 'Configured' : 'Missing'}`);
    console.log(`Key halves: ${keyHalves.size} loaded`);
    console.log(`Registered releases: ${releaseRegistry.releases.size}`);
    console.log(`Event IDs: ${eventIds.size} tracked`);
    console.log(`Blacklist: ${blacklist.size} entries`);
    console.log();
    console.log('Endpoints:');
    console.log('  POST /v1/auth/hwid          (HWID-first auth)');
    console.log('  GET  /v1/auth/discord       (Discord OAuth)');
    console.log('  GET  /v1/auth/discord/callback');
    console.log('  GET  /v1/challenge');
    console.log('  POST /v1/auth');
    console.log('  POST /v1/heartbeat');
    console.log('  POST /v1/logout');
    console.log('  POST /v1/client-error       (startup/auth errors → @everyone webhook)');
    console.log('  POST /v1/security-report    (in-session errors → @everyone webhook)');
    console.log('  POST /v1/verify');
    console.log('  GET  /v1/keys/:keyId         (split-key retrieval)');
    console.log('  POST /v1/admin/license');
    console.log('  PATCH /v1/admin/license/jar-hashes');
    console.log('  POST /v1/admin/jar-hash');
    console.log('  GET  /v1/admin/licenses');
    console.log('  POST /v1/admin/key           (create key half)');
    console.log('  GET  /v1/admin/keys');
    console.log('  POST /v1/admin/key/assign');
    console.log();
    console.log('========================================');
});

setInterval(() => {
    if (PERSIST_SESSIONS) sessionStore.purgeExpired(sessions);
    const nowMs = Date.now();
    for (const [state, exp] of oauthStates) {
        if (exp < nowMs) oauthStates.delete(state);
    }
}, 60 * 60 * 1000);
