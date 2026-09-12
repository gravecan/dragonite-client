'use strict';

const crypto = require('crypto');

/** @param {string} text @param {{ redactLicenseKeys?: boolean }} [options] */
function sanitizeWebhookUserField(text, options = {}) {
    const redactLicenseKeys = options.redactLicenseKeys !== false;
    let s = String(text || '').replace(/\0/g, '').trim();
    s = s.replace(/@everyone|@here/gi, '[mention]');
    s = s.replace(/<@[!&]?\d+>/g, '[user]');
    s = s.replace(/@/g, '@\u200b');
    if (redactLicenseKeys) {
        s = s.replace(/\b[A-Z0-9]{4}(?:-[A-Z0-9]{4}){4}\b/g, '[license]');
    }
    s = s.replace(/\b[A-Za-z]:\\[^\r\n]{1,300}/g, '[local-path]');
    s = s.replace(/\/(?:home|Users)\/[^\r\n]{1,300}/g, '[local-path]');
    if (s.length > 500) s = s.slice(0, 497) + '...';
    return s;
}

function sanitizeWebhookContent(text) {
    const lines = String(text || '').split('\n');
    const sanitized = lines.map((line) => {
        // Operator identity lines are projected in full to the private Discord alert
        // channel. Mentions/paths are still stripped; accidental license paste in
        // unrelated fields (MC, OS, etc.) remains redacted below.
        if (/^(?:License|LicenseKey):\s*(.+)$/i.test(line)
                || /^(?:HWID|Stored HWID(?: SHA-256)?):\s*(.+)$/i.test(line)
                || /^(License fp|HWID fp):\s/.test(line)) {
            return sanitizeWebhookUserField(line, { redactLicenseKeys: false });
        }
        return sanitizeWebhookUserField(line);
    });
    let s = sanitized.join('\n');
    if (s.length > 2000) s = s.slice(0, 1997) + '...';
    return s;
}

function sensitiveFingerprint(value) {
    const normalized = String(value || '').trim();
    if (!normalized) return 'unknown';
    return `sha256:${crypto.createHash('sha256').update(normalized).digest('hex').slice(0, 12)}`;
}

class AlertDeduplicator {
    constructor(windowMs = 30_000) {
        this.windowMs = Math.max(1, Number(windowMs) || 30_000);
        this.seen = new Map();
    }

    accept(message, now = Date.now()) {
        const key = crypto.createHash('sha256').update(String(message || '')).digest('hex');
        const previous = this.seen.get(key) || 0;
        if (now - previous < this.windowMs) return false;
        this.seen.set(key, now);
        for (const [candidate, timestamp] of this.seen) {
            if (now - timestamp >= this.windowMs) this.seen.delete(candidate);
        }
        return true;
    }
}

module.exports = {
    sanitizeWebhookUserField,
    sanitizeWebhookContent,
    sensitiveFingerprint,
    AlertDeduplicator
};
