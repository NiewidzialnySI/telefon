plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Strong Skipping Mode — kompilator Compose pomija recompozycję elementów,
// których parametry się nie zmieniły (nawet dla niestabilnych typów). Cała aplikacja płynniejsza
// przy listach i przełączaniu zakładek.
composeCompiler {
    enableStrongSkippingMode = true
}

android {
    namespace = "dialer.dominik.pl"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dialer.dominik.pl"
        minSdk = 31
        targetSdk = 37
        versionCode = 5
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Autoaktualizacja — patrz UPDATE.md / update.json w root repo.
        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL",
            "\"https://raw.githubusercontent.com/NiewidzialnySI/telefon/main/update.json\""
        )

        // whisper-android (free): tylko arm64-v8a — telefony; emulator x86 nie.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // MMS: Fossify fork android-smsmms (PDU + system sending) — jak Fossify Messages
    implementation("org.fossify:mmslib:1.0.1")
    // Backup: zgoda OAuth na Dysku Google (AuthorizationClient + drive.file)
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    // Motyw: seed color → paleta Material 3 (light/dark + animate)
    implementation("com.materialkolor:material-kolor:2.1.1")
    // Nagrywanie rozmów przez Shizuku (UserService = UID shell)
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    // Refleksja ActivityThread / AttributionSource w procesie shell
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
    // Offline transkrypcja (Whisper.cpp AAR; model PL ~488 MB przy pierwszym użyciu)
    implementation("dev.ffmpegkit-maintained:whisper-android:1.0.0")
    // Własne ADB (Wireless debugging) → shell UID bez Shizuku (jak CallVault)
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("com.github.MuntashirAkon:sun-security-android:1.1")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
}
