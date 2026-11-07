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
// Foreground polling of the operational queues stays off until the backend closes M3. Debug builds can enable it locally with
// dleche.queuePolling=true; release builds ignore it.
val queuePollingEnabled = localProperties.getProperty("dleche.queuePolling")?.toBoolean() ?: false

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

        // Background check (every 15 min, the platform minimum) that notifies a signed-in user of new work in their queue.
        // Flip to false to ship without it; the foreground polling above is a separate switch.
        buildConfigField("boolean", "ORDER_ALERTS_ENABLED", "true")
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
            buildConfigField("boolean", "QUEUE_POLLING_ENABLED", "$queuePollingEnabled")
            buildConfigField("int", "QUEUE_POLLING_SECONDS", "60")
        }
        release {
            // Published API. Do not point a release build at it until the server runs the current backend.
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiUrl\"")
            // Must mirror CAPTCHA_ENABLED on the server; when true the login needs the Turnstile WebView (not built yet).
            buildConfigField("boolean", "CAPTCHA_ENABLED", "false")
            buildConfigField("String", "TURNSTILE_SITE_KEY", "\"\"")
            // Never from local.properties: a release must not poll until the backend closes M3 (shared rate-limit key).
            buildConfigField("boolean", "QUEUE_POLLING_ENABLED", "false")
            buildConfigField("int", "QUEUE_POLLING_SECONDS", "60")
            // R8 code shrinking, obfuscation, resource optimization. Library consumer rules (Retrofit, OkHttp) apply
            // automatically; only app-level rules live in proguard-rules.pro.
            // mapping.txt is written to app/build/outputs/mapping/release/ and must be archived per versionCode with
            // `archiveReleaseMapping`, otherwise release crashes cannot be deobfuscated.
            optimization {
                enable = true
                keepRules {
                    files.add(file("proguard-rules.pro"))
                }
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
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation(libs.okhttp.mockwebserver)
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Copies the release R8 mapping.txt to app/release-mappings/<versionCode>/ (git-ignored) so crash stack traces from
// that build stay deobfuscatable. Run after assembleRelease: `bash gradlew archiveReleaseMapping`.
tasks.register<Copy>("archiveReleaseMapping") {
    description = "Archives app/build/outputs/mapping/release/mapping.txt under release-mappings/<versionCode>/."
    group = "distribution"
    dependsOn("assembleRelease")
    val versionCodeValue = checkNotNull(android.defaultConfig.versionCode) { "defaultConfig.versionCode is required" }
    from(layout.buildDirectory.file("outputs/mapping/release/mapping.txt"))
    into(layout.projectDirectory.dir("release-mappings/$versionCodeValue"))
}
