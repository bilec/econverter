# eConverter

<a href="https://f-droid.org/repository/browse/?fdid=com.econverter.app" target="_blank">
<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="48" align="left" /></a><br /><br />

[![F-Droid](https://img.shields.io/f-droid/v/com.econverter.app.svg)](https://f-droid.org/en/packages/com.econverter.app/)
[![GitHub release](https://img.shields.io/github/release/bilec/econverter.svg)](https://github.com/bilec/econverter/releases) 
[![Release](https://github.com/bilec/econverter/actions/workflows/release.yml/badge.svg)](https://github.com/bilec/econverter/actions/workflows/release.yml)

Android e-book converter using:

- [ebook-converter](https://github.com/gryf/ebook-converter) through our
	[ebook-converter-lib](https://github.com/bilec/ebook-converter-lib) package and
	[Chaquopy](https://chaquo.com/chaquopy/) (Python on Android).
- [boko](https://github.com/zacharydenton/boko) by Zachary Denton through our
	Android bindings (`io.github.bilec:boko-android`).

The ebook-converter-lib package's KF8/AZW3 writer module (`ebook_converter.ebooks.mobi.writer8`) and HTML export templates (`ebook_converter/data/html_export_default*`) are ported from [calibre](https://github.com/kovidgoyal/calibre) (GPL v3, © Kovid Goyal). The templates were adapted from Mustache to templite syntax.

## Supported formats

**Input (Auto):** epub, mobi, azw3, docx, odt, fb2, html, htmlz, lrf, pdb, rtf, txt, md, markdown, pdf, kfx

**Output (Auto):** epub, kfx, mobi, azw3, docx, fb2, htmlz, lrf, md, txt, txtz

> **PDF input supports text-based PDFs.** Text, embedded images, bookmarks (as table of contents), title and a first-page cover are read with PDFium. Scanned PDFs require OCR, which is unavailable on Android.

Choose the conversion backend in **Settings**. The choice is remembered, and
accepted inputs and output choices change with the backend:

- **Auto** (default): Calibre handles its supported formats. KFX inputs are
	converted to an intermediate EPUB by boko.
	KFX and Markdown outputs use Calibre to produce an EPUB, then boko to write
	the final file. Calibre conversion options apply throughout Auto mode.
- **Calibre**: Auto's formats except KFX input and KFX/Markdown output.
- **boko**: direct native conversion. Input: epub, kfx, azw3, mobi.
	Output: epub, kfx, azw3, md. Calibre options are hidden in this mode; their values
	remain stored for switching back to Auto or Calibre.

Switching backend retains selected books. Incompatible books are flagged and
conversion is disabled until the selection is cleared or a compatible backend
is chosen. An incompatible output choice resets to EPUB.

Use **Cancel** to stop a conversion and discard all temporary outputs from the
current batch, including already completed books. Original files are unchanged.
**Clear all** deselects books. Tapping a history entry restores its source books,
output format, and backend; history lasts for the current app session.

## Build

Requires JDK 17+, Android SDK platform 35, and Python 3.11. Android Studio is
optional. Python must be available as `python3.11` on PATH, or set
`CHAQUOPY_BUILD_PYTHON` to its absolute executable path.

The [F-Droid recipe](metadata/com.econverter.app.yml) pins host CPython to
`v3.11.17` using F-Droid's shared [cpython srclib](srclibs/cpython.yml).
Each ABI recipe compiles Python, checks the required modules, and points
Gradle at that executable.
Submit recipe and srclib changes to `fdroid/fdroiddata` as well: F-Droid does
not automatically use these app-repository copies.

Engine versions are pinned by `EBOOK_CONVERTER_LIB_VERSION`, `PYPDFIUM2_VERSION` and
`BOKO_ANDROID_VERSION` in [gradle.properties](gradle.properties).
`PYPDFIUM2_VERSION` must match ebook-converter-lib's pin. PyPI has no x86_64 Android
wheel for it, so the build also reads the `pypdfium2-<version>-android` release of
ebook-converter-lib.

Build a debug APK:

```sh
./gradlew assembleDebug
```

Install on connected device/emulator:

```sh
./gradlew installDebug
```
