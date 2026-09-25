pluginManagement {
    includeBuild("../plugin")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "tests"
include("r2dbc-basic")
include("r2dbc-wal")
include("jdbc-basic")
include("jdbc-wal")
include("sharedTest")
includeBuild("../lib")
