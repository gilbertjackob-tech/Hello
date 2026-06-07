plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

fun String.asBuildConfigString(): String {
    return "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

fun geminiKeysFromPlan(): Map<String, String> {
    val planFile = rootProject.projectDir.parentFile?.parentFile?.resolve("plan.md")
    if (planFile?.isFile != true) return emptyMap()
    val keyPattern = Regex("""(gemini_api\d+)\s*=\s*"([^"]+)"""")
    return planFile.readLines()
        .mapNotNull { line ->
            keyPattern.find(line)?.let { match -> match.groupValues[1] to match.groupValues[2] }
        }
        .toMap()
}

android {
    namespace = "com.glassbox.hello"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.glassbox.hello"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val forceRelay = providers.gradleProperty("webrtcForceRelay").orNull?.toBoolean()
            ?: System.getenv("WEBRTC_FORCE_RELAY")?.toBoolean()
            ?: false
        buildConfigField("Boolean", "WEBRTC_FORCE_RELAY", forceRelay.toString())

        val drivePcBaseUrl = providers.gradleProperty("drivePcBaseUrl").orNull
            ?: System.getenv("DRIVE_PC_BASE_URL")
            ?: "https://home.bookhelloctg.com/hello/api"
        buildConfigField("String", "DRIVE_PC_BASE_URL", drivePcBaseUrl.asBuildConfigString())

        val planGeminiKeys = geminiKeysFromPlan()
        val geminiKeys = listOf(
            providers.gradleProperty("gemini_api1").orNull
                ?: System.getenv("GEMINI_API1")
                ?: planGeminiKeys["gemini_api1"],
            providers.gradleProperty("gemini_api2").orNull
                ?: System.getenv("GEMINI_API2")
                ?: planGeminiKeys["gemini_api2"]
        ).filterNot { it.isNullOrBlank() }
            .joinToString(",")
        buildConfigField("String", "GEMINI_API_KEYS", geminiKeys.asBuildConfigString())
    }

    signingConfigs {
        create("release") {
            storeFile = file(providers.gradleProperty("HELLO_UPLOAD_STORE_FILE").get())
            storePassword = providers.gradleProperty("HELLO_UPLOAD_STORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("HELLO_UPLOAD_KEY_ALIAS").get()
            keyPassword = providers.gradleProperty("HELLO_UPLOAD_KEY_PASSWORD").get()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            keepDebugSymbols += setOf("**/*.so")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.material)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.socketio)
    implementation(libs.gson)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.webrtc.android)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}
