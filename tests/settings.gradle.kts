pluginManagement {
    includeBuild("../plugin")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "tests"
include("r2dbc-basic")
includeBuild("../lib")
