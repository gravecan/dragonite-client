'use strict';

/** Canonical string shared by the auth-response signing path and tests. */
function authResponseSigningPayload(request, statusCode, payload) {
    const granted = payload.authenticated === true || payload.success === true || payload.ok === true ? '1' : '0';
    const nonce = String(request?.body?.nonce || '');
    const jarHash = String(request?.body?.jarHash || '').trim().toLowerCase();
    return `${request.method}|${request.path}|${statusCode}|${granted}|${payload.sessionToken || ''}|${payload.expiresAt || ''}|${nonce}|${jarHash}`;
}

module.exports = { authResponseSigningPayload };
