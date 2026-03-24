rootProject.name = "miku"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        maven("https://jitpack.io")
    }
}

include(":libs:extension-api")
include(":libs:android-compat")
include(":libs:extension-runner")
include(":libs:domain")
include(":libs:database")
include(":apps:server")
