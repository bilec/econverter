# eConverter

Android app wrapping [ebook-converter](https://github.com/gryf/ebook-converter) via [Chaquopy](https://chaquo.com/chaquopy/) (Python on Android).

The KF8/AZW3 writer module (`ebook_converter.ebooks.mobi.writer8`) is ported directly from [calibre](https://github.com/kovidgoyal/calibre) (GPL v3, © Kovid Goyal), as it was never included in gryf's fork.

## Supported formats

**Input:** epub, mobi, azw3, azw4, docx, odt, fb2, html, htmlz, lit, lrf, pdb, pml, rb, rtf, snb, tcr, txt, djvu, djv, chm, cbz, cbr

**Output:** epub, mobi, azw3, docx, fb2, htmlz, html, lit, lrf, oeb, pdb, pml, rb, rtf, snb, tcr, txt, txtz

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
