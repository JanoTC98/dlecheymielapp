import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Per-developer values live in local.properties (git-ignored), so a changing LAN IP never lands in the repo:
//   dleche.debugApiUrl=http://192.168.18.4:8080/     (physical phone: the PC's Wi-Fi IPv4)
// Without it the debug build targets the emulator's alias for the host PC.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val debugApiUrl = localProperties.getProperty("dleche.debugApiUrl") ?: "http://10.0.2.2:8080/"
val releaseApiUrl = localProperties.getProperty("dleche.releaseApiUrl") ?: "https://dlecheymiel.pe/"

android {
    namespace = "com.grupo4.dlecheymielapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.grupo4.dlecheymielapp"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            // Local backend (Docker compose). HTTP is only allowed here, see src/debug/res/xml/network_security_config.xml.
            buildConfigField("String", "API_BASE_URL", "\"$debugApiUrl\"")
            // The local backend runs with CAPTCHA_ENABLED=false.
            buildConfigField("boolean", "CAPTCHA_ENABLED", "false")
            buildConfigField("String", "TURNSTILE_SITE_KEY", "\"\"")
        }
        release {
            // Published API. Do not point a release build at it until the server runs the current backend.
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiUrl\"")
            // Must mirror CAPTCHA_ENABLED on the server; when true the login needs the Turnstile WebView (not built yet).
            buildConfigField("boolean", "CAPTCHA_ENABLED", "false")
            buildConfigField("String", "TURNSTILE_SITE_KEY", "\"\"")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.zxing.core)
    implementation(libs.okhttp.logging.interceptor)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
