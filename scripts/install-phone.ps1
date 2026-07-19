# Builds (if needed) and installs the RELEASE APK on a connected phone.
# Uses obfuscated release build; does not place APK in project root.
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
. (Join-Path $Root "scripts\setup-env.ps1")
Set-Location $Root

& (Join-Path $Root "scripts\build-release.ps1")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$apk = Get-ChildItem (Join-Path $Root "dist") -Filter "Scanner-release-*.apk" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $apk) {
    Write-Host "Release APK not found in dist/"
    exit 1
}

Write-Host ""
Write-Host "Bagli cihazlar:"
adb devices -l

$phones = adb devices | Select-String "`tdevice$" | Where-Object { $_ -notmatch "emulator-" }
if (-not $phones) {
    Write-Host ""
    Write-Host "Fiziksel telefon bulunamadi."
    Write-Host "USB hata ayiklama acik olsun ve bu scripti tekrar calistir."
    exit 1
}

$serial = ($phones | Select-Object -First 1).ToString().Split("`t")[0].Trim()
Write-Host "Yukleniyor: $serial -> $($apk.Name)"
# New applicationId; uninstall old demo package if present
adb -s $serial uninstall com.example.scanner 2>$null | Out-Null
adb -s $serial uninstall com.saitsuer.scanner 2>$null | Out-Null
adb -s $serial install -r $apk.FullName
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

adb -s $serial shell am start -n com.saitsuer.scanner/.MainActivity
Write-Host ""
Write-Host "Tamam. Scanner (release) acildi."
Write-Host "APK sadece dist/ altinda; proje kokune kopyalanmaz."
