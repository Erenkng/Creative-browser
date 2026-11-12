pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.mozilla.org/maven2/") {
            content { includeGroup("org.mozilla.geckoview") }
        }
    }
}

rootProject.name = "Oavi"

include(
    ":app",
    ":core:model",
    ":core:device",
    ":core:data",
    ":core:engine",
    ":core:ui",
    ":feature:browser",
    ":feature:tabs",
    ":feature:privacy",
    ":feature:reader",
    ":feature:onboarding",
    ":feature:settings",
)
