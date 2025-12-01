plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)   // applies com.google.gms.google-services
}

android {
    namespace = "com.example.b07project"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.b07project"
        minSdk = 24
        targetSdk = 36
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.firestore)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)

    // Material and UI
    implementation("com.google.android.material:material:1.12.0")
    implementation("nl.dionsegijn:konfetti-xml:2.0.4")
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:13.0.0")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Utilities
    implementation("androidx.work:work-runtime:2.9.0")
    implementation("com.google.guava:guava:31.1-android")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Unit tests
    testImplementation(libs.junit)
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")

    // Android instrumented tests
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
