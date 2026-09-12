'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { registerManifest } = require('../scripts/register-release-key');

const manifest = {
    buildId: 'build-1784395843756',
    keyId: 'build-1784395843756',
    keyHalfB: 'b4b0d5907b4c3a3e765d35c5f17b1b69',
    jarSha256: 'c'.repeat(64),
    createdAtUtc: '2026-07-18T17:30:43.756Z'
};

function response(ok, status, body = '{}') {
    return { ok, status, text: async () => body };
}

test('registers and verifies a release, including an idempotent retry', async () => {
    const calls = [];
    const fetchImpl = async (url, options) => {
        calls.push({ url, options });
        return response(true, url.endsWith('/register') ? 201 : 200);
    };
    await registerManifest(manifest, 'release-token', 'https://dragoniteclient.fun', fetchImpl);
    await registerManifest(manifest, 'release-token', 'https://dragoniteclient.fun', fetchImpl);
    assert.equal(calls.length, 4);
    assert.match(calls[0].options.headers.authorization, /^Bearer /);
    assert.equal(JSON.parse(calls[0].options.body).jarSha256, manifest.jarSha256);
});

test('fails closed when the server rejects registration or verification', async () => {
    await assert.rejects(
        registerManifest(manifest, 'release-token', 'https://dragoniteclient.fun', async () => response(false, 409, 'conflict')),
        /rejected release registration/
    );
    let count = 0;
    await assert.rejects(
        registerManifest(manifest, 'release-token', 'https://dragoniteclient.fun', async () => response(++count === 1, count === 1 ? 201 : 404, 'missing')),
        /could not verify/
    );
});

test('fails closed on network errors and malformed manifests', async () => {
    await assert.rejects(
        registerManifest(manifest, 'release-token', 'https://dragoniteclient.fun', async () => { throw new Error('offline'); }),
        /offline/
    );
    await assert.rejects(
        registerManifest({ ...manifest, keyHalfB: 'not-a-key' }, 'release-token'),
        /keyHalfB/
    );
});

test('public release manifest excludes split-key material', () => {
    const script = fs.readFileSync(
        path.join(__dirname, '..', '..', 'scripts', 'Protect-And-Release-Native.ps1'),
        'utf8'
    );
    const publicManifestBlock = script.slice(
        script.indexOf('# Public build metadata.'),
        script.indexOf('# Private registration/recovery payload.')
    );
    assert.match(publicManifestBlock, /keyHalfBSha256/);
    assert.doesNotMatch(publicManifestBlock, /\bkeyHalfB\s*=/);
    assert.match(script, /release-registration-/);
});
