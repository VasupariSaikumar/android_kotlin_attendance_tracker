plugins {
    alias(libs.plugins.android.application) apply false

    // Kotlin
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.kapt") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false

    // Firebase
    id("com.google.gms.google-services") version "4.4.1" apply false
}