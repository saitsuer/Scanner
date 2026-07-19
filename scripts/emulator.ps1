# Starts the Scanner AVD (creates it if missing)
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
. (Join-Path $Root "scripts\setup-env.ps1")

$env:Path = "$env:ANDROID_HOME\emulator;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:Path"
$avdName = "Scanner_API34"
$avdManager = Join-Path $env:ANDROID_HOME "cmdline-tools\latest\bin\avdmanager.bat"
$emulator = Join-Path $env:ANDROID_HOME "emulator\emulator.exe"

$existing = & $avdManager list avd 2>&1 | Out-String
if ($existing -notmatch $avdName) {
    Write-Host "Creating AVD $avdName ..."
    echo "no" | & $avdManager create avd `
        -n $avdName `
        -k "system-images;android-34;google_apis;x86_64" `
        -d "pixel_6" `
        --force
}

Write-Host "Starting emulator $avdName ..."
Start-Process -FilePath $emulator -ArgumentList @("-avd", $avdName, "-netdelay", "none", "-netspeed", "full")
Write-Host "Waiting for device boot..."
adb wait-for-device
$deadline = (Get-Date).AddMinutes(5)
do {
    Start-Sleep -Seconds 3
    $boot = adb shell getprop sys.boot_completed 2>$null
} while (($boot -notmatch "1") -and ((Get-Date) -lt $deadline))

if ($boot -match "1") {
    Write-Host "Emulator ready."
    adb devices
} else {
    Write-Host "Timed out waiting for boot. Check emulator window."
    exit 1
}
