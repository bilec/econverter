# AGENTS.md — econverter

An Android e-book converter app wrapping Calibre (`ebook-converter`) via Chaquopy (Python 3.11).

## Tech Stack

- **UI**: Jetpack Compose (Material3), Kotlin (JVM 17, min API 24, target/compile SDK 35)
- **Python Runtime**: Chaquopy 16.0.0 (Python 3.11 embedded in Android app)
- **E-book Engine**: Custom Calibre fork in `app/src/main/python/ebook_converter`
- **Build & Format**: Gradle (Kotlin DSL), Spotless + ktlint, `uv` + `ruff` for Python

## Architecture

- **Single Module Android App**: `app/` contains both Kotlin UI and Python runtime.
- **Kotlin UI / ViewModel**: `MainActivity.kt` renders Compose UI (`ConverterScreen`), `ConverterViewModel.kt` holds state and manages file picking/saving via Storage Access Framework.
- **Python Bridge**: `ConverterViewModel.kt` launches coroutines calling `converter.py` via Chaquopy.
- **Conversion Engine**: `converter.py` bridges parameters to Calibre's `Plumber` conversion pipeline.

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

# Python environment & testing (uv + ruff)
uv sync
uv run ruff check app/src/main/python
uv run ruff format app/src/main/python
uv run pytest
```

## Coding Conventions

- **UI State**: Always use `var name by mutableStateOf<T>(...)` in ViewModel.
- **Error Handling**: Exceptions caught at UI boundary returned as strings starting with `"Error: "`.
- **Composables**: Helper composables marked `private fun`.
- **Resource Management**: Streams closed with `.use {}`, null checks using `?:` or `?.let {}`.
- **Imports**: No wildcard imports (`*`).

## Documentation Maintenance

- Keep `AGENTS.md` and `CODEBASE_MAP.md` up to date whenever architecture, entrypoints, or conventions change.

## Domain Glossary

- **Chaquopy**: Gradle plugin embedding Python interpreter inside the Android APK.
- **Plumber**: Calibre's core conversion orchestrator class in Python (`ebook_converter.ebooks.conversion.plumber`).
- **SAF**: Storage Access Framework (Android Uri file pickers/savers).
