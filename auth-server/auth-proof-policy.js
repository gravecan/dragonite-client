'use strict';

function proofKind(proof) {
    if (typeof proof !== 'string') return 'missing';
    if (proof.startsWith('hmac2:')) return 'nonce-bound';
    if (proof.startsWith('ecdsa2:')) return 'nonce-bound';
    if (proof.startsWith('ecdsa:') || proof.startsWith('ed25519:')) return 'legacy';
    return 'unknown';
}

function proofFormatAllowed(proof, { production = false, allowLegacy = false } = {}) {
    const kind = proofKind(proof);
    if (kind === 'nonce-bound') return true;
    if (kind === 'legacy') return !production && allowLegacy;
    return false;
}

module.exports = { proofKind, proofFormatAllowed };
