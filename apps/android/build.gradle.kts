// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register("fastDebug") {
    group = "hello"
    description = "Fast local debug APK build only. Skips release, lint, and connected tests."
    dependsOn(":app:assembleDebug")
}

tasks.register("fastTest") {
    group = "hello"
    description = "Fast JVM unit tests for the debug variant only."
    dependsOn(":app:testDebugUnitTest")
}

tasks.register("fastVerify") {
    group = "hello"
    description = "Fast everyday verification: debug APK plus debug JVM unit tests."
    dependsOn(":app:assembleDebug", ":app:testDebugUnitTest")
}

tasks.register("fastInstall") {
    group = "hello"
    description = "Install the debug APK on a connected device without release/lint work."
    dependsOn(":app:installDebug")
}
