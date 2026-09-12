/**
 * Cascade blacklist removal: one identifier (UUID, license, etc.) clears every
 * ban key for the same person (HWID hash, IP, Discord, license, …).
 */
const crypto = require('crypto');

const HWID_HEX_RE = /^[a-f0-9]{64}$/i;

function normalizeLicenseKey(key) {
    if (!key) return '';
    return String(key).replace(/[^A-Za-z0-9]/g, '').toUpperCase();
}

function normalizeMcUuid(uuid) {
    if (!uuid) return '';
    return String(uuid).replace(/-/g, '').toLowerCase();
}

function normalizeHwidForBlacklist(value) {
    const v = String(value || '').trim();
    if (!v) return '';
    if (HWID_HEX_RE.test(v)) return v.toLowerCase();
    return crypto.createHash('sha256').update(v).digest('hex');
}

function normalizeBlacklistValue(type, value) {
    if (!value) return '';
    let v = String(value).trim();
    if (type === 'license') return normalizeLicenseKey(v);
    if (type === 'hwid') return normalizeHwidForBlacklist(v);
    if (type === 'mc_username' || type === 'discord_username' || type === 'windows_name' || type === 'pc_name') {
        return v.toLowerCase();
    }
    if (type === 'discord_id') return v.replace(/[<@!>]/g, '');
    if (type === 'ip') return v.replace(/^::ffff:/i, '');
    if (type === 'mc_uuid') return v;
    return v;
}

function addIp(subject, ip) {
    if (!ip || ip === 'unknown') return;
    const s = String(ip).trim();
    subject.add(s);
    const norm = s.replace(/^::ffff:/i, '');
    if (norm && norm !== s) subject.add(norm);
}

/** All blacklist map keys that identify this license row. */
function addLicenseIdentifiers(subject, licKey, data) {
    if (!data) return;
    if (licKey) {
        subject.add(licKey);
        subject.add(normalizeLicenseKey(licKey));
    }
    if (data.hwid) {
        subject.add(data.hwid);
        if (!HWID_HEX_RE.test(data.hwid)) {
            subject.add(normalizeHwidForBlacklist(data.hwid));
        }
    }
    if (data.mcUsername) subject.add(String(data.mcUsername).toLowerCase());
    if (data.mcUuid) {
        subject.add(data.mcUuid);
        subject.add(normalizeMcUuid(data.mcUuid));
    }
    if (data.discordId) subject.add(String(data.discordId));
    if (data.discordUsername) subject.add(String(data.discordUsername).toLowerCase());
    addIp(subject, data.ip);
    addIp(subject, data.lastIp);
    const COMMON_NAMES = new Set(['pc', 'desktop', 'admin', 'user', 'windows', 'owner', 'client', 'laptop', 'work', 'home', 'player', 'administrator', 'guest', 'comp', 'computer']);

    if (data.windowsName) {
        const wn = String(data.windowsName).toLowerCase().trim();
        if (wn && !COMMON_NAMES.has(wn)) subject.add(wn);
    }
    if (data.pcName) {
        const pn = String(data.pcName).toLowerCase().trim();
        if (pn && !COMMON_NAMES.has(pn)) subject.add(pn);
    }

    // Owner fields
    if (data.ownerHwid) {
        subject.add(data.ownerHwid);
        if (!HWID_HEX_RE.test(data.ownerHwid)) {
            subject.add(normalizeHwidForBlacklist(data.ownerHwid));
        }
    }
    if (data.ownerMcUsername) subject.add(String(data.ownerMcUsername).toLowerCase());
    if (data.ownerMcUuid) {
        subject.add(data.ownerMcUuid);
        subject.add(normalizeMcUuid(data.ownerMcUuid));
    }
    if (data.ownerDiscordId) subject.add(String(data.ownerDiscordId));
    if (data.ownerDiscordUsername) subject.add(String(data.ownerDiscordUsername).toLowerCase());
    addIp(subject, data.ownerIp);
    if (data.ownerWindowsName) {
        const own = String(data.ownerWindowsName).toLowerCase().trim();
        if (own && !COMMON_NAMES.has(own)) subject.add(own);
    }
    if (data.ownerPcName) {
        const opn = String(data.ownerPcName).toLowerCase().trim();
        if (opn && !COMMON_NAMES.has(opn)) subject.add(opn);
    }

    // History arrays
    if (Array.isArray(data.pastHwids)) {
        for (const h of data.pastHwids) {
            if (h) {
                subject.add(h);
                if (!HWID_HEX_RE.test(h)) subject.add(normalizeHwidForBlacklist(h));
            }
        }
    }
    if (Array.isArray(data.pastMcUsernames)) {
        for (const u of data.pastMcUsernames) {
            if (u) subject.add(String(u).toLowerCase());
        }
    }
    if (Array.isArray(data.pastMcUuids)) {
        for (const u of data.pastMcUuids) {
            if (u) {
                subject.add(u);
                subject.add(normalizeMcUuid(u));
            }
        }
    }
    if (Array.isArray(data.pastDiscordIds)) {
        for (const id of data.pastDiscordIds) {
            if (id) subject.add(String(id));
        }
    }
    if (Array.isArray(data.pastDiscordUsernames)) {
        for (const u of data.pastDiscordUsernames) {
            if (u) subject.add(String(u).toLowerCase());
        }
    }
    if (Array.isArray(data.pastIps)) {
        for (const ip of data.pastIps) {
            addIp(subject, ip);
        }
    }
    if (Array.isArray(data.pastWindowsNames)) {
        for (const w of data.pastWindowsNames) {
            if (w) {
                const pwn = String(w).toLowerCase().trim();
                if (pwn && !COMMON_NAMES.has(pwn)) subject.add(pwn);
            }
        }
    }
    if (Array.isArray(data.pastPcNames)) {
        for (const p of data.pastPcNames) {
            if (p) {
                const ppn = String(p).toLowerCase().trim();
                if (ppn && !COMMON_NAMES.has(ppn)) subject.add(ppn);
            }
        }
    }
}

function licenseRowMatchesAnchor(licKey, data, type, anchor, rawValue) {
    if (!data) return false;
    const ids = new Set();
    addLicenseIdentifiers(ids, licKey, data);
    
    if (type === 'license') {
        return ids.has(anchor) || ids.has(normalizeLicenseKey(licKey));
    }
    if (type === 'mc_uuid') {
        const u = normalizeMcUuid(rawValue || anchor);
        return ids.has(u) || ids.has(rawValue) || ids.has(anchor);
    }
    if (type === 'hwid') {
        const h = normalizeHwidForBlacklist(rawValue || anchor);
        return ids.has(h) || ids.has(rawValue) || ids.has(anchor);
    }
    if (type === 'ip') {
        const ipClean = String(rawValue || anchor).replace(/^::ffff:/i, '');
        return ids.has(ipClean) || ids.has(rawValue) || ids.has(anchor);
    }
    return ids.has(anchor) || (rawValue && ids.has(String(rawValue).toLowerCase()));
}

/**
 * All blacklist keys for one person (from licenses.json + anchor value).
 * Used for both cascade add and cascade remove.
 */
function collectCascadeSubjectKeys(type, rawValue, licenses) {
    const anchor = normalizeBlacklistValue(type, rawValue);
    const subject = new Set();
    if (!anchor) return [];

    subject.add(anchor);
    if (type === 'mc_uuid') {
        subject.add(normalizeMcUuid(rawValue || anchor));
    }
    if (type === 'hwid' && rawValue && !HWID_HEX_RE.test(String(rawValue).trim())) {
        subject.add(normalizeHwidForBlacklist(rawValue));
    }
    if (type === 'ip' && rawValue) {
        addIp(subject, rawValue);
    }

    for (const [licKey, data] of licenses) {
        if (licenseRowMatchesAnchor(licKey, data, type, anchor, rawValue)) {
            addLicenseIdentifiers(subject, licKey, data);
        }
    }

    for (const [licKey, data] of licenses) {
        const probe = new Set();
        addLicenseIdentifiers(probe, licKey, data);
        for (const id of probe) {
            if (subject.has(id)) {
                addLicenseIdentifiers(subject, licKey, data);
                break;
            }
        }
    }

    return [...new Set([...subject].filter(Boolean))];
}

/**
 * Blacklist every identifier we know for this person (manual /admin add).
 */
function applyCascadeBlacklistAdd(type, rawValue, licenses, blacklist, meta) {
    const keys = collectCascadeSubjectKeys(type, rawValue, licenses);
    if (keys.length === 0) return { keys: [], added: 0 };

    const banGroup = crypto.randomUUID();
    const createdAt = new Date().toISOString();
    const { reason, typeLabel, rawValue: raw } = meta;
    let added = 0;

    for (const key of keys) {
        blacklist.set(key, {
            reason,
            type,
            typeLabel,
            rawValue: raw,
            manual: true,
            banGroup,
            createdAt,
            target: key
        });
        added++;
    }
    return { keys, added, banGroup };
}

/**
 * Collect every blacklist key to delete for this person.
 * @param {string} type - blacklist type (mc_uuid, license, …)
 * @param {string} rawValue - user-provided value
 * @param {Map} licenses
 * @param {Map} blacklist
 * @returns {string[]}
 */
function collectCascadeRemoveKeys(type, rawValue, licenses, blacklist) {
    const subject = new Set(collectCascadeSubjectKeys(type, rawValue, licenses));
    if (subject.size === 0) return [];

    const banGroups = new Set();
    for (const [key, entry] of blacklist) {
        if (subject.has(key) || subject.has(String(key).toLowerCase())) {
            if (entry && entry.banGroup) banGroups.add(entry.banGroup);
        }
    }
    for (const [key, entry] of blacklist) {
        if (entry && entry.banGroup && banGroups.has(entry.banGroup)) {
            subject.add(key);
        }
    }

    const toRemove = [];
    for (const key of blacklist.keys()) {
        if (subject.has(key) || subject.has(String(key).toLowerCase())) {
            toRemove.push(key);
        }
    }
    return [...new Set(toRemove)];
}

module.exports = {
    normalizeBlacklistValue,
    normalizeHwidForBlacklist,
    normalizeLicenseKey,
    collectCascadeSubjectKeys,
    applyCascadeBlacklistAdd,
    collectCascadeRemoveKeys
};
