plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    // Firebase plugin
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"

}

android {
    namespace = "com.senapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.senapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        // URL del servidor local (cámbiala por tu IP LAN si compilas para dispositivo físico)
        debug {
            // Ejemplo: emulador
            buildConfigField(
                "String",
                "INTERPRET_BASE_URL",
                "\"http://192.168.18.46:8080\""
            )
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Puedes dejar la misma URL en release por ahora o poner una pública
            buildConfigField(
                "String",
                "INTERPRET_BASE_URL",
                "\"http://192.168.18.46:8080\""
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        // 🔴 ESTA ES LA CLAVE PARA QUITAR EL ERROR
        buildConfig = true
    }

    packagingOptions {
        resources {
            pickFirsts += listOf(
                "lib/arm64-v8a/libmediapipe_jni.so",
                "lib/armeabi-v7a/libmediapipe_jni.so",
                "lib/x86/libmediapipe_jni.so",
                "lib/x86_64/libmediapipe_jni.so",
                "lib/**/libprotobuf_jni.so"
            )
        }
    }
}


dependencies {
    // Jetpack Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation("io.ktor:ktor-client-okhttp:2.3.11")
    implementation("io.ktor:ktor-client-logging:2.3.11")
    implementation("io.ktor:ktor-client-core:2.3.11")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.11")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.11")


    // MediaPipe Tasks Vision (última estable soportada en Maven)
    implementation("com.google.mediapipe:tasks-vision:0.10.20")

    // Material Components
    implementation("com.google.android.material:material:1.12.0")

    // Firebase BOM
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))

    // Firebase (solo lo que necesites)
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")

    // CameraX
    val camerax_version = "1.4.0"
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")
    implementation("androidx.camera:camera-extensions:$camerax_version")

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

