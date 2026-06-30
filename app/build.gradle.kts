plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
    id("com.diffplug.spotless")
}

android {
    namespace = "com.econverter.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.econverter.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.2"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    // ponytail: no splits — Chaquopy conflicts with splits.abi. Single APK with both archs.

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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") // ponytail: null when KEYSTORE_PATH unset → F-Droid signs instead
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

chaquopy {
    defaultConfig {
        version = "3.12"
        extractPackages("ebook_converter")
        pip {
            install("beautifulsoup4>=4.9.3")
            install("css-parser>=1.0.6")
            install("filelock>=3.0.12")
            install("html2text>=2020.1.16")
            install("lxml")
            install("odfpy>=1.4.1")
            install("pillow>=8.0.1")
            install("python-dateutil>=2.8.1")
            install("tinycss>=0.4")
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
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
