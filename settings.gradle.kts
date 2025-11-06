pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS) // <---- CHANGE THIS LINE

    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Final"
include(":app")
