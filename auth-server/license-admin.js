/**
 * License keys (purchased slots) vs license IDs (activation periods on a key).
 * Admin history: hwid_reset, clear, create.
 */
const crypto = require('crypto');
const {
    normalizeBlacklistValue,
    normalizeLicenseKey,
    normalizeHwidForBlacklist
} = require('./blacklist-cascade');

const LICENSE_META_FILE = 'license_meta.json';

const LICENSE_QUERY_TYPES = {
    license: 'License Key',
    license_id: 'License ID',
    ip: 'IP Address',
    mc_username: 'Minecraft Username',
    mc_uuid: 'Minecraft UUID',
    discord_id: 'Discord User ID',
    discord_username: 'Discord Username',
    hwid: 'HWID (hash)',
    windows_name: 'Windows Username',
    pc_name: 'PC Name'
};

function normalizeMcUuid(uuid) {
    if (!uuid) return '';
    return String(uuid).replace(/-/g, '').toLowerCase();
}

function normalizeLicenseQueryValue(type, value) {
    if (type === 'license_id') {
        const n = parseInt(String(value || '').trim(), 10);
        return Number.isFinite(n) && n > 0 ? String(n) : '';
    }
    return normalizeBlacklistValue(type, value);
}

function bindingSnapshot(data) {
    if (!data) return {};
    return {
        hwid: data.hwid || null,
        hwidChanges: data.hwidChanges ?? 0,
        mcUsername: data.mcUsername || null,
        mcUuid: data.mcUuid || null,
        discordId: data.discordId || null,
        discordUsername: data.discordUsername || null,
        ip: data.ip || null,
        lastIp: data.lastIp || null,
        windowsName: data.windowsName || null,
        pcName: data.pcName || null,
        osVersion: data.osVersion || null,
        active: !!data.active
    };
}

function ensureLicenseShape(data) {
    if (!data || typeof data !== 'object') return data;
    if (!data.stats || typeof data.stats !== 'object') {
        data.stats = { hwidResets: 0, clears: 0 };
    }
    if (data.stats.hwidResets == null) data.stats.hwidResets = 0;
    if (data.stats.clears == null) data.stats.clears = 0;
    if (!Array.isArray(data.licenseIdHistory)) data.licenseIdHistory = [];
    if (!Array.isArray(data.adminHistory)) data.adminHistory = [];
    if (data.currentLicenseId == null) data.currentLicenseId = null;
    return data;
}

function loadLicenseMeta(metaPath) {
    const fs = require('fs');
    const path = require('path');
    const file = metaPath || path.join(__dirname, LICENSE_META_FILE);
    try {
        if (!fs.existsSync(file)) return { nextLicenseId: 1 };
        const parsed = JSON.parse(fs.readFileSync(file, 'utf8'));
        const next = parseInt(parsed.nextLicenseId, 10);
        return { nextLicenseId: Number.isFinite(next) && next > 0 ? next : 1 };
    } catch {
        return { nextLicenseId: 1 };
    }
}

function saveLicenseMeta(meta, metaPath) {
    const fs = require('fs');
    const path = require('path');
    const file = metaPath || path.join(__dirname, LICENSE_META_FILE);
    fs.writeFileSync(file, JSON.stringify(meta, null, 2), 'utf8');
}

function allocateLicenseId(meta) {
    const id = meta.nextLicenseId;
    meta.nextLicenseId = id + 1;
    return id;
}

/** Assign global license IDs to legacy keys by createdAt (oldest = lowest id). */
function migrateLicenseIds(licenses, meta) {
    const needs = [];
    for (const [key, data] of licenses) {
        ensureLicenseShape(data);
        if (data.currentLicenseId == null) {
            needs.push({ key, data, createdAt: data.createdAt || '1970-01-01' });
        }
    }
    needs.sort((a, b) => String(a.createdAt).localeCompare(String(b.createdAt)));
    for (const row of needs) {
        row.data.currentLicenseId = allocateLicenseId(meta);
    }
}

function recordAdminEvent(data, action, licenseId, note) {
    ensureLicenseShape(data);
    data.adminHistory.push({
        at: new Date().toISOString(),
        action,
        licenseId: licenseId ?? data.currentLicenseId,
        note: note || null
    });
    if (data.adminHistory.length > 200) {
        data.adminHistory = data.adminHistory.slice(-200);
    }
}

function licenseRowMatchesQuery(licKey, data, type, anchor, rawValue) {
    if (!data) return false;
    if (type === 'license') {
        return licKey === anchor || normalizeLicenseKey(licKey) === anchor;
    }
    if (type === 'license_id') {
        const id = parseInt(anchor, 10);
        if (data.currentLicenseId === id) return true;
        return (data.licenseIdHistory || []).some((h) => h.licenseId === id);
    }
    const normType = type === 'license_id' ? 'license' : type;
    if (normType === 'license') return false;
    const fakeAnchor = normalizeBlacklistValue(normType, rawValue);
    switch (normType) {
        case 'hwid': {
            const h = data.hwid;
            if (!h) return false;
            return h === fakeAnchor || normalizeHwidForBlacklist(h) === fakeAnchor
                || (rawValue && normalizeHwidForBlacklist(rawValue) === fakeAnchor);
        }
        case 'mc_username':
            return data.mcUsername && data.mcUsername.toLowerCase() === fakeAnchor;
        case 'mc_uuid':
            return data.mcUuid && normalizeMcUuid(data.mcUuid) === normalizeMcUuid(rawValue || fakeAnchor);
        case 'discord_id':
            return data.discordId && String(data.discordId) === fakeAnchor;
        case 'discord_username':
            return data.discordUsername && data.discordUsername.toLowerCase() === fakeAnchor;
        case 'ip': {
            const ip = String(rawValue || '').replace(/^::ffff:/i, '');
            const a = fakeAnchor;
            return (data.ip && (data.ip === rawValue || String(data.ip).replace(/^::ffff:/i, '') === a))
                || (data.lastIp && (data.lastIp === rawValue || String(data.lastIp).replace(/^::ffff:/i, '') === a));
        }
        case 'windows_name':
            return data.windowsName && data.windowsName.toLowerCase() === fakeAnchor;
        case 'pc_name':
            return data.pcName && data.pcName.toLowerCase() === fakeAnchor;
        default:
            return false;
    }
}

/**
 * @returns {{ key: string, data: object, filterLicenseId: number|null, archivedOnly: boolean }|null}
 */
function findLicenseByQuery(type, rawValue, licenses) {
    if (!rawValue || !String(rawValue).trim()) return null;
    const anchor = normalizeLicenseQueryValue(type, rawValue);

    if (type === 'license_id') {
        const id = parseInt(anchor, 10);
        if (!Number.isFinite(id)) return null;
        for (const [key, data] of licenses) {
            ensureLicenseShape(data);
            if (data.currentLicenseId === id) {
                return { key, data, filterLicenseId: id, archivedOnly: false };
            }
            for (const h of data.licenseIdHistory || []) {
                if (h.licenseId === id) {
                    return { key, data, filterLicenseId: id, archivedOnly: true };
                }
            }
        }
        return null;
    }

    for (const [key, data] of licenses) {
        if (licenseRowMatchesQuery(key, data, type, anchor, rawValue)) {
            return { key, data, filterLicenseId: null, archivedOnly: false };
        }
    }
    return null;
}

function resetHwidOnly(data, adminNote) {
    ensureLicenseShape(data);
    const lid = data.currentLicenseId;
    data.hwid = null;
    data.hwidChanges = 0;
    data.machineGuid = null;
    data.stats.hwidResets += 1;
    recordAdminEvent(data, 'hwid_reset', lid, adminNote || 'HWID + MachineGuid cleared; player info kept');
    return lid;
}

function clearLicenseIdentity(data, meta, adminNote) {
    ensureLicenseShape(data);
    const oldId = data.currentLicenseId;
    if (oldId != null) {
        data.licenseIdHistory.push({
            licenseId: oldId,
            archivedAt: new Date().toISOString(),
            snapshot: bindingSnapshot(data)
        });
    }
    const newId = allocateLicenseId(meta);
    data.currentLicenseId = newId;
    data.hwid = null;
    data.hwidChanges = 0;
    data.mcUsername = null;
    data.mcUuid = null;
    data.discordId = null;
    data.discordUsername = null;
    data.ip = null;
    data.lastIp = null;
    data.windowsName = null;
    data.pcName = null;
    data.osVersion = null;
    data.machineGuid = null;
    data.active = false;
    data.stats.clears += 1;
    recordAdminEvent(data, 'clear', newId, adminNote || `New license ID #${newId} (was #${oldId ?? '?'})`);
    return { oldId, newId };
}

function onLicenseCreated(data, meta) {
    ensureLicenseShape(data);
    data.currentLicenseId = allocateLicenseId(meta);
    recordAdminEvent(data, 'create', data.currentLicenseId, 'License key created');
}

function allLicenseIdsForKey(data) {
    ensureLicenseShape(data);
    const ids = new Set();
    if (data.currentLicenseId != null) ids.add(data.currentLicenseId);
    for (const h of data.licenseIdHistory || []) {
        if (h.licenseId != null) ids.add(h.licenseId);
    }
    return [...ids].sort((a, b) => a - b);
}

function filterAdminHistory(data, filterLicenseId) {
    const events = data.adminHistory || [];
    if (filterLicenseId == null) return events;
    return events.filter((e) => e.licenseId === filterLicenseId);
}

function formatHistoryLines(data, filterLicenseId) {
    const events = filterAdminHistory(data, filterLicenseId);
    if (events.length === 0) return '_No admin events for this filter._';
    return events.slice(-25).reverse().map((e) => {
        const ts = e.at ? `<t:${Math.floor(new Date(e.at).getTime() / 1000)}:f>` : '?';
        return `${ts} · **${e.action}** · ID #${e.licenseId}${e.note ? ` — ${e.note}` : ''}`;
    }).join('\n');
}

function computeListStats(licenses) {
    let keys = 0;
    let activated = 0;
    let online = 0;
    let totalIds = 0;
    for (const [, data] of licenses) {
        keys++;
        ensureLicenseShape(data);
        totalIds += allLicenseIdsForKey(data).length;
        if (data.hwid || data.lastLogin) activated++;
        if (data.active) online++;
    }
    return { keys, activated, online, totalIds };
}

module.exports = {
    LICENSE_QUERY_TYPES,
    LICENSE_META_FILE,
    normalizeLicenseQueryValue,
    loadLicenseMeta,
    saveLicenseMeta,
    migrateLicenseIds,
    allocateLicenseId,
    ensureLicenseShape,
    findLicenseByQuery,
    resetHwidOnly,
    clearLicenseIdentity,
    onLicenseCreated,
    allLicenseIdsForKey,
    filterAdminHistory,
    formatHistoryLines,
    computeListStats,
    bindingSnapshot
};
