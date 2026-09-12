'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { isReleaseProvisioningMode } = require('../release-provisioning');

test('a production server with no JAR hash is locked for initial release provisioning', () => {
    assert.equal(isReleaseProvisioningMode({
        isProduction: true,
        enforceJarHash: true,
        hasAllowedJarHash: false
    }), true);
});

test('registered and legacy allowlists exit provisioning mode', () => {
    assert.equal(isReleaseProvisioningMode({
        isProduction: true,
        enforceJarHash: true,
        hasAllowedJarHash: true
    }), false);
    assert.equal(isReleaseProvisioningMode({
        isProduction: false,
        enforceJarHash: true,
        hasAllowedJarHash: false
    }), false);
    assert.equal(isReleaseProvisioningMode({
        isProduction: true,
        enforceJarHash: false,
        hasAllowedJarHash: false
    }), false);
});
