const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const source = fs.readFileSync(path.join(__dirname, '..', 'auth-server.js'), 'utf8');

test('website license sync requires a dedicated token and a direct loopback peer', () => {
    assert.match(source, /process\.env\.WEBSITE_SYNC_TOKEN/);
    assert.match(source, /requestCameDirectlyFromLoopback/);
    assert.match(source, /timingSafeEqualString/);
    assert.match(source, /app\.post\('\/v1\/internal\/license-sync'/);
});

test('license removal revokes active sessions and persists atomically', () => {
    const start = source.indexOf("app.post('/v1/internal/license-sync'");
    const end = source.indexOf("app.post('/v1/admin/key'", start);
    const route = source.slice(start, end);
    assert.match(route, /licenses\.delete/);
    assert.match(route, /revokeSessionsForLicense/);
    assert.match(route, /saveLicenses\(\)/);
});

test('Discord lookup uses the same dedicated website credential', () => {
    const start = source.indexOf("app.post('/api/license/findByDiscord'");
    const route = source.slice(start, source.indexOf("app.post('/v1/admin/test-ping'", start));
    assert.match(route, /requireWebsiteSync/);
    assert.doesNotMatch(route, /ADMIN_KEY/);
});
