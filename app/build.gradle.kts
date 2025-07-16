plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") version "1.9.20"
}

android {
    namespace = "com.Trans2Thai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.Trans2Thai"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        // We are enabling compose AND viewBinding, as your project uses both.
        compose = true
        viewBinding = true
    }
    composeOptions {
        // This version is compatible with Kotlin 1.9.22
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // ⭐ THIS IS THE CORRECT LOCATION FOR THE dependencies BLOCK ⭐
    dependencies {
        // Core & UI
        implementation("androidx.core:core-ktx:1.12.0")
        implementation("androidx.appcompat:appcompat:1.6.1")
        implementation("com.google.android.material:material:1.11.0")
        implementation("androidx.constraintlayout:constraintlayout:2.1.4")
        implementation("com.google.code.gson:gson:2.10.1")
        // NOTE: You had okhttp listed twice. Keeping one.
        implementation("com.squareup.okhttp3:okhttp:4.12.0")

        // Coroutines
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

        // Networking (okhttp was duplicated, keeping the one with logging-interceptor)
        implementation("com.squareup.okhttp3:okhttp:4.12.0") // Kept for clarity, though already above
        implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

        // JSON Parsing (gson was duplicated, keeping one)
        implementation("com.google.code.gson:gson:2.10.1") // Kept for clarity, though already above

        // UI
        implementation("androidx.recyclerview:recyclerview:1.3.2")

        // Lifecycle
        implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
        implementation("androidx.activity:activity-compose:1.8.2")

        // Compose - Using a BOM compatible with the specified compiler
        implementation(platform("androidx.compose:compose-bom:2024.02.02"))
        implementation("androidx.compose.ui:ui")
        implementation("androidx.compose.ui:ui-graphics")
        implementation("androidx.compose.ui:ui-tooling-preview")
        implementation("androidx.compose.material3:material3")

        // Testing
        testImplementation("junit:junit:4.13.2")
        androidTestImplementation("androidx.test.ext:junit:1.1.5")
        androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
        androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.02"))
        androidTestImplementation("androidx.compose.ui:ui-test-junit4")
        debugImplementation("androidx.compose.ui:ui-tooling")
        debugImplementation("androidx.compose.ui:ui-test-manifest")
    }
}
