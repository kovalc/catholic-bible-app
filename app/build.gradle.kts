plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.catholic_bible"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.catholic_bible"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // BuildConfig.API_BASE_URL (must end with a slash)
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"https://pvb7zkkj57.execute-api.us-west-2.amazonaws.com/\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Needed so BuildConfig is generated
    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // leave empty (optional)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Preferences (Settings screen)
    implementation("androidx.preference:preference-ktx:1.2.1")

    // WorkManager (notifications + wallpaper worker)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Retrofit + Gson
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // OkHttp logging
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Coil image loading
    implementation("io.coil-kt:coil:2.6.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}