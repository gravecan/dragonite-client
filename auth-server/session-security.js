'use strict';

function parseTimestamp(value) {
    if (value == null || value === '') return null;
    const millis = Date.parse(String(value));
    return Number.isFinite(millis) ? millis : Number.NaN;
}

function sessionIsExpired(session, nowMs = Date.now()) {
    if (!session || !session.expiresAt) return true;
    const expiry = parseTimestamp(session.expiresAt);
    return !Number.isFinite(expiry) || expiry <= nowMs;
}

function licenseIsExpired(licenseData, nowMs = Date.now()) {
    if (!licenseData) return true;
    if (!licenseData.expiresAt) return false;
    const expiry = parseTimestamp(licenseData.expiresAt);
    return !Number.isFinite(expiry) || expiry <= nowMs;
}

module.exports = { sessionIsExpired, licenseIsExpired };
