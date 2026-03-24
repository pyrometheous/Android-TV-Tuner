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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AndroidTVTuner"

include(":app")
include(":core-ui")
include(":core-data")
include(":tuner-core")
include(":tuner-usb-mygica")
include(":tuner-network")
include(":parser-atsc-psip")
include(":feature-live-tv")
include(":feature-guide")
include(":feature-recordings")
include(":feature-settings")
