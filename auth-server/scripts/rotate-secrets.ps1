# Generates new production secrets — paste into VPS /opt/dragonite-auth/.env then: pm2 restart dragonite-auth
$ErrorActionPreference = "Stop"
Write-Host "=== Dragonite secret rotation ($(Get-Date -Format o)) ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "SERVER_SECRET=$( -join ((1..32) | ForEach-Object { '{0:x2}' -f (Get-Random -Max 256) }))"
Write-Host "ADMIN_KEY=$( -join ((1..24) | ForEach-Object { '{0:x2}' -f (Get-Random -Max 256) }))"
Write-Host "ACDETECT_ENCRYPTION_KEY=$( -join ((1..32) | ForEach-Object { '{0:x2}' -f (Get-Random -Max 256) }))"
Write-Host ""
Write-Host "DISCORD_WEBHOOK=<create new webhook in Discord channel settings>"
Write-Host ""
Write-Host "After updating .env on the VPS:" -ForegroundColor Yellow
Write-Host "  pm2 restart dragonite-auth"
Write-Host "  curl -s https://dragoniteclient.fun/v1/health"
