# AGENTS.md — econverter

An Android e-book converter app wrapping Calibre (`ebook-converter`) via Chaquopy (Python 3.11).

## Tech Stack

- **UI**: Jetpack Compose (Material3), Kotlin (JVM 17, min API 24, target/compile SDK 37)
- **Python Runtime**: Chaquopy 16.0.0 (Python 3.11 embedded in Android app)
- **E-book Engine**: [`ebook-converter-lib`](https://github.com/bilec/ebook-converter-lib), pinned pip dependency, version set by `EBOOK_CONVERTER_LIB_VERSION` in [gradle.properties](gradle.properties)
- **PDF Engine**: `pypdfium2` (PDFium), pinned by `PYPDFIUM2_VERSION`; must equal the lib's pin. arm wheels come from PyPI, x86_64 from the lib's `pypdfium2-<version>-android` release
- **Build & Format**: Gradle (Kotlin DSL), Spotless + ktlint

## Architecture

- **Single Module Android App**: `app/` contains Kotlin UI and worker-process integration; the Python engine ships as a wheel.
- **Kotlin UI / ViewModel**: `MainActivity.kt` owns `ConverterScreen` and SAF pickers; `ConversionOptions.kt` owns the options form, `ConverterDialogs.kt` owns dialogs and issue reporting, and `ConverterTheme.kt` owns palettes. `ConverterViewModel.kt` holds state and manages selection, conversion, and saving.
- **Python Bridge**: `ConversionService.kt` calls `ebook_converter_lib.convert()` in the private `:conversion` process. Cancellation kills that process before removing its run directory.
- **Conversion Engine**: the packaged bridge forwards parameters to Calibre's `Plumber` conversion pipeline.
- **Backends**: `ConversionBackend.kt` owns accepted input/output formats. Settings persists Auto/Calibre/boko selection; the worker validates the snapshotted request. Direct boko skips Python. Auto uses intermediate EPUBs for KFX inputs and KFX/Markdown outputs, applying options through Calibre.
- **boko Library**: the app depends on `io.github.bilec:boko-android`, pinned by `BOKO_ANDROID_VERSION`. JNI and Rust live in the separate `boko-bindings` project.

## Developer Commands

```sh
# Build debug APK
./gradlew assembleDebug

# Deploy debug build to connected device/emulator
./gradlew installDebug

# Auto-format Kotlin source code with Spotless/ktlint
./gradlew spotlessApply

# Build release APK (requires KEYSTORE_PATH env vars)
./gradlew assembleRelease
```

## Coding Conventions

- **UI State**: Always use `var name by mutableStateOf<T>(...)` in ViewModel.
- **Error Handling**: Exceptions caught at UI boundary returned as strings starting with `"Error: "`.
- **Composables**: Helper composables marked `private fun`.
- **Resource Management**: Streams closed with `.use {}`, null checks using `?:` or `?.let {}`.
- **Formatting**: Kotlin formatting enforced via Spotless; wildcard imports (`*`) allowed per Spotless configuration.

## Documentation Maintenance

- Keep `AGENTS.md` and `CODEBASE_MAP.md` up to date whenever architecture, entrypoints, or conventions change.
- When bumping an engine version, sync its formats in `ConversionBackend.kt`, [README.md](README.md), and [fastlane full_description.txt](fastlane/metadata/android/en-US/full_description.txt).
- When changing F-Droid builds, keep drafts outside active metadata until validated; disabled future recipes also affect automatic updates. Submit [metadata/com.econverter.app.yml](metadata/com.econverter.app.yml) changes to `fdroid/fdroiddata`, which does not use app-repository copies automatically. Preserve the historical 1.2.0 recipes and [cpython srclib](srclibs/cpython.yml); their app commit has no submodule, and fdroiddata's srclib is shared with other apps.

## Domain Glossary

- **Chaquopy**: Gradle plugin embedding Python interpreter inside the APK, and installing pip dependencies for each ABI.
- **Plumber**: Calibre's core conversion orchestrator class in Python (`ebook_converter.ebooks.conversion.plumber`).
- **SAF**: Storage Access Framework (Android Uri file pickers/savers).
