import com.android.build.api.variant.FilterConfiguration
import groovy.json.JsonOutput
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.gradle.api.file.DirectoryProperty

val abiVersionCodes = mapOf("armeabi-v7a" to 1, "x86_64" to 3, "arm64-v8a" to 4)
val baseVersionCode = providers.gradleProperty("VERSION_CODE").map(String::toInt).get()
val releaseVersionName = providers.gradleProperty("VERSION_NAME").get()
val ebookConverterLibVersion = providers.gradleProperty("EBOOK_CONVERTER_LIB_VERSION").get()
val pypdfium2Version = providers.gradleProperty("PYPDFIUM2_VERSION").get()
val bokoAndroidVersion = providers.gradleProperty("BOKO_ANDROID_VERSION").get()

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
    id("com.diffplug.spotless")
}

abstract class InstalledPythonLibraries : DefaultTask() {
    @get:OutputDirectory
    abstract val jniDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val assetsDirectory: DirectoryProperty
}

android {
    namespace = "com.econverter.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.econverter.app"
        minSdk = 24
        targetSdk = 37
        versionCode = baseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += abiVersionCodes.keys
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include(*abiVersionCodes.keys.toTypedArray())
            isUniversalApk = true
        }
    }

    if (System.getenv("KEYSTORE_PATH") != null) {
        signingConfigs {
            create("release") {
                storeFile = file(System.getenv("KEYSTORE_PATH")!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") // ponytail: null when KEYSTORE_PATH unset → F-Droid signs instead
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("17"))
    }
}

androidComponents {
    onVariants { variant ->
        val taskSuffix = variant.name.replaceFirstChar(Char::uppercaseChar)
        val pythonAssets = listOf("misc", "requirements", "source").map {
            layout.buildDirectory.dir("python/assets/$it/${variant.name}/chaquopy")
        }
        val nativeLibraries = layout.buildDirectory.dir("generated/installedPython/${variant.name}/jniLibs")
        val nativeAssets = layout.buildDirectory.dir("generated/installedPython/${variant.name}/assets")
        val bundleLibraries = tasks.register<InstalledPythonLibraries>("generate${taskSuffix}InstalledPythonLibraries") {
            jniDirectory.set(nativeLibraries)
            assetsDirectory.set(nativeAssets)
            dependsOn("generate${taskSuffix}PythonMiscAssets", "generate${taskSuffix}PythonRequirementsAssets", "generate${taskSuffix}PythonSourceAssets")
            inputs.files(pythonAssets)
            doLast {
                val librariesDirectory = nativeLibraries.get().asFile
                val assetsDirectory = nativeAssets.get().asFile
                val pythonAssetDirectories = pythonAssets.map { it.get().asFile }
                val nativeLibraryPattern = Regex("\\.so($|\\.)")
                librariesDirectory.deleteRecursively()
                assetsDirectory.deleteRecursively()
                for (abi in abiVersionCodes.keys) {
                    val mapping = sortedMapOf<String, String>()
                    fun bundleLibrary(relativePath: String, source: InputStream) {
                        require(!relativePath.startsWith('/') && relativePath.split('/').none { it == ".." })
                        val hash = MessageDigest.getInstance("SHA-256").digest(relativePath.toByteArray()).joinToString("") { "%02x".format(it) }
                        val libraryName = "libchaquopy_$hash.so"
                        check(mapping.put(relativePath, libraryName) == null) { "Duplicate native Python library: $relativePath" }
                        val destination = librariesDirectory.resolve("$abi/$libraryName")
                        check(destination.parentFile.isDirectory || destination.parentFile.mkdirs())
                        destination.outputStream().use { source.copyTo(it) }
                    }
                    for (assetDirectory in pythonAssetDirectories) {
                        val bootstrap = assetDirectory.resolve("bootstrap-native/$abi")
                        if (bootstrap.isDirectory) {
                            bootstrap.walkTopDown().filter { it.isFile && nativeLibraryPattern.containsMatchIn(it.name) }.forEach { library ->
                                library.inputStream().use { bundleLibrary("bootstrap-native/$abi/${library.relativeTo(bootstrap).invariantSeparatorsPath}", it) }
                            }
                        }
                        for (archiveName in listOf("stdlib-$abi.imy", "requirements-$abi.imy", "requirements-common.imy", "app.imy")) {
                            val archive = assetDirectory.resolve(archiveName)
                            if (!archive.isFile) continue
                            val packageDirectory = when {
                                archiveName.startsWith("stdlib-") -> "stdlib-$abi"
                                archiveName.startsWith("requirements-") -> "requirements"
                                else -> "app"
                            }
                            ZipFile(archive).use { zip ->
                                zip.entries().asSequence().filter { !it.isDirectory && nativeLibraryPattern.containsMatchIn(it.name) }.forEach { entry ->
                                    zip.getInputStream(entry).use { bundleLibrary("AssetFinder/$packageDirectory/${entry.name}", it) }
                                }
                            }
                        }
                    }
                    val manifest = assetsDirectory.resolve("chaquopy/native-libraries/$abi.json")
                    check(manifest.parentFile.isDirectory || manifest.parentFile.mkdirs())
                    manifest.writeText(JsonOutput.toJson(mapping))
                }
            }
        }
        variant.sources.jniLibs?.addGeneratedSourceDirectory(bundleLibraries) { it.jniDirectory }
        variant.sources.assets?.addGeneratedSourceDirectory(bundleLibraries) { it.assetsDirectory }
    }

    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            val abiVersionCode = output.filters
                .find { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
                ?.let(abiVersionCodes::get)
                ?: 0
            output.versionCode.set(baseVersionCode * 100 + abiVersionCode)
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.11"
        buildPython(providers.environmentVariable("CHAQUOPY_BUILD_PYTHON").getOrElse("python3.11"))
        extractPackages("ebook_converter")
        pip {
            options("--find-links", "$projectDir/libs")
            // PyPI has pypdfium2 Android wheels for arm only; x86_64 comes from ebook-converter-lib releases
            options("--find-links", "https://github.com/bilec/ebook-converter-lib/releases/expanded_assets/pypdfium2-$pypdfium2Version-android")
            install("ebook-converter-lib==$ebookConverterLibVersion")
            install("pypdfium2==$pypdfium2Version")
        }
    }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint().editorConfigOverride(mapOf(
            "ktlint_standard_no-wildcard-imports" to "disabled",
            "ktlint_standard_max-line-length" to "disabled",
            "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
            "ktlint_standard_function-naming" to "disabled"
        ))
    }
}

dependencies {
    implementation("io.github.bilec:boko-android:$bokoAndroidVersion")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
