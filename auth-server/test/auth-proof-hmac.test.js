'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('crypto');
const {
    deriveAuthProofKey,
    verifyLicenseBoundHmac2,
    verifyHwidNonceBoundHmac2,
    diagnoseLicenseBoundHmac2,
    hkdfSha256
} = require('../auth-proof-hmac');

test('hkdf matches client vector shape', () => {
    const ikm = Buffer.alloc(32, 0x11);
    const out = hkdfSha256(ikm, Buffer.from('salt'), Buffer.from('info'), 32);
    assert.equal(out.length, 32);
});

test('dedicated HWID verifier accepts only the exact nonce-bound canonical payload', () => {
    const marker = '__HWID_AUTH__';
    const nonce = 'nonce-hwid';
    const timestamp = '1785683860881';
    const hwid = 'hwid-value';
    const jarHash = 'ABCDEF';
    const ikmHex = crypto.randomBytes(32).toString('hex');
    const payload = `${nonce}|${timestamp}|${hwid}|${marker}|abcdef`;
    const key = deriveAuthProofKey(ikmHex, marker);
    const signature = crypto.createHmac('sha256', key).update(payload, 'utf8').digest('base64');
    const proof = `hmac2:${signature}`;

    assert.equal(verifyHwidNonceBoundHmac2(
        nonce, timestamp, hwid, proof, jarHash, ikmHex, marker), true);
    assert.equal(verifyHwidNonceBoundHmac2(
        'different-nonce', timestamp, hwid, proof, jarHash, ikmHex, marker), false);
    assert.equal(verifyHwidNonceBoundHmac2(
        nonce, timestamp, hwid, `hmac:${signature}`, jarHash, ikmHex, marker), false);
});

test('license-bound hmac2 round trip', () => {
    const ikmHex = crypto.randomBytes(32).toString('hex');
    const license = 'ABCD-1234-EFGH-5678-IJKL';
    const payload = 'nonce|123|hwid|ABCD1234EFGH5678IJKL|deadbeef';
    const key = deriveAuthProofKey(ikmHex, license);
    assert.ok(key);
    const sig = crypto.createHmac('sha256', key).update(payload, 'utf8').digest('base64');
    assert.equal(verifyLicenseBoundHmac2(payload, sig, ikmHex, license), true);
    assert.equal(verifyLicenseBoundHmac2(payload, sig, ikmHex, 'wrong-license'), false);
});

test('mismatch diagnostics contain only bounded fingerprints', () => {
    const ikmHex = crypto.randomBytes(32).toString('hex');
    const license = 'SECRET-LICENSE-1234';
    const payload = 'secret-nonce|123|secret-hwid|__HWID_AUTH__|secret-jar';
    const key = deriveAuthProofKey(ikmHex, license);
    const sig = crypto.createHmac('sha256', key).update(payload, 'utf8').digest('base64');
    const diagnostic = diagnoseLicenseBoundHmac2(payload, sig, ikmHex, license);
    const serialized = JSON.stringify(diagnostic);
    assert.equal(diagnostic.actualProofFp, diagnostic.expectedProofFp);
    assert.equal(Object.keys(diagnostic).length, 4);
    assert.doesNotMatch(serialized, /secret|HWID_AUTH|SECRET-LICENSE/i);
    for (const value of Object.values(diagnostic)) {
        assert.match(value, /^[a-f0-9]{16}$/);
    }
});
