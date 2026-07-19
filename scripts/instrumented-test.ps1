# Runs Espresso tests on a connected device/emulator
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
. (Join-Path $Root "scripts\setup-env.ps1")
Set-Location $Root

$devices = adb devices | Select-String "device$"
if (-not $devices) {
    Write-Host "No device found. Start emulator first: .\scripts\emulator.ps1"
    exit 1
}

# Wake / unlock and reduce flaky Espresso focus issues on fresh AVDs
adb shell input keyevent KEYCODE_WAKEUP 2>$null
adb shell wm dismiss-keyguard 2>$null
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell am force-stop com.example.scanner 2>$null

.\gradlew.bat connectedDebugAndroidTest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "Instrumented tests passed."
