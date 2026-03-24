plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.tvtuner.core.ui"
    compileSdk = 36

    defaultConfig {
        minSdk = 33
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.ui)
    api(libs.androidx.ui.graphics)
    api(libs.androidx.ui.tooling.preview)
    api(libs.androidx.material3)
    api(libs.androidx.material.icons.extended)
    api(libs.androidx.adaptive)
    api(libs.androidx.adaptive.layout)
    api(libs.androidx.adaptive.navigation)
    api(libs.androidx.lifecycle.runtime.compose)

    debugImplementation(libs.androidx.ui.tooling)
}
