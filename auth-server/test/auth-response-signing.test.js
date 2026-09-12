'use strict';

const crypto = require('crypto');
const test = require('node:test');
const assert = require('node:assert/strict');
const { authResponseSigningPayload } = require('../auth-response-signing');

const payload = { authenticated: true, sessionToken: 'session', expiresAt: '2026-07-18T18:00:00.000Z' };
const request = { method: 'POST', path: '/v1/auth', body: { nonce: 'nonce-a', jarHash: 'A'.repeat(64) } };

test('signed auth response binds the nonce and normalized JAR hash', () => {
    const signed = authResponseSigningPayload(request, 200, payload);
    assert.match(signed, /\|nonce-a\|a{64}$/);
    assert.notEqual(signed, authResponseSigningPayload({ ...request, body: { ...request.body, nonce: 'nonce-b' } }, 200, payload));
    assert.notEqual(signed, authResponseSigningPayload({ ...request, body: { ...request.body, jarHash: 'b'.repeat(64) } }, 200, payload));
});

test('Ed25519 signature verification fails when request binding changes', () => {
    const { publicKey, privateKey } = crypto.generateKeyPairSync('ed25519');
    const signed = Buffer.from(authResponseSigningPayload(request, 200, payload), 'utf8');
    const signature = crypto.sign(null, signed, privateKey);
    assert.equal(crypto.verify(null, signed, publicKey, signature), true);
    const replay = Buffer.from(authResponseSigningPayload({ ...request, body: { ...request.body, nonce: 'replayed' } }, 200, payload), 'utf8');
    assert.equal(crypto.verify(null, replay, publicKey, signature), false);
});
