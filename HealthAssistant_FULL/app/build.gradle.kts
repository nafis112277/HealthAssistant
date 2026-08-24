plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.srgroup.healthassistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.srgroup.healthassistant"
        minSdk = 26 // MediaPipe LLM Inference needs API 24+; 26 keeps things simpler for WorkManager + notifications
        targetSdk = 34
        versionCode = 3
        versionName = "0.1.0-step3-complete"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        // Gemma .task model files are large binary assets; don't compress in APK.
        resources.excludes.add("META-INF/*")
    }
}

// Room schema export: writes a JSON snapshot of the DB schema per version
// under app/schemas/. Needed now that exportSchema = true in AppDatabase -
// future Migrations get written and tested against these files.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Core - NotificationCompat, ActivityCompat (used by reminder notifications)
    implementation("androidx.core:core-ktx:1.13.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Extended icon set - Chat/MedicalServices/MonitorHeart/History used in
    // bottom nav aren't in the core icon set bundled with material3.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.1")

    // Room (local encrypted-ish storage for patient records / logs)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager for medication reminders
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Google MediaPipe Tasks GenAI - on-device Gemma inference
    // NOTE: verify latest version at https://developers.google.com/mediapipe/solutions/genai/llm_inference/android
    implementation("com.google.mediapipe:tasks-genai:0.10.14")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
