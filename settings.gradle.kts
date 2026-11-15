pluginManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
    }
    // After pushing Hatzen/gradle-reflections-plugin to GitHub, JitPack resolution works via:
    // resolutionStrategy {
    //     eachPlugin {
    //         if (requested.id.id == "io.github.manosbatsis.gradle.plugin.reflections") {
    //             useModule("com.github.Hatzen:gradle-reflections-plugin:<commit-or-tag>")
    //         }
    //     }
    // }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "reflections-android-demo"
include(":app")
