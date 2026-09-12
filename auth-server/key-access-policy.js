'use strict';

function normalizeSha256(value) {
    const normalized = String(value || '').trim().toLowerCase();
    return /^[a-f0-9]{64}$/.test(normalized) ? normalized : null;
}

/**
 * Authorizes a key-half retrieval request.
 *
 * The client may send one of two jar hashes depending on which resolution path
 * succeeds at runtime:
 *   - Whole-file SHA-256  (jarSha256):        the registered artifact hash
 *   - Class-integrity SHA-256 (altJarSha256): hash of class bytes excluding
 *     BuildFingerprint.class, stored during registration as the fallback.
 *
 * Both are accepted so that Knot/Modrinth environments where
 * getProtectionDomain().getCodeSource() fails still work correctly.
 */
function authorizeKeyAccess({ production = false, session, keyData, release }) {
    if (!session || !keyData) {
        return { allowed: false, reason: 'missing_context' };
    }
    if (keyData.assignedTo && keyData.assignedTo !== session.license) {
        return { allowed: false, reason: 'wrong_license' };
    }
    if (!release) {
        return production
            ? { allowed: false, reason: 'unregistered_release' }
            : { allowed: true, reason: 'legacy_development_key' };
    }

    const sessionHash  = normalizeSha256(session.jarHash);
    const releaseHash  = normalizeSha256(release.jarSha256);
    const altHash      = normalizeSha256(release.altJarSha256); // class-integrity hash

    if (!releaseHash) {
        return { allowed: false, reason: 'unregistered_release' };
    }

    // If the session carries no jar hash (old client or hash resolution failed),
    // allow in dev / warn in production but don't hard-block — the HWID check
    // already validated the machine.
    if (!sessionHash) {
        return production
            ? { allowed: false, reason: 'missing_jar_hash' }
            : { allowed: true,  reason: 'development_no_jar_hash' };
    }

    // Accept either the whole-file artifact hash OR the class-integrity hash.
    if (sessionHash !== releaseHash && sessionHash !== altHash) {
        return { allowed: false, reason: 'wrong_release' };
    }
    return { allowed: true, reason: 'authorized' };
}

module.exports = { authorizeKeyAccess, normalizeSha256 };
