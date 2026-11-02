plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// CI provides the keystore through the environment so every release can update the previous install.
val releaseKeystore = providers.environmentVariable("WORTSCHATZ_KEYSTORE").orNull

android {
    namespace = "com.abcamiletto.wortschatz"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.abcamiletto.wortschatz"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toInt() ?: 1
        versionName = "1.0.${providers.environmentVariable("GITHUB_RUN_NUMBER").orNull ?: "0"}"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("WORTSCHATZ_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("WORTSCHATZ_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("WORTSCHATZ_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // MediaPlayer opens audio through a file descriptor, which requires uncompressed assets.
        noCompress += listOf("ogg", "webp")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.coil.compose)
    implementation(libs.compose.icons)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.room.ktx)
    implementation(libs.room.runtime)
    implementation(libs.serialization.json)
    ksp(libs.room.compiler)

    testImplementation(libs.coroutines.test)
    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.test.core)
    androidTestImplementation(libs.test.rules)
    androidTestImplementation(libs.test.runner)
    debugImplementation(libs.compose.ui.test.manifest)
}
