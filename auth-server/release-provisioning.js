'use strict';

/**
 * A brand-new production server needs to accept the first authenticated release
 * registration before it can have an allowlisted JAR.  While that happens, all
 * client-facing routes remain unavailable instead of treating every JAR as
 * allowed.
 */
function isReleaseProvisioningMode({ isProduction, enforceJarHash, hasAllowedJarHash }) {
    return Boolean(isProduction && enforceJarHash && !hasAllowedJarHash);
}

module.exports = { isReleaseProvisioningMode };
