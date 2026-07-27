# Builds a signed release Android App Bundle (.aab) into dist/ for Play Store upload (gitignored).
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
. (Join-Path $Root "scripts\setup-env.ps1")
Set-Location $Root

$props = Join-Path $Root "keystore.properties"
$jks = Join-Path $Root "release.jks"
if (-not (Test-Path $props) -or -not (Test-Path $jks)) {
    Write-Host "Release keystore missing. Creating one-time local keystore..."
    $pass = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 24 | ForEach-Object { [char]$_ })
    & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v `
        -keystore $jks `
        -storepass $pass `
        -keypass $pass `
        -alias scanner `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=Scanner, OU=Private, O=saitsuer, L=Private, ST=NA, C=US" | Out-Host
    @"
storeFile=release.jks
storePassword=$pass
keyAlias=scanner
keyPassword=$pass
"@ | Set-Content -Path $props -Encoding ASCII
    Write-Host "Created release.jks + keystore.properties (gitignored). Keep these private."
}

.\gradlew.bat bundleRelease
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$dist = Join-Path $Root "dist"
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$src = Join-Path $Root "app\build\outputs\bundle\release\app-release.aab"
if (-not (Test-Path $src)) {
    $src = Get-ChildItem (Join-Path $Root "app\build\outputs\bundle\release") -Filter "*.aab" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
$dest = Join-Path $dist ("Scanner-release-" + (Get-Date -Format "yyyyMMdd-HHmm") + ".aab")
Copy-Item $src $dest -Force
Write-Host ""
Write-Host "Signed release bundle (upload this .aab to Play Console):"
Write-Host $dest
Write-Host ""
Write-Host "IMPORTANT: back up release.jks and keystore.properties somewhere safe outside this repo."
Write-Host "Losing them means you can no longer publish updates to this app listing."
