# Run once from Polish\src to download gradle-wrapper.jar
$wrapperDir = Join-Path $PSScriptRoot "gradle\wrapper"
$jarPath = Join-Path $wrapperDir "gradle-wrapper.jar"
$url = "https://raw.githubusercontent.com/gradle/gradle/v8.10.2/gradle/wrapper/gradle-wrapper.jar"

if (Test-Path $jarPath) { Write-Host "gradle-wrapper.jar exists. Skipping."; exit 0 }
if (-not (Test-Path $wrapperDir)) { New-Item -ItemType Directory -Path $wrapperDir -Force | Out-Null }
Write-Host "Downloading gradle-wrapper.jar..."
Invoke-WebRequest -Uri $url -OutFile $jarPath -UseBasicParsing
Write-Host "Done. Run: .\gradlew.bat build"
