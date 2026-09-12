'use strict';

const fs = require('fs');

function writeJsonAtomic(filePath, value) {
    const temporaryPath = `${filePath}.${process.pid}.tmp`;
    fs.writeFileSync(temporaryPath, JSON.stringify(value, null, 2), 'utf8');
    fs.renameSync(temporaryPath, filePath);
}

function normalizeRelease(input) {
    const buildId = String(input?.buildId || '').trim();
    const keyId = String(input?.keyId || '').trim();
    const keyHalfB = String(input?.keyHalfB || '').trim().toLowerCase();
    const jarSha256 = String(input?.jarSha256 || '').trim().toLowerCase();
    const createdAtUtc = String(input?.createdAtUtc || input?.createdAt || '').trim();

    if (!/^build-[0-9]+$/.test(buildId) || keyId !== buildId) {
        throw new Error('buildId and keyId must be the same build-<timestamp> value');
    }
    if (!/^[a-f0-9]{32}$/.test(keyHalfB)) {
        throw new Error('keyHalfB must be exactly 32 hexadecimal characters');
    }
    if (!/^[a-f0-9]{64}$/.test(jarSha256)) {
        throw new Error('jarSha256 must be exactly 64 hexadecimal characters');
    }
    if (Number.isNaN(Date.parse(createdAtUtc))) {
        throw new Error('createdAtUtc must be an ISO-8601 timestamp');
    }
    return { buildId, keyId, keyHalfB, jarSha256, createdAtUtc };
}

class ReleaseRegistry {
    constructor(filePath) {
        this.filePath = filePath;
        this.releases = new Map();
    }

    load() {
        this.releases.clear();
        if (!fs.existsSync(this.filePath)) return;
        const parsed = JSON.parse(fs.readFileSync(this.filePath, 'utf8'));
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
            throw new Error('release registry must be a JSON object');
        }
        for (const [buildId, entry] of Object.entries(parsed)) {
            const normalized = normalizeRelease({ ...entry, buildId });
            this.releases.set(buildId, normalized);
        }
    }

    register(release) {
        const normalized = normalizeRelease(release);
        const existing = this.releases.get(normalized.buildId);
        if (existing) {
            if (JSON.stringify(existing) !== JSON.stringify(normalized)) {
                throw new Error(`build ${normalized.buildId} is already registered with different release data`);
            }
            return { created: false, release: existing };
        }

        // This one file is the canonical, atomic release decision: key half and
        // JAR hash become active together only after this rename succeeds.
        const next = new Map(this.releases);
        next.set(normalized.buildId, normalized);
        const serialized = Object.fromEntries([...next.entries()].sort(([a], [b]) => a.localeCompare(b)));
        writeJsonAtomic(this.filePath, serialized);
        this.releases = next;
        return { created: true, release: normalized };
    }

    get(buildId) {
        return this.releases.get(buildId) || null;
    }

    jarHashes() {
        return new Set([...this.releases.values()].map((release) => release.jarSha256));
    }
}

module.exports = { ReleaseRegistry, normalizeRelease };
