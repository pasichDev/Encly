pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "Encly"
include(":app")
include(":editor")
include(":core:database")
include(":core:common")
include(":core:crypto")
include(":core:security")
include(":core:keystore")
include(":core:resources")
include(":shared")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
    }
}
