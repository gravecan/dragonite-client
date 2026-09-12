# Dragonite release secret / exposure audit (Composer or manual).
# Usage:
#   .\server\scripts\audit-release-secrets.ps1 -JarPath "C:\path\to\cloth-config-15.0.140-legendaryFabric.jar"
param(
    [Parameter(Mandatory = $true)]
    [string]$JarPath
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path -LiteralPath $JarPath)) { throw "JAR not found: $JarPath" }

$patterns = @(
    "SERVER_SECRET", "ADMIN_KEY", "DISCORD_TOKEN", "discord.com/api/webhooks",
    "BEGIN PRIVATE", "PKCS8", "NATIVE_IKM", "ed25519.*private"
)
$ipPattern = "\b\d{1,3}(\.\d{1,3}){3}\b"

$temp = Join-Path $env:TEMP ("dragonite-audit-" + [Guid]::NewGuid().ToString("n"))
New-Item -ItemType Directory -Path $temp -Force | Out-Null
try {
    Expand-Archive -LiteralPath $JarPath -DestinationPath $temp -Force
    Write-Host "=== Secret / key string scan ===" -ForegroundColor Cyan
    $hits = 0
    foreach ($p in $patterns) {
        $m = Get-ChildItem -LiteralPath $temp -Recurse -File | Select-String -Pattern $p -SimpleMatch:$false -ErrorAction SilentlyContinue
        if ($m) {
            $hits++
            Write-Host "PATTERN: $p" -ForegroundColor Yellow
            $m | Select-Object -First 5 | ForEach-Object { Write-Host "  $($_.Path):$($_.LineNumber)" }
        }
    }
    if ($hits -eq 0) { Write-Host "No obvious secret patterns in JAR contents." -ForegroundColor Green }

    Write-Host "`n=== JAR SHA-256 (for EXPECTED_JAR_HASHES) ===" -ForegroundColor Cyan
    $hash = (Get-FileHash -LiteralPath $JarPath -Algorithm SHA256).Hash.ToLower()
    Write-Host $hash

    Write-Host "`n=== ED25519 private key in Java classes (must be placeholder only) ===" -ForegroundColor Cyan
    $pk = Get-ChildItem -LiteralPath $temp -Recurse -Filter "*.class" | Select-String -Pattern "ED25519_SIGNING_PKCS8" -SimpleMatch -ErrorAction SilentlyContinue
    if ($pk) { Write-Host "Found ED25519_SIGNING_PKCS8 reference in class files (verify obfuscated / __NATIVE__ only)." -ForegroundColor Yellow }
    else { Write-Host "No ED25519_SIGNING_PKCS8 string in .class files (good)." -ForegroundColor Green }
}
finally {
    Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue
}
