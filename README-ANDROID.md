# THE ANAMIKA LUX — Android app

This repository builds an installable Android APK from the existing website.

- App name: **THE ANAMIKA LUX**
- Android application ID: `com.theanamika.lux`
- Launch screen: the existing `index.html` website
- Launcher icon: generated from the exact existing website favicon, `the-anamika-lux-logo.png`
- Website/cart/booking pages are copied into the app bundle; the source website files are not rewritten.

## Automatic APK build

Every relevant push to `main` starts the **Build THE ANAMIKA LUX Android APK** GitHub Actions workflow. It builds a debug-signed APK, stores it as a workflow artifact, and updates the GitHub release tagged `android-apk`.

Download:
https://github.com/sharyas0322-hub/anamika-lux-salon/releases/tag/android-apk

The APK is suitable for direct installation on Android. Publishing to Google Play requires a separately managed production signing key.
