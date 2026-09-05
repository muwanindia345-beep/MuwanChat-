plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

ksp {
    // Room ab har version ka schema JSON save karega app/schemas/ mein —
    // isse aage se real migrations likhna safe ho jaata hai (bina isके
    // migration SQL likhna guesswork jaisa hota hai aur crash ka risk rehta hai)
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.muwan.muwanchat"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.muwan.muwanchat"
        minSdk = 24
        targetSdk = 34
        versionCode = 162
        versionName = "2.161.0"
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("KEYSTORE_PATH")
            if (ksPath != null) {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
        // Fixed keystore just for the beta channel, committed directly in the repo
        // (app/beta.keystore) — so every beta build has the same signature and
        // installs as an UPDATE over the previous beta, no uninstall needed.
        // Not the official key, so no secrets/env vars needed for it.
        create("betaRelease") {
            storeFile = file("$projectDir/beta.keystore")
            storePassword = "QwvGbECV77rXsJpXVId1"
            keyAlias = "talkwave-beta"
            keyPassword = "QwvGbECV77rXsJpXVId1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false   // TEMP test
            isShrinkResources = false   // TEMP test
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
            firebaseCrashlytics {
                nativeSymbolUploadEnabled = true
            }
        }
    }

    flavorDimensions += "channel"
    productFlavors {
        // Official build — same applicationId as always, ships only confirmed features.
        create("production") {
            dimension = "channel"
            buildConfigField("boolean", "ENABLE_NEW_NAV", "false")
        }
        // Pre-release/beta build — separate applicationId so it installs alongside
        // the official app on the same phone instead of conflicting with it.
        create("beta") {
            dimension = "channel"
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            resValue("string", "app_name", "TalkWave Beta")
            buildConfigField("boolean", "ENABLE_NEW_NAV", "true")
            signingConfig = signingConfigs.getByName("betaRelease")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // Autofill wale code (LocalAutofill/AutofillNode/LocalAutofillTree) Compose ke
        // experimental API use karta hai — inke bina compile hi nahi hoga.
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-video:2.5.0")
    implementation("io.coil-kt:coil-gif:2.5.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("io.socket:socket.io-client:2.1.0") {
        exclude(group = "org.json", module = "json")
    }
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ndk")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-datasource:1.3.1")
    implementation("androidx.media3:media3-database:1.3.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // Calling feature (voice/video) -- Maven Central pe hosted, koi extra
    // repository config nahi chahiye. Package naam org.webrtc.* hi hai
    // (Google ka original WebRTC API), yeh sirf uska maintained prebuilt hai.
    implementation("io.github.webrtc-sdk:android:144.7559.09")
}
