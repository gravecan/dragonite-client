'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const {
    sanitizeWebhookContent,
    sensitiveFingerprint,
    AlertDeduplicator
} = require('../notification-security');

test('notification content redacts mentions, licenses, and local paths', () => {
    const result = sanitizeWebhookContent(
        '@everyone ABCD-EFGH-IJKL-MNOP-QRST C:\\Users\\Daniel\\secret.txt');
    assert.equal(result.includes('@everyone'), false);
    assert.equal(result.includes('ABCD-EFGH'), false);
    assert.equal(result.includes('Daniel'), false);
});

test('lowercase UUID lines are not treated as license keys', () => {
    const result = sanitizeWebhookContent(
        'UUID: 2c4c2c03-b344-46bc-98c3-0c09655a9d05');
    assert.equal(result.includes('2c4c2c03-b344-46bc-98c3-0c09655a9d05'), true);
});

test('operator License lines keep the full key for Discord projection', () => {
    const license = 'ABCD-1234-WXYZ-5678-9012';
    const result = sanitizeWebhookContent(`License: ${license}`);
    assert.equal(result, `License: ${license}`);
});

test('operator HWID lines keep the full hash for Discord projection', () => {
    const hwid = 'a'.repeat(64);
    const result = sanitizeWebhookContent(
        `HWID: ${hwid}\nStored HWID SHA-256: ${hwid}`);
    assert.equal(result.split('\n')[0], `HWID: ${hwid}`);
    assert.equal(result.split('\n')[1], `Stored HWID SHA-256: ${hwid}`);
});

test('user-supplied lines still redact pasted license keys', () => {
    const result = sanitizeWebhookContent('MC: ABCD-EFGH-IJKL-MNOP-QRST');
    assert.equal(result.includes('ABCD-EFGH'), false);
});

test('sensitive identifiers still have a short fingerprint helper for logs', () => {
    const value = 'ABCD-EFGH-IJKL-MNOP-QRST';
    const fingerprint = sensitiveFingerprint(value);
    assert.match(fingerprint, /^sha256:[a-f0-9]{12}$/);
    assert.equal(fingerprint.includes(value), false);
});

test('alert deduplication suppresses only the configured burst window', () => {
    const dedupe = new AlertDeduplicator(1000);
    assert.equal(dedupe.accept('same', 1000), true);
    assert.equal(dedupe.accept('same', 1500), false);
    assert.equal(dedupe.accept('different', 1500), true);
    assert.equal(dedupe.accept('same', 2001), true);
});
