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

    sourceSets {
        // The word list, images and audio live in /content, which the website serves too.
        getByName("main").assets.directories.add(rootDir.resolve("content").path)
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
    implementation(project(":shared"))
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.room.ktx)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.test.core)
    androidTestImplementation(libs.test.rules)
    androidTestImplementation(libs.test.runner)
    debugImplementation(platform(libs.compose.bom))
    debugImplementation(libs.compose.ui.test.manifest)
}
