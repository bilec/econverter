# CODEBASE_MAP.md — econverter

## Directory Structure

```
.
├── app/
│   ├── src/main/
│   │   ├── java/com/econverter/app/   # Kotlin source (Activity, ViewModel, Compose UI)
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
| [app/src/main/java/com/econverter/app/MainActivity.kt](app/src/main/java/com/econverter/app/MainActivity.kt) | Entry Activity, sets Compose theme, handles SAF file pickers. |
| [app/src/main/java/com/econverter/app/ConverterViewModel.kt](app/src/main/java/com/econverter/app/ConverterViewModel.kt) | State container, file input/output handling, coroutine execution for conversion. |
| [app/src/main/java/com/econverter/app/ConversionBackend.kt](app/src/main/java/com/econverter/app/ConversionBackend.kt) | Backend descriptions and accepted input/output capabilities. |
| [app/src/main/java/com/econverter/app/ConversionService.kt](app/src/main/java/com/econverter/app/ConversionService.kt) | Bound conversion process and cancellable Binder client. |
| [app/build.gradle.kts](app/build.gradle.kts) | Configures Chaquopy, the pinned Python engine, and the external boko Android AAR. |
| [settings.gradle.kts](settings.gradle.kts) | Optional `bokoBindingsDir` composite-build substitution for local library development. |

## Primary Data Flows

1. **Input File Selection**:
   User selects files via Storage Access Framework (`OpenMultipleDocuments`) → `MainActivity.kt` passes Uris to `ConverterViewModel.kt`. History retains selections for the current session; Clear all removes only the selection.

2. **Conversion Execution**:
   User taps Convert → `ConverterViewModel.kt` snapshots the request → `ConversionService.kt` copies inputs and runs Chaquopy in the private `:conversion` process → outputs remain in a per-run directory under app storage. Cancel stops the worker and discards all outputs from the current batch.

   Settings selects Auto, Calibre, or boko and filters format choices. Direct boko skips Python. Auto normalizes KFX inputs with boko, applies conversion options through Calibre, and uses boko for KFX/Markdown output via intermediate EPUBs.

   Native conversion calls `io.github.bilec.boko.Boko` from the external Android library. This repository contains no Rust or JNI implementation.

3. **Output File Saving**:
   User selects target location via Storage Access Framework (`CreateDocument`) → `ConverterViewModel.kt` copies generated output file to target Uri. Single-file saves resolve the MIME type from the output filename at launch, with an explicit EPUB mapping and a binary fallback for unknown extensions. The storage provider controls duplicate naming.

## Omitted Generated / Build Paths

The following paths are generated build artifacts and should remain excluded from indexing:
- `app/build/`
- `.gradle/`
- `**/__pycache__/`
- `.kotlin/`
