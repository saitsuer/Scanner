# Builds an obfuscated release APK into dist/ (gitignored). Never copies to project root.
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

.\gradlew.bat assembleRelease
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$dist = Join-Path $Root "dist"
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$src = Join-Path $Root "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $src)) {
    # AGP may name signed output differently
    $src = Get-ChildItem (Join-Path $Root "app\build\outputs\apk\release") -Filter "*.apk" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
$dest = Join-Path $dist ("Scanner-release-" + (Get-Date -Format "yyyyMMdd-HHmm") + ".apk")
Copy-Item $src $dest -Force
# Do not leave a root-level Scanner.apk
Remove-Item (Join-Path $Root "Scanner.apk") -Force -ErrorAction SilentlyContinue
Write-Host ""
Write-Host "Release APK (local only, not for public upload):"
Write-Host $dest
