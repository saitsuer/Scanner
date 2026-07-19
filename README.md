# Scanner (private)

US ID / document scanner. Local development only.

## Protection notes

- Do **not** commit or publish APK/AAB files.
- Release builds use R8 minify + resource shrinking + a private signing key (`release.jks`, `keystore.properties` — gitignored).
- Application id: `com.saitsuer.scanner`
- Keep the GitHub repo **private**.
- APKs are written only under `dist/` via `scripts\build-release.ps1`.

## Build (local)

```powershell
.\scripts\build-release.ps1
.\scripts\install-phone.ps1
```

## Flow

Home → Scan ID / license or Scan document → Camera → Crop → Done → PDF
