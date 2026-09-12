param(
    [Parameter(Mandatory = $true)]
    [string]$JarPath,
    [string]$EnvFile = ""
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path $JarPath)) { throw "JAR not found: $JarPath" }

$hash = (Get-FileHash -Algorithm SHA256 -Path $JarPath).Hash.ToLower()
Write-Host "SHA-256: $hash"
Write-Host ""
Write-Host "On the VPS, add to /opt/dragonite-auth/.env (comma-separate multiple builds):"
Write-Host "  EXPECTED_JAR_HASHES=$hash"
Write-Host ""
Write-Host "Or append to existing hashes, then:"
Write-Host "  pm2 restart dragonite-auth"
Write-Host ""
Write-Host "On Linux VPS with the JAR uploaded:"
Write-Host "  bash server/scripts/register-jar-hash.sh /path/to/your.jar"
