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
    }
}

rootProject.name = "FitnessPlatformAndroid"
include(
    ":app",
    ":core:model",
    ":core:database",
    ":core:datastore",
    ":core:network",
    ":core:sync",
    ":core:testing",
    ":core:designsystem",
    ":domain",
    ":data",
    ":feature:main",
)
