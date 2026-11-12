plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.oavi.browser.device"
    compileSdk = 37

    defaultConfig {
        minSdk = 37
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:model"))
}
