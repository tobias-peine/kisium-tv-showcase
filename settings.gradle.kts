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

rootProject.name = "KisiumTV"

include(
    ":app-tv",
    ":core-model",
    ":core-data",
    ":core-ui",
    ":core-player",
    ":source-xtream",
    ":source-catalog-json",
    ":source-metadata",
    ":feature-settings",
)
