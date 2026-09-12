param(
    [string]$Hostname = "assets-delivery.site",
    [int]$Port = 443,
    [Parameter(Mandatory = $true)][string]$ObfuscatorJar
)

$ErrorActionPreference = "Stop"

$ObfuscatorJar = (Resolve-Path -LiteralPath $ObfuscatorJar).Path
$hex = (& java -cp $ObfuscatorJar com.dragonite.obfuscator.crypto.TlsSpkiUtil $Hostname $Port).Trim()
if ($LASTEXITCODE -ne 0 -or $hex -notmatch '^[0-9a-f]{64}$') {
    throw "Could not obtain a certificate-validated SPKI pin for $Hostname`:$Port"
}
Write-Host "SPKI SHA-256 (hex): $hex"
Write-Host "Launcher: -Ddragonite.auth.spki=$hex"
