pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    // Kotlin/Wasm adds its own repositories to download Node and Binaryen, so it can't be FAIL_ON_PROJECT_REPOS.
    repositoriesMode = RepositoriesMode.PREFER_PROJECT
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "wortschatz"
include(":app", ":shared", ":web")
