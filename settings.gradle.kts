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
rootProject.name = "GdmK4811AppSuite"

// Módulo comum (dock, Ui, configurações em /sdcard/nwd-configs) e um módulo por app.
include(":common")
include(":launcher")
include(":car-setting")
include(":radio")
include(":music")
include(":bt-music")
include(":phone")
include(":eq")
include(":fan")
include(":clima")
include(":updater")
