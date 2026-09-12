param(
    [Parameter(Mandatory = $true)][string]$JarPath,
    [string]$AuthUrl = "https://assets-delivery.site",
    [string]$AdminKey = $env:DRAGONITE_ADMIN_KEY,
    [string]$LicenseKey = ""
)
if (-not $AdminKey) { throw "Set DRAGONITE_ADMIN_KEY or pass -AdminKey" }
$hash = (Get-FileHash -Algorithm SHA256 -Path $JarPath).Hash.ToLower()
& "$PSScriptRoot\register-jar-hash-remote.ps1" -JarSha256 $hash -AuthUrl $AuthUrl -AdminKey $AdminKey -LicenseKey $LicenseKey
