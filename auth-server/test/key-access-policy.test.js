'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { authorizeKeyAccess } = require('../key-access-policy');

const HASH_A = 'a'.repeat(64);
const HASH_B = 'b'.repeat(64);

function access(overrides = {}) {
    return authorizeKeyAccess({
        production: true,
        session: { license: 'license-a', jarHash: HASH_A },
        keyData: { assignedTo: null },
        release: { jarSha256: HASH_A },
        ...overrides
    });
}

test('allows a registered key only for its authenticated release hash', () => {
    assert.equal(access().allowed, true);
    assert.deepEqual(access({ release: { jarSha256: HASH_B } }), {
        allowed: false,
        reason: 'wrong_release'
    });
});

test('production rejects keys absent from the atomic release registry', () => {
    assert.deepEqual(access({ release: null }), {
        allowed: false,
        reason: 'unregistered_release'
    });
});

test('development preserves explicit legacy unregistered keys', () => {
    assert.deepEqual(access({ production: false, release: null }), {
        allowed: true,
        reason: 'legacy_development_key'
    });
});

test('license assignment remains an independent authorization requirement', () => {
    assert.deepEqual(access({ keyData: { assignedTo: 'license-b' } }), {
        allowed: false,
        reason: 'wrong_license'
    });
});

test('registered releases reject missing or malformed session hashes', () => {
    assert.equal(access({ session: { license: 'license-a', jarHash: null } }).allowed, false);
    assert.equal(access({ session: { license: 'license-a', jarHash: 'not-a-hash' } }).allowed, false);
});
