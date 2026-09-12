#!/usr/bin/env node
/*
 * Offline VPS fallback for a private release-registration payload. The build machine uses the
 * same two registration calls over HTTPS; this command reaches localhost and
 * reads the dedicated token from the VPS .env file.
 */
const fs = require('fs');
const path = require('path');

function envValue(text, name) {
  const match = text.match(new RegExp(`^${name}=(.*)$`, 'm'));
  return match ? match[1].trim() : '';
}

function releasePayload(manifest) {
  const payload = {
    buildId: manifest?.buildId,
    keyId: manifest?.keyId,
    keyHalfB: manifest?.keyHalfB,
    jarSha256: manifest?.jarSha256,
    createdAtUtc: manifest?.createdAtUtc
  };
  if (!/^build-[0-9]+$/.test(String(payload.buildId)) || payload.keyId !== payload.buildId) {
    throw new Error('Manifest has an invalid buildId or keyId.');
  }
  if (!/^[0-9a-f]{32}$/i.test(String(payload.keyHalfB))) {
    throw new Error('Manifest has an invalid keyHalfB.');
  }
  if (!/^[0-9a-f]{64}$/i.test(String(payload.jarSha256))) {
    throw new Error('Manifest has an invalid jarSha256. Use the final release manifest after hash patching.');
  }
  if (Number.isNaN(Date.parse(String(payload.createdAtUtc)))) {
    throw new Error('Manifest has an invalid createdAtUtc timestamp.');
  }
  return payload;
}

async function registerManifest(manifest, releaseToken, baseUrl = 'http://127.0.0.1:8000', fetchImpl = fetch) {
  if (!releaseToken) throw new Error('RELEASE_REGISTRATION_TOKEN is missing.');
  const payload = releasePayload(manifest);
  const headers = { 'content-type': 'application/json', authorization: `Bearer ${releaseToken}` };
  const response = await fetchImpl(`${baseUrl}/v1/release/register`, {
    method: 'POST', headers, body: JSON.stringify(payload)
  });
  const body = await response.text();
  if (!response.ok) throw new Error(`Auth server rejected release registration (${response.status}): ${body}`);

  const verify = await fetchImpl(`${baseUrl}/v1/release/verify`, {
    method: 'POST', headers, body: JSON.stringify(payload)
  });
  const verifyBody = await verify.text();
  if (!verify.ok) throw new Error(`Auth server could not verify release registration (${verify.status}): ${verifyBody}`);
  return payload;
}

async function main() {
  const manifestPath = process.argv[2];
  if (!manifestPath) throw new Error('Usage: node scripts/register-release-key.js /path/to/release-registration-build-*.json');
  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  const root = path.resolve(__dirname, '..');
  const envText = fs.readFileSync(path.join(root, '.env'), 'utf8');
  const releaseToken = envValue(envText, 'RELEASE_REGISTRATION_TOKEN');
  const payload = await registerManifest(manifest, releaseToken);
  console.log(`Registered and verified release ${payload.buildId}.`);
}

if (require.main === module) {
  main().catch((error) => {
    console.error(`Registration failed: ${error.message}`);
    process.exitCode = 1;
  });
}

module.exports = { registerManifest, releasePayload };
