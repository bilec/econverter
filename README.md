# eConverter

Android app wrapping [ebook-converter](https://github.com/gryf/ebook-converter) via [Chaquopy](https://chaquo.com/chaquopy/) (Python on Android).

## Supported formats

**Input:** epub, docx, odt, txt, rtf, mobi, azw3, fb2, html, lrf, pdb

**Output:** epub, mobi, docx, txt, lrf, htmlz

> **PDF input is NOT supported** — requires poppler CLI tools (pdftohtml, pdfinfo, pdftoppm) which are unavailable on Android.

## Build

No Android Studio required. JDK 17+ and Android SDK command-line tools suffice.

```sh
./gradlew assembleDebug
```

Install on connected device/emulator:

```sh
./gradlew installDebug
```

## Prerequisites

- JDK 17+
- Android SDK with platform 35 (`sdkmanager "platforms;android-35"`)
- NDK (Chaquopy auto-downloads if missing, or: `sdkmanager "ndk;26.1.10909125"`)
