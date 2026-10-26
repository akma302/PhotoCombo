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

rootProject.name = "PhotoCombo"
include(":composeApp")
include(":core")
include(":data")
include(":domain")
include(":design-system")
include(":ui-common")
include(":feature:builder")
include(":feature:library")
include(":feature:combo-list")
include(":feature:viewer")
include(":test-support")
