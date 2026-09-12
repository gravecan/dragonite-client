'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');
const test = require('node:test');
const assert = require('node:assert/strict');
const { ReleaseRegistry } = require('../release-registry');

function release(id = 'build-1784395843756') {
    return {
        buildId: id,
        keyId: id,
        keyHalfB: 'b4b0d5907b4c3a3e765d35c5f17b1b69',
        jarSha256: 'a'.repeat(64),
        createdAtUtc: '2026-07-18T17:30:43.756Z'
    };
}

function registryForTest() {
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'dragonite-release-registry-'));
    return { directory, registry: new ReleaseRegistry(path.join(directory, 'releases.json')) };
}

test('registers a release atomically and reloads its key/hash pair', () => {
    const { directory, registry } = registryForTest();
    try {
        assert.equal(registry.register(release()).created, true);
        const reloaded = new ReleaseRegistry(path.join(directory, 'releases.json'));
        reloaded.load();
        assert.equal(reloaded.get('build-1784395843756').keyHalfB, release().keyHalfB);
        assert.ok(reloaded.jarHashes().has(release().jarSha256));
    } finally {
        fs.rmSync(directory, { recursive: true, force: true });
    }
});

test('is idempotent for the same manifest and rejects a conflicting retry', () => {
    const { directory, registry } = registryForTest();
    try {
        registry.register(release());
        assert.equal(registry.register(release()).created, false);
        assert.throws(() => registry.register({ ...release(), jarSha256: 'b'.repeat(64) }), /different release data/);
    } finally {
        fs.rmSync(directory, { recursive: true, force: true });
    }
});

test('rejects malformed release data without creating a registry file', () => {
    const { directory, registry } = registryForTest();
    try {
        assert.throws(() => registry.register({ ...release(), keyHalfB: 'bad' }), /keyHalfB/);
        assert.equal(fs.existsSync(path.join(directory, 'releases.json')), false);
    } finally {
        fs.rmSync(directory, { recursive: true, force: true });
    }
});
