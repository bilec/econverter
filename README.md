# eConverter

<a href="https://f-droid.org/repository/browse/?fdid=com.econverter.app" target="_blank">
<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="48" align="left" /></a><br /><br />

[![F-Droid](https://img.shields.io/f-droid/v/com.econverter.app.svg)](https://f-droid.org/en/packages/com.econverter.app/)
[![GitHub release](https://img.shields.io/github/release/bilec/econverter.svg)](https://github.com/bilec/econverter/releases) 
[![Release](https://github.com/bilec/econverter/actions/workflows/release.yml/badge.svg)](https://github.com/bilec/econverter/actions/workflows/release.yml)

Android app wrapping [ebook-converter](https://github.com/gryf/ebook-converter) via [Chaquopy](https://chaquo.com/chaquopy/) (Python on Android).

The KF8/AZW3 writer module (`ebook_converter.ebooks.mobi.writer8`) and HTML export templates (`ebook_converter/data/html_export_default*`) are ported from [calibre](https://github.com/kovidgoyal/calibre) (GPL v3, © Kovid Goyal). The templates were adapted from Mustache to templite syntax.

## Supported formats

**Input:** epub, mobi, azw3, azw4, docx, odt, fb2, html, htmlz, lrf, pdb, rtf, txt, djvu, djv, chm, cbz, cbr

**Output:** epub, mobi, azw3, docx, fb2, html, htmlz, lrf, oeb, txt, txtz

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
