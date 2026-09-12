'use strict';

const crypto = require('crypto');

const AUTH_PROOF_INFO = Buffer.from('cloth-auth-proof-v2', 'utf8');

function mixIkmBytes(raw32) {
    if (!raw32 || raw32.length < 32) return null;
    const out = Buffer.from(raw32.subarray(0, 32));
    for (let i = 0; i < 32; i++) {
        out[i] ^= (0x5a ^ i) & 0xff;
    }
    return out;
}

function loadMixedIkmFromHex(ikmHex) {
    if (typeof ikmHex !== 'string' || !/^[a-f0-9]{64}$/i.test(ikmHex.trim())) {
        return null;
    }
    return mixIkmBytes(Buffer.from(ikmHex.trim(), 'hex'));
}

/** Matches client {@code JavaNativeDerivation.hkdfSha256}. */
function hkdfSha256(ikm, salt, info, outLen) {
    const prk = crypto.createHmac('sha256', ikm).update(salt && salt.length ? salt : Buffer.alloc(32)).digest();
    const out = Buffer.alloc(outLen);
    let offset = 0;
    let t = Buffer.alloc(0);
    let counter = 1;
    while (offset < outLen) {
        const h = crypto.createHmac('sha256', prk);
        if (t.length) h.update(t);
        if (info && info.length) h.update(info);
        h.update(Buffer.from([counter]));
        t = h.digest();
        const copy = Math.min(32, outLen - offset);
        t.copy(out, offset, 0, copy);
        offset += copy;
        counter += 1;
    }
    return out;
}

function normalizeLicenseKey(key) {
    if (!key) return '';
    return String(key).replace(/[^A-Za-z0-9]/g, '').toUpperCase();
}

function deriveAuthProofKey(ikmHex, license) {
    const ikm = loadMixedIkmFromHex(ikmHex);
    if (!ikm) return null;
    const norm = normalizeLicenseKey(license);
    if (!norm) return null;
    return hkdfSha256(ikm, Buffer.from(norm, 'utf8'), AUTH_PROOF_INFO, 32);
}

function verifyLicenseBoundHmac2(payload, proofB64, ikmHex, license) {
    if (!payload || !proofB64 || !license) return false;
    const key = deriveAuthProofKey(ikmHex, license);
    if (!key) return false;
    const expected = crypto.createHmac('sha256', key).update(payload, 'utf8').digest();
    let actual;
    try {
        actual = Buffer.from(proofB64, 'base64');
    } catch {
        return false;
    }
    if (actual.length !== expected.length) return false;
    return crypto.timingSafeEqual(actual, expected);
}

/** Dedicated verifier for the HWID auto-login protocol boundary. */
function verifyHwidNonceBoundHmac2(nonce, timestamp, hwid, proof, jarHash, ikmHex, marker) {
    if (typeof proof !== 'string' || !proof.startsWith('hmac2:')) return false;
    if (!nonce || timestamp == null || !hwid || !marker) return false;
    const ts = typeof timestamp === 'string'
        ? timestamp.trim()
        : (typeof timestamp === 'number' && Number.isFinite(timestamp)
            ? String(Math.trunc(timestamp)) : String(timestamp));
    const normalizedJar = jarHash == null ? '' : String(jarHash).trim().toLowerCase();
    const payload = `${nonce}|${ts}|${hwid}|${marker}|${normalizedJar}`;
    return verifyLicenseBoundHmac2(
        payload, proof.slice('hmac2:'.length), ikmHex, marker);
}

function shortFingerprint(value) {
    return crypto.createHash('sha256').update(value || Buffer.alloc(0)).digest('hex').slice(0, 16);
}

/** Redacted mismatch evidence only. Never return raw payload, proof, key, license, HWID, or IKM. */
function diagnoseLicenseBoundHmac2(payload, proofB64, ikmHex, license) {
    const key = deriveAuthProofKey(ikmHex, license);
    let actual = Buffer.alloc(0);
    try {
        actual = Buffer.from(String(proofB64 || ''), 'base64');
    } catch {}
    const expected = key && payload
        ? crypto.createHmac('sha256', key).update(payload, 'utf8').digest()
        : Buffer.alloc(0);
    const rawIkm = /^[a-f0-9]{64}$/i.test(String(ikmHex || '').trim())
        ? Buffer.from(String(ikmHex).trim(), 'hex') : Buffer.alloc(0);
    return {
        payloadFp: shortFingerprint(Buffer.from(String(payload || ''), 'utf8')),
        actualProofFp: shortFingerprint(actual),
        expectedProofFp: shortFingerprint(expected),
        ikmFp: shortFingerprint(rawIkm)
    };
}

module.exports = {
    mixIkmBytes,
    loadMixedIkmFromHex,
    hkdfSha256,
    deriveAuthProofKey,
    verifyLicenseBoundHmac2,
    verifyHwidNonceBoundHmac2,
    diagnoseLicenseBoundHmac2,
    normalizeLicenseKey
};
