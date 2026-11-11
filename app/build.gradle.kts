plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.foundit.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.foundit.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    
    // Glide image loading library
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Free, open-source MapLibre map SDK
    implementation("org.maplibre.gl:android-sdk:11.11.0")
}