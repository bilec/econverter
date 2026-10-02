# AGENTS.md — econverter

An Android e-book converter app wrapping Calibre (`ebook-converter`) via Chaquopy (Python 3.11).

## Tech Stack

- **UI**: Jetpack Compose (Material3), Kotlin (JVM 17, min API 24, target/compile SDK 35)
- **Python Runtime**: Chaquopy 16.0.0 (Python 3.11 embedded in Android app)
- **E-book Engine**: [`ebook-converter-lib`](https://github.com/bilec/ebook-converter-lib), pinned pip dependency, version set by `EBOOK_CONVERTER_LIB_VERSION` in [gradle.properties](gradle.properties)
- **Build & Format**: Gradle (Kotlin DSL), Spotless + ktlint

## Architecture

- **Single Module Android App**: `app/` contains the Kotlin UI only; the Python engine ships as a wheel.
- **Kotlin UI / ViewModel**: `MainActivity.kt` renders Compose UI (`ConverterScreen`), `ConverterViewModel.kt` holds state and manages file picking/saving via Storage Access Framework.
- **Python Bridge**: `ConverterViewModel.kt` launches coroutines calling `ebook_converter_lib.convert()` / `check_deps_str()` via Chaquopy.
- **Conversion Engine**: the packaged bridge forwards parameters to Calibre's `Plumber` conversion pipeline.

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
- When bumping `EBOOK_CONVERTER_LIB_VERSION`, sync the input format list in `ConverterViewModel.inputFormats`, [README.md](README.md), and [fastlane full_description.txt](fastlane/metadata/android/en-US/full_description.txt).

## Domain Glossary

- **Chaquopy**: Gradle plugin embedding Python interpreter inside the APK, and installing pip dependencies for each ABI.
- **Plumber**: Calibre's core conversion orchestrator class in Python (`ebook_converter.ebooks.conversion.plumber`).
- **SAF**: Storage Access Framework (Android Uri file pickers/savers).
