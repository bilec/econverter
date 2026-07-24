# CODEBASE_MAP.md — econverter

## Directory Structure

```
.
├── app/
│   ├── src/main/
│   │   ├── java/com/econverter/app/   # Kotlin source (Activity, ViewModel, Compose UI)
│   │   ├── python/                    # Embedded Python source
│   │   │   ├── converter.py           # Chaquopy bridge / Plumber CLI wrapper
│   │   │   └── ebook_converter/       # Vendored Calibre conversion library
│   │   ├── res/                       # Android app drawables and values
│   │   └── AndroidManifest.xml        # Android manifest
│   └── build.gradle.kts              # App build script (Chaquopy pip dependencies)
├── fastlane/metadata/                 # Store descriptions and graphics
├── metadata/                          # F-Droid app metadata
├── build.gradle.kts                   # Root Gradle build script
└── settings.gradle.kts                # Gradle settings
```

## Public Entrypoints & Key Surfaces

| Path | Purpose |
|------|---------|
| [app/src/main/java/com/econverter/app/MainActivity.kt](app/src/main/java/com/econverter/app/MainActivity.kt) | Entry Activity, initializes Python, sets Compose theme, handles SAF file pickers. |
| [app/src/main/java/com/econverter/app/ConverterViewModel.kt](app/src/main/java/com/econverter/app/ConverterViewModel.kt) | State container, file input/output handling, coroutine execution for conversion. |
| [app/src/main/python/converter.py](app/src/main/python/converter.py) | Python entrypoint called by Chaquopy; parses CLI args and runs `Plumber`. |
| [app/build.gradle.kts](app/build.gradle.kts) | Configures Chaquopy, Python 3.11, pip dependencies (`lxml`, `pillow`, `reportlab`, etc.). |

## Primary Data Flows

1. **Input File Selection**:
   User selects file via Storage Access Framework (`OpenDocument`) → `MainActivity.kt` passes Uri to `ConverterViewModel.kt` → copied to temporary file in app storage.

2. **Conversion Execution**:
   User configures options and taps Convert → `ConverterViewModel.kt` builds CLI args → calls `converter.py` via Chaquopy → `Plumber.run()` converts e-book → output saved to local app cache.

3. **Output File Saving**:
   User selects target location via Storage Access Framework (`CreateDocument`) → `ConverterViewModel.kt` copies generated output file to target Uri.

## Omitted Generated / Build Paths

The following paths are generated build artifacts and should remain excluded from indexing:
- `app/build/`
- `.gradle/`
- `**/__pycache__/`
- `.kotlin/`
