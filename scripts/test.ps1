# Runs JVM unit tests (no emulator required)
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
. (Join-Path $Root "scripts\setup-env.ps1")
Set-Location $Root
.\gradlew.bat test --info
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host ""
Write-Host "Unit tests passed. HTML report:"
Write-Host "  $Root\app\build\reports\tests\testDebugUnitTest\index.html"
