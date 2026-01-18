plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.android")

    id("kotlin-kapt")
}

android {
    namespace = "com.technikh.employeeattendancetracking"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.technikh.employeeattendancetracking"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    // --- MISSING BLOCKS ADDED BELOW ---
    buildFeatures {
        compose = true
    }

    composeOptions {
        // This version MUST be 1.5.1 to match Kotlin 1.9.0
        kotlinCompilerExtensionVersion = "1.5.1"
    }
}

dependencies {
    // DataStore (For saving Settings like IP/Key)
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Supabase
    implementation("io.github.jan-tennert.supabase:postgrest-kt:2.3.0")
    implementation("io.github.jan-tennert.supabase:storage-kt:2.3.0")
    implementation("io.ktor:ktor-client-okhttp:2.3.0")

    implementation("com.google.android.material:material:1.11.0")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.08.00")) // Using stable BOM
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Charts
    implementation("com.patrykandpatrick.vico:compose:1.12.0")
    implementation("com.patrykandpatrick.vico:core:1.12.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:${cameraxVersion}")
    implementation("androidx.camera:camera-camera2:${cameraxVersion}")
    implementation("androidx.camera:camera-lifecycle:${cameraxVersion}")
    implementation("androidx.camera:camera-view:${cameraxVersion}")
    implementation("io.coil-kt:coil-compose:2.5.0")
}