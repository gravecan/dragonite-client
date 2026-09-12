'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { proofKind, proofFormatAllowed } = require('../auth-proof-policy');

test('production accepts only nonce-bound proof format', () => {
    assert.equal(proofFormatAllowed('hmac2:signature', { production: true }), true);
    assert.equal(proofFormatAllowed('ecdsa2:signature', { production: true }), true);
    assert.equal(proofFormatAllowed('ecdsa:captured', { production: true, allowLegacy: true }), false);
    assert.equal(proofFormatAllowed('ed25519:captured', { production: true, allowLegacy: true }), false);
    assert.equal(proofFormatAllowed('', { production: true }), false);
});

test('hmac2 is classified as nonce-bound', () => {
    assert.equal(proofKind('hmac2:abc'), 'nonce-bound');
});

test('legacy proof requires explicit non-production migration switch', () => {
    assert.equal(proofKind('ecdsa:signature'), 'legacy');
    assert.equal(proofFormatAllowed('ecdsa:signature', { production: false }), false);
    assert.equal(proofFormatAllowed('ecdsa:signature', { production: false, allowLegacy: true }), true);
});
