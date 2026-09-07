pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // sherpa-onnx (on-device Piper TTS for Broadcast mode) is only published
        // via JitPack. Scoped to its group so nothing else resolves from here.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.k2-fsa") }
        }
    }
}

rootProject.name = "SleepRadio"
include(":app")
