plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.oavi.browser"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.oavi.browser"
        minSdk = 37
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        // The engine ships native code per CPU type; phones on Android 17 are all arm64.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        // Builds are signed with the key in keystore/ so every build installs over the last
        // one. Set the OAVI_KEYSTORE_* variables to sign with a private key instead.
        create("oavi") {
            val customStore = System.getenv("OAVI_KEYSTORE_FILE")
            storeFile = if (customStore != null) file(customStore) else rootProject.file("keystore/oavi-sideload.keystore")
            storePassword = System.getenv("OAVI_KEYSTORE_PASSWORD") ?: "oavi-sideload"
            keyAlias = System.getenv("OAVI_KEY_ALIAS") ?: "oavi"
            keyPassword = System.getenv("OAVI_KEY_PASSWORD") ?: "oavi-sideload"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("oavi")
            // Lets the debug build run on the x86_64 emulator used for the CI smoke test.
            ndk {
                abiFilters += "x86_64"
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("oavi")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:device"))
    implementation(project(":core:data"))
    implementation(project(":core:engine"))
    implementation(project(":core:ui"))
    implementation(project(":feature:browser"))
    implementation(project(":feature:tabs"))
    implementation(project(":feature:privacy"))
    implementation(project(":feature:reader"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:settings"))
    implementation(libs.androidx.profileinstaller)
}
