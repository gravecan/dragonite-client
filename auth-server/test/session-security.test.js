'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { sessionIsExpired, licenseIsExpired } = require('../session-security');

const NOW = Date.parse('2026-07-19T12:00:00.000Z');

test('sessions fail closed on missing, malformed, or expired timestamps', () => {
    assert.equal(sessionIsExpired(null, NOW), true);
    assert.equal(sessionIsExpired({}, NOW), true);
    assert.equal(sessionIsExpired({ expiresAt: 'not-a-date' }, NOW), true);
    assert.equal(sessionIsExpired({ expiresAt: '2026-07-19T11:59:59.000Z' }, NOW), true);
    assert.equal(sessionIsExpired({ expiresAt: '2026-07-19T12:01:00.000Z' }, NOW), false);
});

test('lifetime licenses remain valid but malformed configured expiry fails closed', () => {
    assert.equal(licenseIsExpired(null, NOW), true);
    assert.equal(licenseIsExpired({}, NOW), false);
    assert.equal(licenseIsExpired({ expiresAt: 'invalid' }, NOW), true);
    assert.equal(licenseIsExpired({ expiresAt: '2026-07-20T12:00:00.000Z' }, NOW), false);
});
