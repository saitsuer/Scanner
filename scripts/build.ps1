# Assembles debug APK
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
. (Join-Path $Root "scripts\setup-env.ps1")
Set-Location $Root
.\gradlew.bat assembleDebug
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host ""
Write-Host "APK:"
Write-Host "  $Root\app\build\outputs\apk\debug\app-debug.apk"
