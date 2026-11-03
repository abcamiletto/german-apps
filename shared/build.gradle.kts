// The whole product: domain logic and Compose UI. :app and :web only add what a platform must do differently.
plugins {
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.abcamiletto.wortschatz.shared"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
        androidResources { enable = true }
    }
    wasmJs {
        // Skiko can't load under Node, so shared tests run in headless Chrome.
        browser {
            testTask { useKarma { useChromeHeadless() } }
        }
        // Compose only lets a target run browser tests when it is bundled.
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.foundation)
            api(libs.compose.material3)
            api(libs.compose.runtime)
            api(libs.compose.ui)
            api(libs.coroutines.core)
            api(libs.lifecycle.runtime.compose)
            api(libs.lifecycle.viewmodel.compose)
            api(libs.navigation.compose)
            api(libs.serialization.json)
            implementation(libs.coil.compose)
            implementation(libs.compose.icons)
            implementation(libs.compose.resources)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
    }
}

compose.resources {
    packageOfResClass = "com.abcamiletto.wortschatz.resources"
}
