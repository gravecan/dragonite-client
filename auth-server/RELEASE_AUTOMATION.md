# Automated release registration

Each finalized JAR is registered as one atomic record in `release_registry.json`:
its build ID, split-key half, final SHA-256, and creation timestamp are written
together. Existing registered hashes remain accepted; a retry with identical
data is safe, while a conflicting reuse of a build ID is rejected.

## One-time VPS setup

Generate a dedicated random registration token on the VPS and put it in the
auth-server `.env` file. This is not `ADMIN_KEY`; do not reuse the admin key.

```bash
openssl rand -hex 32
```

Add the resulting value as:

```dotenv
RELEASE_REGISTRATION_TOKEN=replace-with-the-random-value
```

Restart the service after deploying `auth-server.js`, `release-registry.js`,
and `release-provisioning.js`:

```bash
cd /root/dragonite-auth
npm test
node --check auth-server.js
pm2 restart dragonite-auth --update-env
```

With `NODE_ENV=production` and `ENFORCE_JAR_HASH=true`, a fresh server with no
legacy hash intentionally starts in **release provisioning lock**. It rejects
all client authentication until the first release is registered, while keeping
only the authenticated release registration/verification endpoints available.
Run the normal release build to register that first hash automatically; do not
temporarily disable hash enforcement just to bootstrap it.

## One-time build-machine setup

Keep the same token only in the build machine's user environment, never in a
tracked file, the JAR, release manifests, source, or build logs.

```powershell
[Environment]::SetEnvironmentVariable('DRAGONITE_RELEASE_REGISTRATION_TOKEN', 'replace-with-the-random-value', 'User')
[Environment]::SetEnvironmentVariable('DRAGONITE_RELEASE_REGISTRATION_URL', 'https://dragoniteclient.fun/v1/release/register', 'User')
```

Open a new terminal after setting the variables. The release script sends the
final manifest over HTTPS, then calls `/v1/release/verify`. If either call
fails, it prints `RELEASE NOT READY TO DISTRIBUTE` and retains the local
`build-output/release-build-*.json` file for the offline VPS fallback.

## Offline fallback

Copy the final `release-build-*.json` file to the VPS, then run:

```bash
node scripts/register-release-key.js /path/to/release-build-123.json
```

That command uses the VPS-local registration token and verifies the stored
record. Do not edit `key_halves.json` or `EXPECTED_JAR_HASHES` by hand for new
releases.
