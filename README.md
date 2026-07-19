# Scanner

Android app to scan US driver’s licenses, state IDs, and documents into PDF.

## Features

- Scan ID / driver’s license (front + back, life-size on US Letter)
- Scan multi-page documents
- Built-in camera + crop; optional ML Kit document scanner when available
- Stacked or side-by-side ID layout chooser

## Privacy / build notes

- APKs and signing keys are **not** in this repo (`dist/`, `release.jks`, `keystore.properties` are gitignored)
- Release builds use R8 minify + resource shrinking
- Application id: `com.saitsuer.scanner`

## Build (Windows)

Requires JDK 17 + Android SDK (or use a local `.tools` setup).

```powershell
.\scripts\build-release.ps1
.\scripts\install-phone.ps1
```

## Flow

Home → Scan ID / license or Scan document → Camera → Crop → Done → PDF
