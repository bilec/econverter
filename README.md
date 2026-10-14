# eConverter

<a href="https://f-droid.org/repository/browse/?fdid=com.econverter.app" target="_blank">
<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="48" align="left" /></a><br /><br />

[![F-Droid](https://img.shields.io/f-droid/v/com.econverter.app.svg)](https://f-droid.org/en/packages/com.econverter.app/)
[![GitHub release](https://img.shields.io/github/release/bilec/econverter.svg)](https://github.com/bilec/econverter/releases) 
[![Release](https://github.com/bilec/econverter/actions/workflows/release.yml/badge.svg)](https://github.com/bilec/econverter/actions/workflows/release.yml)

Android app wrapping [ebook-converter](https://github.com/gryf/ebook-converter) via [Chaquopy](https://chaquo.com/chaquopy/) (Python on Android).

The conversion engine lives in [ebook-converter-lib](https://github.com/bilec/ebook-converter-lib) and is installed as a pinned pip dependency. The pinned version is `EBOOK_CONVERTER_LIB_VERSION` in [gradle.properties](gradle.properties). This repository contains only the Android app.

The library's KF8/AZW3 writer module (`ebook_converter.ebooks.mobi.writer8`) and HTML export templates (`ebook_converter/data/html_export_default*`) are ported from [calibre](https://github.com/kovidgoyal/calibre) (GPL v3, © Kovid Goyal). The templates were adapted from Mustache to templite syntax.

## Supported formats

**Input (Auto):** epub, mobi, azw3, docx, odt, fb2, html, htmlz, lrf, pdb, rtf, txt, md, markdown, pdf, kfx

**Output (Auto):** epub, kfx, mobi, azw3, docx, fb2, htmlz, lrf, md, txt, txtz

> **PDF input supports text-based PDFs.** Scanned PDFs require OCR, which is unavailable on Android.

Choose the conversion backend in **Settings**. The choice is remembered, and
accepted inputs and output choices change with the backend:

- **Auto** (default): Calibre handles its supported formats. KFX inputs are
	converted to an intermediate EPUB by [boko](https://github.com/zacharydenton/boko).
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

No Android Studio required. APK builds use JDK 17+, the Android SDK, and
Python 3.11 available as `python3.11` on PATH for Chaquopy bytecode compilation.
The boko Kotlin/JNI implementation is owned by the separate `boko-bindings`
project, not this repository. `BOKO_ANDROID_VERSION` in
[gradle.properties](gradle.properties) pins its Maven artifact.

```sh
./gradlew assembleDebug
```

Until `io.github.bilec:boko-android:0.1.0` is published to Maven Central, use
the explicit local-source override with the sibling checkout:

```sh
export ANDROID_HOME=/path/to/android-sdk
./gradlew -PbokoBindingsDir=../boko-bindings assembleDebug
```

The override uses a Gradle composite build to substitute the Maven dependency.
Only source builds require Docker; Rust and the NDK remain in the bindings
project's container. Published AARs include arm64-v8a, armeabi-v7a, and x86_64
libraries, so normal app builds need neither Docker nor Rust.
CI and clean checkouts without the override require the pinned artifact to be
available in Maven Central.

Install on connected device/emulator:

```sh
./gradlew installDebug
```

## Prerequisites

- JDK 17+
- Python 3.11 available as `python3.11` on PATH
- Android SDK with platform 35 (`sdkmanager "platforms;android-35"`)
- For the local bindings source override only: Docker and a `boko-bindings` checkout
