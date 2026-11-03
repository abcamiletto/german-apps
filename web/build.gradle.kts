// The website: shared UI plus browser audio, speech, storage and content loading.
plugins {
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    wasmJs {
        browser {
            commonWebpackConfig { outputFileName = "wortschatz.js" }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain {
            // The site serves the same word list, images and audio that the APK bundles.
            resources.srcDir(rootDir.resolve("content"))
            dependencies {
                implementation(project(":shared"))
                implementation(libs.browser)
                implementation(libs.coil.network)
                implementation(libs.ktor.client.js)
            }
        }
    }
}
