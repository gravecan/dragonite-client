param(
    [Parameter(Mandatory = $true)]
    [string]$JarSha256,
    [string]$AuthUrl = "https://assets-delivery.site",
    [string]$AdminKey = $env:DRAGONITE_ADMIN_KEY,
    [string]$LicenseKey = ""
)

$ErrorActionPreference = "Stop"
$hash = $JarSha256.Trim().ToLower()
if ($hash -notmatch '^[a-f0-9]{64}$') {
    throw "JarSha256 must be 64 lowercase hex characters (SHA-256)"
}
if (-not $AdminKey) { throw "Set DRAGONITE_ADMIN_KEY or pass -AdminKey" }

$headers = @{
    Authorization = "Bearer $AdminKey"
    "Content-Type"  = "application/json"
}

Write-Host "Registering global JAR hash on $AuthUrl ..."
$body = @{ jarSha256 = $hash } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$AuthUrl/v1/admin/jar-hash" -Headers $headers -Body $body | ConvertTo-Json
Write-Host "OK: $hash"

if ($LicenseKey) {
    Write-Host "Appending hash to license allowedJarHashes ..."
    $patch = @{
        licenseKey = $LicenseKey
        jarHashes  = @($hash)
        append     = $true
    } | ConvertTo-Json
    Invoke-RestMethod -Method Patch -Uri "$AuthUrl/v1/admin/license/jar-hashes" -Headers $headers -Body $patch | ConvertTo-Json
}
