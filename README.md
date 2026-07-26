# Scanner

Android app to scan US driver’s licenses, state IDs, passports, and documents
into PDF or JPEG, and to design a two-sided business card from your own
details.

## Features

- ID type picker: driver’s license, state ID, passport, other card
- Passport photo page + optional visa/endorsement page (TD3 life-size on US Letter)
- Card front/back with stacked or side-by-side Letter layout
- Built-in camera + crop (unchanged core); optional ML Kit document scanner
- Review screen: preview, rename, export PDF or JPEG (quality)
- Library rename + export JPEG from viewer
- OCR text extraction
- Business card designer: fill in name/title/contact/services, pick a brand
  color pair, add a logo and QR link, then export a print-ready two-sided
  card (US standard 3.5×2in) as PDF or PNG; saved designs can be reopened
  and edited later

## Privacy / build notes

- APKs and signing keys are **not** in this repo (`dist/`, `release.jks`, `keystore.properties` are gitignored)
- Release builds use R8 minify + resource shrinking
- Application id: `com.saitsuer.scanner`

## Build (Windows)

```powershell
.\scripts\build-release.ps1
.\scripts\install-phone.ps1
```

## Flow

Home → Scan ID/passport (pick type) or Scan document → Camera → Crop → (layout if card) → Review & save → Library

Home → Create business card → fill in details, logo, colors, QR link → Preview (front/back) → Save PDF/PNG → Library (reopen to edit)
