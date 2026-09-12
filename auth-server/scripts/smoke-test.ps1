# Local pre-flight checks before shipping a build (run on Windows build machine)
param(
    [Parameter(Mandatory = $true)]
    [string]$JarPath
)
$ErrorActionPreference = "Stop"
if (-not (Test-Path $JarPath)) { throw "JAR not found: $JarPath" }

Write-Host "=== Dragonite smoke preflight ===" -ForegroundColor Cyan
$hash = (Get-FileHash -Algorithm SHA256 -Path $JarPath).Hash.ToLower()
Write-Host "JAR SHA-256: $hash"
Write-Host ""
Write-Host "Manual steps (clean PC):" -ForegroundColor Yellow
Write-Host "  1. Copy legendaryFabric JAR + register hash on VPS (register-jar-hash.ps1)"
Write-Host "  2. Launcher includes SPKI from compute-spki.ps1 (or use obfuscator-embedded BuildFingerprint)"
Write-Host "  3. Login -> join server -> open GUI -> enable KillAura/TargetHUD"
Write-Host "  4. Stop auth server briefly — client should exit after grace period"
Write-Host ""
$leaks = @("discord.com/api/webhooks", "ADMIN_KEY=", "SERVER_SECRET=")
$zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $JarPath))
try {
    foreach ($e in $zip.Entries) {
        if ($e.FullName -notmatch '\.(class|json)$') { continue }
        $sr = New-Object System.IO.StreamReader($e.Open())
        $text = $sr.ReadToEnd(); $sr.Close()
        foreach ($p in $leaks) {
            if ($text.Contains($p)) { Write-Warning "Possible leak in $($e.FullName): $p" }
        }
    }
} finally { $zip.Dispose() }
Write-Host "Telemetry scan done." -ForegroundColor Green
